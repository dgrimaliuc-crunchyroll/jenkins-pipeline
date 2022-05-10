package com.ellation

import com.ellation.git.GitRepoUrl
import com.ellation.jenkins.BuildInformation
import com.ellation.pipeline.JsonSchemaServicePipelineCommonSteps
import com.ellation.web.Config
import jenkins.plugins.http_request.ResponseContentSupplier
import groovy.json.JsonSlurper
import groovy.transform.Field
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

@Field String buildBranch = 'master'

@Field String commitHash

@Field String serviceName

@Field String subServicesName

@Field String environment = 'proto0'

@Field String credentials = null

@Field String testNamePattern = 'SERVICE-pipeline-BUILD_ID (DATE)'

@Field resultNameGenerator = null

@Field onTestFail = []

@Field onFailure = []

@Field serviceBuildJob = null

@Field listAmiId = [:]

@Field JsonSchemaServicePipelineCommonSteps servicePipelineCommonSteps

/**
 * Keeps all the build jobs, so that they can be accessed
 * by service name. Required by other scripts who want to
 * generate relevant links. Whole pipeline has to be refactored
 * as the methods who depend on serviceBuildJob have temporal
 * coupling. This was another reason why this field was added.
 */
@Field Map<String, RunWrapper> buildJobs = [:]

/**
 * Used to keep the ef_version wrapper
 */
@Field registry = null

@Field BuildInformation buildInformation

@Field String serviceRepositoryUrl

def forService(name) {
    serviceName = name
    return this
}

def forSubServices(name) {
    subServicesName = name
    return this
}

def fromBranch(name) {
    currentBuild.description = currentBuild.description + ' branch:' + name
    buildBranch = name
    return this
}

// TODO: change the name of this function. Two letter function names is not good.
def on(name) {
    environment = name
    return this
}

def withCredentials(credentialId) {
    credentials = credentialId
    return this
}

def whenTestFails(Closure cl) {
    cl.delegate = this
    onTestFail.add(cl)
}

def onFailure(Closure cl) {
    cl.delegate = this
    onFailure.add(cl)
}

def withConfigDeltaIntegration(Config config) {
    serviceRepositoryUrl = config.repository
    servicePipelineCommonSteps = new JsonSchemaServicePipelineCommonSteps(config, this)
}

def buildService() {
    stage 'build'
    this.serviceBuildJob = executeJob("${serviceName}-build")
    this.buildJobs[serviceName] = this.serviceBuildJob
    def amiId = getServiceAmiId()
    println("AMI ID: ${amiId}")
    listAmiId[serviceName] = amiId
    ellation_formation.set(serviceName, environment, amiId, [commit_hash: this.getBuildJobCommit(), build: env.BUILD_ID, noprecheck: true])
    if (subServicesName) {
        def subservicesArray = subServicesName.split(',')
        for (def i = 0; i < subservicesArray.size(); i++) {
            def subservice = subservicesArray[i]
            this.serviceBuildJob = executeJob("${subservice}-build")
            this.buildJobs[subservice] = this.serviceBuildJob
            amiId = getServiceAmiId()
            listAmiId[subservice] = amiId
            ellation_formation.set(subservice, environment, amiId, [noprecheck: true])
        }
    }
    return this.serviceBuildJob
}

Map<String, RunWrapper> buildServicesInParallel(Config config) {
    println("Using OS: ${config.baseImageOS}")
    Map<String, RunWrapper> serviceBuildResults = [:]
    List<String> serviceNames = getServiceList()
    Map<String, Closure> jobsToRun = [:]
    for (String serviceName in serviceNames) {
        // inside each loop for the parallel keyword, you will have to redefine values
        // in temp variables to avoid running the same value multiple times
        // https://jenkins.io/doc/pipeline/examples/#jobs-in-parallel
        String serviceNameLoop = serviceName
        jobsToRun[serviceNameLoop] = {
            RunWrapper buildJob = executeJob("${serviceNameLoop}-build")
            this.buildJobs[serviceNameLoop] = buildJob
            this.listAmiId[serviceNameLoop] = parseAMIIDFromBuildJob(buildJob)
        }
    }
    parallel(jobsToRun)

    return buildJobs
}

