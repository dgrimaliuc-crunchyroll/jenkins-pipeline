package com.ellation.qe.steps

import com.cloudbees.groovy.cps.NonCPS
import com.ellation.github.exception.InvalidGithubResponse
import com.ellation.qe.config.QeConfig
import groovy.transform.Field
import hudson.FilePath
import hudson.tasks.test.AbstractTestResultAction

import static hudson.model.Result.SUCCESS

@Field QeConfig config = new QeConfig(env, params)


def withConfig(QeConfig config) {
    this.config = config
}

def runTests() {
    def workspace

    if (env['NODE_NAME'].equals("master")) {
        workspace = new FilePath(null, env['WORKSPACE'])
    } else {
        workspace = new FilePath(Jenkins.getInstance().getComputer(env['NODE_NAME']).getChannel(), env['WORKSPACE'])
    }

    if (fileExists(".testrail")) {
        workspace.child(".testrail").delete()
    }

    if (config.module && fileExists(config.module + "/.testrail")) {
        workspace.child(config.module).child(".testrail").delete()
    }

    if (config.github && config.targetServiceSlug && config.targetServiceBase && config.targetServiceHead) {
        def unsupportedCharsRegExp = /[^a-zA-Z0-9_-]+/
        def tokens = config.targetServiceSlug.tokenize('/')
        def owner = tokens[0]
        def repo = tokens[1]
        def serviceTags = config.github.getCommitsDiff(owner, repo, config.targetServiceBase, config.targetServiceHead).commits
                .findResults { it =~ /GH-(\d+)/ }
                .findResults { it ? it[0][1] : null }
                .unique()
                .findResults {
                    try {
                        return config.github.getIssue(owner, repo, it).labels
                    } catch (InvalidGithubResponse error) {
                        if (error.message.contains('404')) {
                            return null
                        }

                        throw error
                    }
                }
                .flatten()
                .collect { it.name.replaceAll(unsupportedCharsRegExp, '') }
                .unique()
                .join(' | ')

        if (serviceTags) {
            if (config.tags) {
                config.tags = "${config.tags} | $serviceTags"
            } else {
                config.tags = serviceTags
            }
        }
    }

    mvn "clean verify ${config.getArgs()} ${config.additionalArgs} -Dmaven.test.failure.ignore=true -U"

    def app = inferApp()
    if (app) {
        config.app = app[0]
        config.app_version = app[1]
        config.app_build = app[2]

        // appendToBuildDescription("${config.app_type ? config.app_type : config.app} v${config.app_version} (${config.app_build})")
    }
}

def publishResults() {
    if (config.country)
        appendToBuildDescription("<b>Geo:</b> ${config.country}")

    if (config.archivePerformanceResults)
        archiveJUnitPerformanceResults()

    archiveJUnitResults()

    if (!config.disableTestRailPublish)
        publishResultsToTestRail()

    publishHtmlReport("Allure Report", "target/site/allure-maven-plugin")
}

def publishHtmlReport(title, folder) {
    if (fileExists(folder + "/index.html")) {
        publishHTML([
                allowMissing         : true,
                alwaysLinkToLastBuild: true,
                keepAll              : true,
                reportDir            : folder,
                reportFiles          : 'index.html',
                reportName           : title,
                reportTitles         : title
        ])
    }
}

def attachTestRailRun() {
    if (fileExists("${config.module ?: "."}/.testrail")) {
        def workspace

        if (env['NODE_NAME'].equals("master")) {
            workspace = new FilePath(null, env['WORKSPACE'])
        } else {
            workspace = new FilePath(Jenkins.getInstance().getComputer(env['NODE_NAME']).getChannel(), env['WORKSPACE'])
        }

        def properties = new Properties()
        properties.load(new ByteArrayInputStream(workspace.child("${config.module ?: "."}/.testrail").readToString().getBytes()))

        if (properties.run_url) {
            def matches = properties.run_url =~ /\/runs\/view\/(\d+)/
            if (matches && !currentBuild.description?.contains("TestRail URL")) {
                appendToBuildDescription("<b>TestRail URL:</b> <a href=\"${properties.run_url}\">${matches[0][1]}</a><br>")
                return true
            }
        }
    }

    return false
}

