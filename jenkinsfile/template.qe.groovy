library 'ellation@qe'

try {
    qe {
        if (config.healthCheckServices)
            stage('Health Check') {
                parallel config.healthCheckServices.collectEntries { host ->
                    return [(host): { healthCheck(host) }]
                }
            }

        stage('SCM') {
            def statusCode = sh script: "git lfs > /dev/null 2>&1", returnStatus: true
            if (statusCode != 0) {
                git url: config.repository, branch: config.branch, credentialsId: config.gitCredentialId
            } else {
                def extensions = [[$class: 'GitLFSPull']]

                if (config.clearUntrackedFiles) {
                    extensions.add([$class: 'CleanBeforeCheckout'])
                }

                checkout([
                        $class                           : 'GitSCM',
                        branches                         : [[name: config.branch]],
                        doGenerateSubmoduleConfigurations: false,
                        extensions                       : extensions,
                        submoduleCfg                     : [],
                        userRemoteConfigs                : [
                                [credentialsId: config.gitCredentialId, url: config.repository]
                        ]
                ])
            }

            unstashFileParams()
        }

        stage('Run Tests') {
            def finished = false
            parallel 'Run automation': {
                try {
                    runTests()
                } finally {
                    finished = true
                }
            }, 'Wait for testrail-run': {
                while (true) {
                    sleep 1

                    def isFinished = finished
                    if (attachTestRailRun() || isFinished) {
                        break
                    }
                }
            }
        }

        stage('Publish Results') {
            publishResults()
        }
    }
} catch (e) {
    // If there was an exception thrown, the build failed
    currentBuild.result = "FAILED"
    throw e
} finally {
    // Success or failure, always send notifications
    if (env.SLACK_CHANNELS) {
        def color = "good"
        def status = "passed"

        if (currentBuild.result == "UNSTABLE") {
            color = "danger"
            status = "warning"
        }

        if (currentBuild.result == "FAILURE") {
            color = "danger"
            status = "failed"
        }

        SLACK_CHANNELS.split(",").each { channel ->
            def slackChannel = channel.trim()

            if (slackChannel) {
                slackSend(channel: slackChannel, color: color, message: "*Job:* <${currentBuild.absoluteUrl}#${currentBuild.number}|${currentBuild.projectName}>\n*Status:* ${status}\n*Description:* \n```\n${(currentBuild.description ?: "No description").replaceAll("<([^>]+)>", "").trim()}\n```")
            }
        }
    }
}
