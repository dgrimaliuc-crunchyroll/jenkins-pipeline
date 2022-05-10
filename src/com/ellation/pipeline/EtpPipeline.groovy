package com.ellation.pipeline

import com.ellation.ef.ServiceHistory
import com.ellation.ef.ServiceRevision
import com.ellation.web.Config
import groovy.json.JsonSlurper
import jenkins.plugins.http_request.ResponseContentSupplier
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

/**
 * Ellation Technology Platform implementation of a service pipeline to be run in our Jenkins Build environment that
 * a Jenkinsfile pipeline can call on.
 */
class EtpPipeline implements ServicePipeline, Serializable {
    private enum VersionKeyType { AMI_ID, DIST_HASH }
    private Map<String, RunWrapper> serviceBuildJobs
    private String environment
    private VersionKeyType serviceType
    private String jenkinsGithubCredentialsId
    private String githubUrl
    private String buildBranch
    private String deployBranch
    private String unitTestLanguage
    private String mainServiceName
    private String subServicesNames
    private List<String> allServicesNames
    private int pipelineBuildNumber
    private int waitTimeAfterDeploy
    private boolean runBuild
    private boolean runDeploy
    private boolean runPostDeployTest
    private boolean runRollback
    private String testTarget
    private List<String> slackChannels
    private Closure onJobFailure = {}
    private Closure onTestFailure = {}
    private Script jenkinsScript
    private JsonSchemaServicePipelineCommonSteps servicePipelineCommonSteps

    /**
     * Constructor that takes in all the settings from Config and converts them into forms usable by this class.
     * Note: we want to restrict all interactions with the Config class to this constructor in order to reduce
     * impacts of future changes.
     * @param config - all the settings read from params and envs provided in jenkins
     */
    EtpPipeline(Script script, Config config) {
        jenkinsScript = script
        serviceBuildJobs = [:]
        environment = config.environment
        if (config.serviceType.toLowerCase() == "http_service") {
            serviceType = VersionKeyType.AMI_ID
        } else if (config.serviceType.toLowerCase() == "dist_static") {
            serviceType = VersionKeyType.DIST_HASH
        } else {
            jenkinsScript.error("${config.serviceType} not a recognized service type.")
        }
        jenkinsGithubCredentialsId = config.credentials
        githubUrl = config.repository
        buildBranch = (environment == "staging") ? config.mainBranchName : config.branch
        deployBranch = (environment == "staging") ? config.mainBranchName : config.deployBranch
        unitTestLanguage = config.technology
        mainServiceName = config.service
        allServicesNames = []
        allServicesNames.add(mainServiceName)
        subServicesNames = config.subservices
        // subservices is passed in as a comma separated string
        if (subServicesNames.length() > 0) {
            for (String service in config.subservices.split(',')) {
                allServicesNames.add(service.trim())
            }
        }
        pipelineBuildNumber = config.buildNumber
        waitTimeAfterDeploy = config.waitTime
        runBuild = config.runBuild
        runDeploy = config.runDeploy
        runPostDeployTest = config.runPostDeployTest
        runRollback = (environment == "staging") ? config.runRollback : false
        testTarget = config.testTarget
        slackChannels = []
        // notifications is passed in as a comma separated string
        if (config.notifications.length() > 0) {
            for (String channelName in config.notifications.split(',')) {
                slackChannels.add(channelName.trim())
            }
        }

        servicePipelineCommonSteps = new JsonSchemaServicePipelineCommonSteps(config, jenkinsScript)
    }

    private void logMessage(String message) {
        String prefix = "EtpPipeline Message - "
        jenkinsScript.echo(prefix + message)
    }

    /**
     * Returns a map of services and their build jobs. If the build function hasn't been run yet, this returns an
     * empty map.
     * @return Map<String, RunWrapper>    services and their build jobs
     */
    @Override
    Map<String, RunWrapper> getServiceBuildJobs() {
        return serviceBuildJobs
    }