def publishResultsToTestRail() {
    if (fileExists("${config.module ?: "."}/.testrail")) {
        def properties = new Properties()
        properties.load(new ByteArrayInputStream(readFile("${config.module ?: "."}/.testrail").getBytes()))
        if (properties.getProperty("should_fail", "true").toBoolean() && !config.testRailNoFail) {
            error("A test marked as critical was failed, please check TestRail run for details: ${properties.run_url}")
        }
    } else {

        try {
            mvn "testrail:publish ${config.getArgs()} ${config.additionalArgs} -U"
        } catch (e) {
            try {
                // TODO: Remove catch when all projects will use `testrail:publish` from Ellation nexus repository
                sleep 1
                if (currentBuild.rawBuild.getLog(100).join('\n').contains("No plugin found for prefix 'testrail'"))
                    mvn "testrail-maven-plugin:publish ${config.getArgs()} ${config.additionalArgs} -U"
                else
                    throw e
            } catch (error) {
                sleep 1
                if (!currentBuild.rawBuild.getLog(100).join('\n').contains("No plugin found for prefix 'testrail-maven-plugin'"))
                    throw error
            }
        } finally {
            // wait a bit in order to let Jenkins record console output from the previous command
            sleep 1

            // Append TestRail Run URL to the build description
            def stdout = currentBuild.rawBuild.getLog(100).join('\n')
            def matches = stdout =~ /(https:\/\/ellation\.testrail\.net\/index.php\?\/runs\/view\/(\d+))/
            if (matches)
                appendToBuildDescription("<b>TestRail URL:</b> <a href=\"${matches[0][1]}\">${matches[0][2]}</a>")
        }
    }
}

def archiveJUnitPerformanceResults() {
    def status = currentBuild.rawBuild.@result

    try {
        perfReport sourceDataFiles: "target/**/TEST-*.xml", ignoreFailedBuilds: true, ignoreUnstableBuilds: true
    } catch (Throwable ignore) {
    }

    currentBuild.rawBuild.@result = status
}

def archiveJUnitResults() {
    // Fix BrowserStack jenkins plugin
    if (fileExists("target/browserstack-reports")) {
        sh "find target/browserstack-reports -size 0 -delete"
    }

    // Normalize XML Results (needed by browserstack plugin)
    sortJUnitXMLTestCases('**/surefire-reports/**/*.xml')
    sortJUnitXMLTestCases('**/failsafe-reports/**/*.xml')

    // Attach JUnit results & prevent marking the build as UNSTABLE
    junit testResults: '**/surefire-reports/**/*.xml,**/failsafe-reports/**/*.xml',
            healthScaleFactor: 0.0,
            allowEmptyResults: true,
            keepLongStdio: true,
            testDataPublishers: [[$class: 'AutomateTestDataPublisher']]

    // If TestRail integration is disabled then the status will be determined by JUnit Jenkins plugin
    if (!config.disableTestRailPublish)
        currentBuild.rawBuild.@result = SUCCESS

    // Extract JUnit results from JUnit Plugin and add them to build description
    AbstractTestResultAction testResultAction = currentBuild.rawBuild.getAction(AbstractTestResultAction.class)
    if (testResultAction != null) {
        def totalNumberOfTests = testResultAction.totalCount
        def failedNumberOfTests = testResultAction.failCount
        def failedDiff = testResultAction.failureDiffString
        def skippedNumberOfTests = testResultAction.skipCount
        def passedNumberOfTests = totalNumberOfTests - failedNumberOfTests - skippedNumberOfTests

        def report = ''
        report += "<b>Passed:</b> ${passedNumberOfTests}<br>\n"
        report += "<b>Failed:</b> ${failedNumberOfTests} ${failedDiff}<br>\n"
        report += "<b>Skipped:</b> ${skippedNumberOfTests} out of ${totalNumberOfTests}<br>\n"

        appendToBuildDescription(report)
    }
}

def healthCheck(String host) {
    sh """
        set +x +e
        echo "Check health for $host"
        status=0
        endtime=\$(( \$(date +%s) + 300 ))
        while [[ \$(date +%s) -le \$endtime ]];
        do
            status=\$(curl --max-time 10 --write-out %{http_code} --output /dev/null --silent "${host}/_health")

            if [[ "\$status" == "200" ]]; then
                break
            fi
    
            echo "actual: \$status, expected: 200"
            sleep 10
        done

        if [[ "\$status" == "200" ]]; then
            echo "Check health passed for $host"
        else
            curl --verbose --max-time 10 "$host/_health"
            echo "Check health was failed for $host"
            exit 1
        fi
    """
}