String parseAMIIDFromBuildJob(RunWrapper buildJob) {
    String jobConsoleTextUrl = "${buildJob.absoluteUrl}consoleText"
    String script =
            """curl --silent '${jobConsoleTextUrl}' | \
    grep 'amazon-ebs' | \
    grep 'AMI' | \
    grep -Po 'ami-[a-zA-Z0-9]+'| \
    grep -v 'tags' | \
    cut -d':' -f 3 | \
    tail -n1"""
    String amiID = sh(
            script: script,
            returnStdout: true
    ).trim()
    return amiID?.trim()
}

void updateServiceAMI(String serviceName, RunWrapper buildJob, flags = [:]) {
    String amiID = parseAMIIDFromBuildJob(buildJob)
    if (amiID?.trim()) {
        echo "${serviceName} : ${amiID}"
        commitHash = getBuildJobCommitV2(buildJob)

        flags["commit_hash"] = commitHash
        flags["build"] = buildJob.number
        flags["noprecheck"] = true
        ellation_formation.set(
                serviceName,
                environment,
                amiID,
                flags)
    } else {
        // Build job failed to produce an AMI ID,
        // tell the pipeline not to continue moving forward
        buildJob.setResult("FAILURE")
        error("${serviceName} did not produce an AMI ID.")
    }
}

RunWrapper buildServiceDistHash() {
    Map<String, RunWrapper> serviceBuildResults = [:]
    List<String> serviceNames = getServiceList()
    Map<String, Closure> jobsToRun = [:]
    for (String serviceName in serviceNames) {
        // inside each loop for the parallel keyword, you will have to redefine values
        // in temp variables to avoid running the same value multiple times
        // https://jenkins.io/doc/pipeline/examples/#jobs-in-parallel
        String serviceNameLoop = serviceName
        jobsToRun[serviceNameLoop] = {
            RunWrapper buildJob = executeJob("${serviceNameLoop}-build")
            this.buildJobs[serviceNameLoop] = buildJob
        }
    }
    parallel(jobsToRun)
    return buildJobs
}

def generateApiDocs(repository) {
    withEnv(['PATH=/usr/local/bin:/bin:/usr/bin:/usr/local/sbin:/usr/sbin:/usr/local/glide:/usr/local/go/bin:/opt/go/bin']) {
        gitWrapper.checkoutRepository branch: buildBranch, credentialsId: credentials, url: repository
        if (fileExists('Makefile')) {
            def makeRules = sh script: '''
      make -qp | awk -F':' '/^[a-zA-Z0-9][^$#\\/\\t=]*:([^=]|$)/ {split($1,A,/ /);for(i in A)print A[i]}'
      ''', returnStdout: true
            if (makeRules.split().contains('docgen')) {
                sh 'make docgen'
            } else {
                println('Docgen rule not found in Makefile. Skipping step.')
            }
        }

        if (fileExists('docs/swagger')) {
            def awsSyncCommand = "[ -d 'docs/swagger' ] && aws s3 sync --delete docs/swagger/ s3://ellation-cx-${ETP_ENVIRONMENT}-static/docs/swagger/${ETP_SERVICE}/ --cache-control max-age=300"
            if (env.ETP_DOCS_DEPLOY_AWS_CLI_PROFILE) {
                awsSyncCommand += " --profile ${env.ETP_DOCS_DEPLOY_AWS_CLI_PROFILE}"
            }

            sh awsSyncCommand
        } else {
            println('Swagger directory not detected.')
        }
    }
}

def uploadJsonSchemaAMI() {
    servicePipelineCommonSteps.uploadJsonSchemaAMI(getServiceRepositoryCommitSha())
}

def uploadJsonSchemaDistHash() {
    servicePipelineCommonSteps.uploadJsonSchemaDistHash(getServiceRepositoryCommitSha())
}

boolean schemaStoreIntegrationEnabled() {
    return servicePipelineCommonSteps.isSchemaStoreIntegrationEnabled()
}

def getServiceRepositoryCommitSha() {
    def commit = buildInformation.commits.find {
        it.value.repoName == new GitRepoUrl(serviceRepositoryUrl).repoName
    }
    return commit.key
}