    /**
     * Runs unit tests from this service's repo.
     */
    @Override
    void unitTests() {
        if (!jenkinsGithubCredentialsId || !githubUrl) {
            logMessage("The jenkins credentials or github repository url was never specified. " +
                    "Skipping unit tests.")
            return
        }
        // Make a unit test directory and perform the following commands inside
        jenkinsScript.dir("unitTests") {
            jenkinsScript.gitWrapper.checkoutRepository(branch: buildBranch, credentialsId: jenkinsGithubCredentialsId,
                    url: githubUrl)
            switch (unitTestLanguage) {
                case "php":
                    jenkinsScript.sh('/usr/local/bin/composer.phar install --optimize-autoloader --no-interaction ' +
                            '--no-scripts')
                    jenkinsScript.sh('vendor/phpunit/phpunit/phpunit')
                    break
                case "php71":
                    jenkinsScript.sh('COMPOSER_OPTIONS="--optimize-autoloader --no-interaction --no-scripts" ' +
                            'make test-unit')
                    break
                case "go":
                    jenkinsScript.sh('make test')
                    break
                default:
                    logMessage("No matching test language for ${unitTestLanguage}, skipping unit tests.")
                    break
            }
        }
    }

    /**
     * Runs this service's and subservices' build jobs
     */
    @Override
    void build(Config config) {
        if (!runBuild) {
            logMessage("Skipping build since flag is set to false")
            return
        }
        println("Using OS: ${config.baseImageOS}")
        Map<String, Closure> jobsToRun = [:]
        for (String serviceName in allServicesNames) {
            // inside each loop for the parallel keyword, you will have to redefine values
            // in temp variables to avoid running the same value multiple times
            // https://jenkins.io/doc/pipeline/examples/#jobs-in-parallel
            String serviceNameLoop = serviceName
            jobsToRun[serviceNameLoop] = {
                List parameters = []
                parameters.add(jenkinsScript.string(name: "BUILDBRANCH", value: "${buildBranch}"))
                parameters.add(jenkinsScript.string(name: "ENVIRONMENT", value: "${environment}"))
                RunWrapper buildJob = executeJob("${serviceNameLoop}-build", parameters)
                this.serviceBuildJobs[serviceNameLoop] = buildJob
            }
        }
        jenkinsScript.parallel(jobsToRun)
    }

    /**
     * Helper function that calls the actual build step to execute a jenkins job.
     * @param jobName - name of the job in jenkins
     * @param parameters - flags to send to the build step
     * @return the build job in jenkins that was run
     * @throws Exception if the jenkins job failed
     */
    private RunWrapper executeJob(String jobName, List parameters) {
        try {
            return jenkinsScript.build(job: jobName, parameters: parameters)
        } catch (Exception error) {
            onJobFailure()
            throw error
        }
    }

    /**
     * Update's a service's ami id or dist hash history
     * @param type - service uses ami id or dist hash for its history
     * @param stable - true if we want to mark the build as stable, false if not
     * @param noprecheck - true if we don't want to check that the ami id has been deployed,
     * false if we do want to check
     */
    @Override
    void updateServiceVersion(boolean stable = false, boolean noprecheck = true) {
        if (serviceType == VersionKeyType.AMI_ID) {
            serviceBuildJobs.each { serviceName, buildJob ->
                updateAmiIdVersion(serviceName, buildJob, stable, noprecheck)
            }
        } else if (serviceType == VersionKeyType.DIST_HASH) {
            updateDistHashVersion()
        } else {
            jenkinsScript.error("Specified a type ${serviceType.name()} that isn't supported yet.")
        }
    }

