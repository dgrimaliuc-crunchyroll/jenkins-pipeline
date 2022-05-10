#!/usr/bin/groovy

def call(Map params) {
    qe(params) {
        stage("Checkout") {
            deleteDir()
            checkout scm
        }

        stage("Build") {
            try {
                sh "mvn clean test-compile compile -U"
                // githubCommitStatus("Build", "", "SUCCESS")
            } catch (ignored) {
                // githubCommitStatus("Build", "Commit has compilation issues", "FAILURE")
                error("Commit has compilation issues")
            }
        }

        stage("Checkstyle") {
            try {
                sh "mvn detekt:check"
                // githubCommitStatus("Checkstyle", "", "SUCCESS")
            } catch (ignored) {
                // githubCommitStatus("Checkstyle", "Commit has checkstyle violations", "FAILURE")
                error("Commit has checkstyle violations")
            }
        }

        if (!params.SKIP_TESTS) {
            stage("Test") {
                def env = params.ENVIRONMENT
                def repoUrl = scm.userRemoteConfigs[0].url

                lock("$env: $repoUrl") {
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
            }

            stage('Publish Results') {
                publishResults()
            }
        }
    }
}

return this