private def mvn(String args) {
    if (!env.NODE_NAME.contains("docker") && !(env.FORCE_DOCKER == "true")) {
        sh "mvn $args"
    } else {
        def dockerComposeYml = null
        def rootDockerComposeYml = "docker-compose.yml"
        def moduleDockerComposeYml = "${config.module}/docker-compose.yml"

        if (fileExists(moduleDockerComposeYml)) {
            dockerComposeYml = moduleDockerComposeYml
        } else if (fileExists(rootDockerComposeYml)) {
            dockerComposeYml = rootDockerComposeYml
        }

        if (dockerComposeYml) {
            try {
                sh """
                    export COMPOSE_INTERACTIVE_NO_CLI=1
                    export DOCKER_USER="\$(id -u):\$(id -g)"
                    docker-compose -f "${dockerComposeYml}" build
                    docker-compose -f "${dockerComposeYml}" run --rm maven mvn $args
                """
            } finally {
                sh """
                    export COMPOSE_INTERACTIVE_NO_CLI=1
                    export DOCKER_USER="\$(id -u):\$(id -g)"
                    docker-compose -f "${dockerComposeYml}" down
                """
            }
        } else {
            if (env.QA_JENKINS) {
                sh """
                    MAVEN_CONTAINER=maven:3.6.2-jdk-8
                    MAVEN_REPOSITORY_VOLUME=maven-repo
        
                    docker volume create --name "\${MAVEN_REPOSITORY_VOLUME}" >/dev/null 2>&1
                    docker run --user "\$(id -u):\$(id -g)" -v maven-repo:/root/.m2 -v "\$(pwd):/app" -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_REGION -e AWS_CREDENTIAL_ID -e AWS_ROLE_ARN -e AWS_ROLE_SESSION_NAME -e JOB_NAME -e BUILD_NUMBER -e LT_USERNAME -e LT_ACCESS_KEY -e BROWSERSTACK_ACCESS_KEY -e BROWSERSTACK_ACCESSKEY -e BROWSERSTACK_USER -e BROWSERSTACK_USERNAME -e JIRA_USERNAME -e JIRA_PASSWORD -e JIRA_TOKEN -e JIRA_HOST -e TESTRAIL_ENABLED -e  TESTRAIL_HOST -e  TESTRAIL_USERNAME -e  TESTRAIL_PASSWORD -e  TESTRAIL_RUN -e  TESTRAIL_ENTRY -e  TESTRAIL_PLAN -e  TESTRAIL_SUITE -e  TESTRAIL_PROJECT -e  TESTRAIL_DESCRIPTION -e  TESTRAIL_CUSTOM_ID_FIELD -e TESTRAIL_CASES -w="/app" --network host --rm "\${MAVEN_CONTAINER}" mvn $args
                """
            } else {
                sh """
                    MAVEN_CONTAINER=maven:3.6.2-jdk-8
                    MAVEN_REPOSITORY_VOLUME=maven-repo
        
                    docker volume create --name "\${MAVEN_REPOSITORY_VOLUME}" >/dev/null 2>&1
                    docker run --user "\$(id -u):\$(id -g)" -v maven-repo:/root/.m2 -v "\$(pwd):/app" \$(env | cut -f1 -d= | sed 's/^/-e /') -w="/app" --network host --rm "\${MAVEN_CONTAINER}" mvn $args
                """
            }
        }
    }
}

private def sortJUnitXMLTestCases(glob) {
    try {
        def files = new FileNameFinder().getFileNames("target", glob)
        for (filePath in files) {
            try {
                def file = new File(filePath)
                def xml = new XmlParser().parseText(file.text.trim())
                xml.value = xml.children().sort { it.@name }
                file.text = XmlUtil.serialize(xml)
            } catch (Throwable ignore) {
            }
        }
    } catch (Throwable ignore) {
    }
}

@NonCPS
private def inferApp() {
    def log = currentBuild.rawBuild.getLog(Integer.MAX_VALUE).join('\n')
    def matches = (log =~ /Application: (.+)\nVersion: (.+?) \((.+)\)/)
    if (matches)
        return [matches[0][1], matches[0][2], matches[0][3]]

    return null
}

private def appendToBuildDescription(message) {
    if (!currentBuild.description)
        currentBuild.description = ''

    currentBuild.description += message + '<br>\n'
}