    /**
     * Helper function that updates a service's history that uses ami id by calling the ellation_formation step.
     * @param serviceName - name of the service where its history is being updated
     * @param buildJob - jenkins build job for the service
     * @param stable - true if we want to mark the build as stable, false if not
     * @param noprecheck - true if we don't want to check that the ami id has been deployed,
     * false if we do want to check
     */
    private void updateAmiIdVersion(String serviceName, RunWrapper buildJob, boolean stable, boolean noprecheck) {
        String amiId = parseAmiIdFromBuildJob(buildJob)
        String commitHash = getBuildJobCommitHash(serviceBuildJobs[serviceName])
        if (amiId) {
            Map<String, Object> flags = [:]
            flags["commit_hash"] = commitHash
            flags["build"] = pipelineBuildNumber
            flags["noprecheck"] = noprecheck
            flags["stable"] = stable
            jenkinsScript.ellation_formation.set(
                    serviceName,
                    environment,
                    amiId,
                    flags)
        } else {
            // Build job failed to produce an AMI ID, pipeline should not move forward
            buildJob.setResult("FAILURE")
            jenkinsScript.error("${serviceName} did not produce an AMI ID.")
        }
    }

    /**
     * Helper function that parses a build job's console text and returns the ami id that was produced.
     * Note: This must be the service's build job, otherwise there is no ami id to extract.
     * @param buildJob - service's build job
     * @return ami id
     */
    private String parseAmiIdFromBuildJob(RunWrapper buildJob) {
        String jobConsoleTextUrl = "${buildJob.absoluteUrl}consoleText"
        String script =
                """curl --silent '${jobConsoleTextUrl}' | \
                grep 'amazon-ebs' | \
                grep 'AMI' | \
                grep -Po 'ami-[a-zA-Z0-9]+'| \
                grep -v 'tags' | \
                cut -d':' -f 3 | \
                tail -n1"""
        String amiId = jenkinsScript.sh(script: script, returnStdout: true).trim()
        return amiId
    }

    /**
     * Helper function that updates a service's history that uses dist hash by calling the ellation_formation step.
     */
    private void updateDistHashVersion() {
        String commitHash = getBuildJobCommitHash(serviceBuildJobs[mainServiceName])
        jenkinsScript.ellation_formation.setDistHash(mainServiceName, environment, commitHash)
    }

    /**
     * Generates the swagger documentation for a service if the service's repo has it setup and stores the output
     * in an S3 bucket.
     */
    @Override
    void generateApiDocs() {
        if (!jenkinsGithubCredentialsId || !githubUrl) {
            logMessage("The jenkins credentials or github repository url was never specified. " +
                    "Skipping API doc generation.")
            return
        }
        jenkinsScript.dir("repoForAPIDoc") {
            jenkinsScript.gitWrapper.checkoutRepository(branch: buildBranch, credentialsId: jenkinsGithubCredentialsId,
                    url: githubUrl)
            if (jenkinsScript.fileExists("Makefile")) {
                String parseMakeRulesCommand = '''
                    make -qp | awk -F':' '/^[a-zA-Z0-9][^$#\\/\\t=]*:([^=]|$)/ {split($1,A,/ /);for(i in A)print A[i]}\'
                '''
                String makeRules = jenkinsScript.sh(script: parseMakeRulesCommand, returnStdout: true)
                if (makeRules.split().contains("docgen")) {
                    jenkinsScript.sh("make docgen")
                } else {
                    logMessage("Docgen rule not found in Makefile. Skipping API Doc generation")
                }
            }

            if (jenkinsScript.fileExists("docs/swagger")) {
                def awsSyncCommand = "[ -d 'docs/swagger' ] && aws s3 sync --delete docs/swagger/ " +
                        "s3://ellation-cx-${environment}-static/docs/swagger/${mainServiceName}/ " +
                        "--cache-control max-age=300"
                jenkinsScript.sh(awsSyncCommand)
            } else {
                logMessage('Swagger directory not detected.')
            }
        }
    }

    /**
     * Returns true if ETP_CFD_SCHEMA_BUILD_COMMAND or ETP_CFD_SCHEMA_PATH_DIST_HASH are defined.
     *
     * @return boolean
     */
    @Override
    boolean schemaStoreIntegrationEnabled() {
        return servicePipelineCommonSteps.isSchemaStoreIntegrationEnabled()
    }