RunWrapper executeJob(String job) {
    def params = []
    params.add(string(name: 'BUILDBRANCH', value: "${buildBranch}"))
    params.add(string(name: 'ENVIRONMENT', value: "${environment}"))
    if (["staging", "prod"].contains(environment)) {
        params.add(string(name: 'DEV', value: "False"))
    }
    try {
        return build(job: job, parameters: params)
    } catch (Exception e) {
        callClosures(onFailure)
        throw e
    }
}

private getServiceBuildUrl() {
    this.serviceBuildJob.getAbsoluteUrl() + '/api/json'
}

private getServiceLogUrl() {
    this.serviceBuildJob.getAbsoluteUrl() + '/consoleText'
}

def getServiceAmiId() {
    def amiId = null
    def serviceLogUrl = getServiceLogUrl()

    //execution forced on master because master is always present and named master
    node('master') {
        def script = "curl --silent '${serviceLogUrl}' | grep 'amazon-ebs' | grep 'AMI' | grep -Po 'ami-[a-zA-Z0-9]+'| grep -v 'tags' | cut -d':' -f 3 | tail -n1"
        amiId = sh(
                script: script,
                returnStdout: true
        ).trim()
    }

    return amiId
}

def getBuildJobCommit() {
    if (this.serviceBuildJob == null) {
        return ""
    }

    def response = httpRequest this.getServiceBuildUrl()
    def slurper = new JsonSlurper()
    def json = slurper.parseText(response.getContent())
    def sha = null

    for (def action in json.actions) {
        if (action._class == "hudson.plugins.git.util.BuildData") {
            sha = action.lastBuiltRevision.SHA1
        }
    }

    if (!sha) {
        throw new NoSuchElementException("Could not determine git commit")
    }

    return sha
}

String getBuildJobCommitV2(RunWrapper buildJob) {
    ResponseContentSupplier response = httpRequest("${buildJob.absoluteUrl}api/json")
    buildInformation = new BuildInformation(response.getContent())
    if (buildInformation.commits.size() == 1) {
        return buildInformation.lastSha
    }

    // Technically we can have a repository name collision if the repos belong to different organisations.
    // At this moment we are limited by ef-version key size, so we are not adding it in the signature
    // When pipelines will migrate to use SSM, we will record the owner as well
    return buildInformation.commits.collect { commitSha, gitRepo ->
        return "${gitRepo.repoName}=${commitSha}"
    }.join(",")
}

/**
 * Generate a service list composed of the service and all subservices,
 * prefixed with environment
 */
List<String> getServiceList() {
    List<String> serviceList = [serviceName.trim()]
    if (subServicesName) {
        String[] subServices = subServicesName.split(',')
        for (String service in subServices) {
            serviceList.add(service.trim())
        }
    }
    return serviceList
}

def deploy(waitTime = 0) {
    doDeploy(waitTime)
}

def publishServiceDeployToNewRelic(version) {
    println getServiceList()
    for (service in getServiceList()) {
        ellation_formation.publishDeployToNewRelic("${service}", version)
    }
}

def deployDistHash(waitTime = 0) {
    deploy(waitTime)
}

void updateDistHash(String serviceName, RunWrapper buildJob) {
    commitHash = getBuildJobCommitV2(buildJob)
    ellation_formation.setDistHash(serviceName, environment, commitHash)
}

def doDeploy(waitTime = 0) {
    executeJob("${serviceName}-deploy-${environment}")
    println "waiting for ${waitTime}"
    sleep waitTime
}

def callClosures(closureList) {
    for (def i = 0; i < closureList.size(); i++) {
        println "Executing Closure"
        def cl = closureList[i]
        cl.delegate = this
        cl.call()
    }
}

def test(service, RunWrapper buildJob) {
    switch (service) {
        case 'ent':
            try {
                runTest(service, buildJob)
            } catch (Exception e) {
                currentBuild.description = currentBuild.description + ' qa ent:fail'
                callClosures(onTestFail)
                callClosures(onFailure)
                throw e
            }
            try {
                runTest('ent.grant', buildJob)
            } catch (Exception e) {
                currentBuild.description = currentBuild.description + ' qa ent.grant-api:fail'
                callClosures(onTestFail)
                callClosures(onFailure)
                throw e
            }
            break
        default:
            try {
                runTest(service, buildJob)
            } catch (Exception e) {
                currentBuild.description = currentBuild.description + ' qa:fail'
                callClosures(onTestFail)
                callClosures(onFailure)
                throw e
            }
            break
    }
}

def runTest(String service, RunWrapper buildJob) {
    String testJob = "${service}-automated-test-${environment}"
    def params = [string(name: 'run', value: testResultNameFor(serviceName)), string(name: "COMMIT_HASH", value: getBuildJobCommitV2(buildJob))]
    if (Jenkins.instance.getItemByFullName(testJob) != null) {
        build(job: testJob, parameters: params)
    } else {
        error("The" + testJob + " job not found")
    }
}

protected testResultNameFor(service) {
    if (resultNameGenerator) {
        return this.resultNameGenerator.call()
    }

    def testResultName = testNamePattern.replaceAll('SERVICE', service).replaceAll('BUILD_ID', env.BUILD_ID).replaceAll('DATE', new Date().format('yyyy-MM-dd'))
    return testResultName
}

/**
 * This executes an unit test
 * @param repo string, the https repository address
 * @param type php|php71|go, the type of the test
 */
def unitTest(repo, type) {
    //in this intermediary phase
    //we just don't execute if we don't have credentials
    if (credentials == null) {
        return
    }

    gitWrapper.checkoutRepository(branch: buildBranch, credentialsId: credentials, url: repo)
    switch (type) {
        case 'php':
            sh '/usr/local/bin/composer.phar install --optimize-autoloader --no-interaction --no-scripts'
            sh 'vendor/phpunit/phpunit/phpunit'
            break
        case 'php71':
            sh 'COMPOSER_OPTIONS="--optimize-autoloader --no-interaction --no-scripts" make test-unit'
            break
        case 'go':
            sh 'PATH=/usr/local/bin:/bin:/usr/bin:/usr/local/sbin:/usr/sbin:/usr/local/glide:/usr/local/go/bin:/opt/go/bin make test'
            break
        default:
            break
    }
}

def info() {
    println "Service:$serviceName Environment:$environment"
}

def call(Closure cl) {
    cl.delegate = this
    try {
        cl.call()
    } catch (Exception e) {
        callClosures(onFailure)
        throw e
    }
}

/**
 * You can write pipeline code as
 * pipeline('service', 'proto0') { codeHere() }*/
def call(config, Closure cl) {
    forService(config.service)
    forSubServices(config.subservices)
    on(config.environment)
    withConfigDeltaIntegration(config)
    call(cl)
}

def call(config) {
    forService(config.service)
    forSubServices(config.subservices)
    on(config.environment)
    fromBranch(config.branch)
    withConfigDeltaIntegration(config)
}

def call(Map m = null) {
    if (m && m['environment']) {
        on(m.environment)
    }

    if (m && m['service']) {
        forService(m.service)
    }

    if (m && m['subServices']) {
        forSubServices(m.subServices)
    }

    if (m && m['branch']) {
        fromBranch(m.branch)
    }
}

def always(Closure cl) {
    cl.delegate = this
    cl()
}

def onMaster(Closure cl) {
    if (env.BRANCH_NAME) {
        return
    }

    cl.delegate = this
    cl()
}

def markStable() {
    keys = listAmiId.keySet() as String[]
    for (def i = 0; i < keys.size(); i++) {
        ellation_formation.set(keys[i], environment, listAmiId[keys[i]], [commit_hash: this.getBuildJobCommit(), build: env.BUILD_ID, stable: true])
    }
}

void markStableV2(String serviceName, RunWrapper buildJob, flags = [:]) {
    String amiID = parseAMIIDFromBuildJob(buildJob)
    if (amiID?.trim()) {
        flags["commit_hash"] = getBuildJobCommitV2(buildJob)
        flags["build"] = buildJob.number
        flags["stable"] = true
        ellation_formation.set(
                serviceName,
                environment,
                amiID,
                flags)
    } else {
        // Build job failed to produce an AMI ID,
        // tell the pipeline not to continue moving forward
        buildJob.setResult("FAILURE")
        error("${serviceName} did not produce an AMI ID.")
    }
}

/**
 * Required to simplify the code in deploy ticket
 * Will be removed once we refactor scripts/pipeline.groovy
 */
def pipelineUrl() {
    return currentBuild.absoluteUrl
}

return this