    /**
     * Schema-store integration. Builds and uploads the service's JSON Schema to the schema store service.
     */
    @Override
    void uploadJsonSchema() {
        String commitHash = getBuildJobCommitHash(serviceBuildJobs[mainServiceName])

        switch (serviceType) {
            case VersionKeyType.AMI_ID:
                servicePipelineCommonSteps.uploadJsonSchemaAMI(commitHash)
                break
            case VersionKeyType.DIST_HASH:
                servicePipelineCommonSteps.uploadJsonSchemaDistHash(commitHash)
                break
        }
    }

    /**
     * Deploys the service to the AWS environment by calling the service's deploy job.
     */
    @Override
    void deploy() {
        if (!runDeploy) {
            logMessage("Skipping deploying since flag is set to false.")
            return
        }
        List parameters = []
        parameters.add(jenkinsScript.string(name: "DEPLOYBRANCH", value: "${deployBranch}"))
        parameters.add(jenkinsScript.string(name: "ENVIRONMENT", value: "${environment}"))
        executeJob("${mainServiceName}-deploy-${environment}", parameters)
        logMessage("Waiting for ${waitTimeAfterDeploy} seconds after deploy.")
        sleep(waitTimeAfterDeploy)
    }

    /**
     * Runs the service's QA tests after the deploy.
     * @throws Exception if the tests fail
     */
    @Override
    void postDeployTests() {
        if (!runPostDeployTest) {
            logMessage("Skipping post deploy tests since flag is set to false")
            return
        }
        if (testTarget && testTarget.toLowerCase() != "none") {
            try {
                ServiceHistory history = jenkinsScript.ellation_formation.history(mainServiceName, environment)
                ServiceRevision latest = history.latest()
                String gitCommitHash = latest.commit_hash
                runTest(testTarget, gitCommitHash)
            } catch (Exception error) {
                onTestFailure()
                rollback()
                throw error
            }
        } else {
            logMessage("Skipping post deploy tests, no target was set.")
        }
    }

    /**
     * Given a build job, extract the git commit hash printed in the output.
     * Note this must be a build job and not any other kind of job in Jenkins,
     * otherwise this function will fail because there is no git commit hash to be found in the output.
     * @param buildJob - Service's build job.
     * @return git commit hash of the service's code built
     * @throws NoSuchElementException if a git commit hash could not be found in the build job
     */
    private String getBuildJobCommitHash(RunWrapper buildJob) {
        // Is there a better way to grab this info instead of making a GET call as an anonymous user to Jenkins?
        ResponseContentSupplier response = jenkinsScript.httpRequest("${buildJob.absoluteUrl}api/json")
        JsonSlurper slurper = new JsonSlurper()
        def json = slurper.parseText(response.getContent())
        String gitCommitHash = null
        for (def action in json["actions"]) {
            if (action["_class"] == "hudson.plugins.git.util.BuildData") {
                gitCommitHash = action["lastBuiltRevision"]["SHA1"]
            }
        }
        if (!gitCommitHash) {
            throw new NoSuchElementException("Could not determine git commit hash")
        }
        return gitCommitHash
    }

    /**
     * Rollback the service and its subservices by running the etp-service-rollback job
     */
    private void rollback() {
        boolean newBuildDeployed = (runBuild && runDeploy)
        if (runRollback && newBuildDeployed) {
            subServicesNames
            List parameters = [jenkinsScript.string(name: 'ENVIRONMENT', value: environment),
                               jenkinsScript.string(name: 'SERVICE', value: mainServiceName),
                               jenkinsScript.string(name: 'SUBSERVICES', value: subServicesNames)]
            executeJob("etp-service-rollback", parameters)
        } else {
            logMessage("Not performing rollback, either flag set to false or no deploy was done.")
        }
    }

    /**
     * Another helper function to determine what tests to run for a service. Bad naming conventions is the cause.
     * @param serviceName - name of the service
     * @param gitCommitHash - git commit hash of the service's code.
     */
    def runTest(String serviceName, String gitCommitHash) {
        String testJob = "${serviceName}-automated-test-${environment}"
        List params = [jenkinsScript.string(name: 'run', value: testResultNameFor(serviceName)),
                       jenkinsScript.string(name: "COMMIT_HASH", value: gitCommitHash)]
        if (Jenkins.instance.getItemByFullName(testJob) != null) {
            executeJob(testJob, params)
        } else {
            error("The" + testJob + " job not found")
        }
    }

    /**
     * Generates a String describing the test run for this service. Used in testrail.net which is why.
     * @param serviceName - name of the service
     * @return service's test run name Ex: subs-pipeline-1245 (2019-03-22)
     */
    private String testResultNameFor(String serviceName) {
        String currentDate = new Date().format('yyyy-MM-dd')
        return "${serviceName}-pipeline-${pipelineBuildNumber} (${currentDate})"
    }

    /**
     * Publishes information about the service to new relic and notifies the slack channels that this service is done
     * in the pipeline.
     */
    @Override
    void publishNotifications() {
        if (runBuild) {
            ServiceHistory history
            switch (serviceType) {
                case VersionKeyType.AMI_ID:
                    history = jenkinsScript.ellation_formation.history(mainServiceName, environment)
                    break
                case VersionKeyType.DIST_HASH:
                    history = jenkinsScript.ellation_formation.distHashHistory(mainServiceName, environment)
                    break
                default:
                    return
            }
            ServiceRevision latest = history.latest()
            String gitCommitHash = latest.commit_hash
            for (String serviceName in allServicesNames) {
                jenkinsScript.newRelic.publishServiceDeployToNewRelicV2(environment, serviceName, gitCommitHash)
            }
        }
        for (String channelName in slackChannels) {
            jenkinsScript.slackSend(channel: channelName, message: "${mainServiceName} has finished on ${environment}")
        }
    }

    /**
     * Displays what was built in this pipeline's run in the Jenkins' UI.
     */
    @Override
    void displayArtifactInformation() {
        logMessage("Deploy complete for the following services")
        serviceBuildJobs.each { serviceName, buildJob ->
            switch (serviceType) {
                case VersionKeyType.AMI_ID:
                    String amiId = getServiceAmiId(buildJob)
                    logMessage("Service: ${serviceName} AMI ID: ${amiId}")
                    break
                case VersionKeyType.DIST_HASH:
                    String commitHash = getBuildJobCommitHash(buildJob)
                    logMessage("Service: ${serviceName} Commit Hash: ${commitHash}")
                    break
            }
        }
    }

    /**
     * Helper function that parses the ami id from the build job's console text.
     * @param buildJob - build job of a service
     * @return ami id of the service
     */
    private String getServiceAmiId(RunWrapper buildJob) {
        // Had to rely on curl instead of making an httpRequest because when trying to echo the response body
        // into a pipe for grep using sh(), Jenkins bash kept thinking the next string line was a command
        String script = "curl --silent ${buildJob.absoluteUrl}/consoleText | grep 'amazon-ebs' | grep 'AMI' | " +
                "grep -Po 'ami-[a-zA-Z0-9]+'| grep -v 'tags' | cut -d':' -f 3 | tail -n1"
        String amiId = jenkinsScript.sh(script: script, returnStdout: true).trim()
        return amiId
    }

    /**
     * Setter function on how the pipeline should behave if a job fails.
     * @param behavior - what the pipeline should do
     */
    @Override
    void setJobFailureBehavior(Closure behavior) {
        onJobFailure = behavior
    }

    /**
     * Setter function on how the pipeline should behave if a test fails.
     * @param behavior - what the pipeline should do
     */
    @Override
    void setTestFailureBehavior(Closure behavior) {
        onTestFailure = behavior
    }
}
