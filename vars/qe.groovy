#!/usr/bin/groovy
import com.ellation.github.GithubClient
import com.ellation.github.RestClient
import com.ellation.qe.config.QeConfig
import com.ellation.qe.steps.QePipelineSteps

def call(environments = [:], @DelegatesTo(QePipelineSteps) Closure closure) {
    def closures = []
    def config = new QeConfig(env.getEnvironment() + environments, params)

    // Choose node for running pipeline if specified
    if (config.slave) {
        closures << { block ->
            node(config.slave) {
                block()
            }
        }
    }

    // Limit how many minutes the job can runs
    if (config.timeout) {
        closures << { block ->
            timeout(time: config.timeout, unit: 'MINUTES') {
                block()
            }
        }
    }

    // Show timestamp
    closures << { block ->
        timestamps {
            block()
        }
    }

    // Enable console output highlighting
    closures << { block ->
        ansiColor('xterm') {
            block()
        }
    }

    // Add AWS credentials
    if (config.awsCredentialId) {
        closures << { block ->
            withCredentials([[credentialsId: config.awsCredentialId, $class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                block()
            }
        }
    }

    // AWS AWS Role
    if (config.awsRole) {
        closures << { block ->
            def values = config.awsRole.split("\\|")
            awsRole(values[0], values[1]) {
                block()
            }
        }
    }

    // Add GitHub credentials
    if (config.githubCredentialId) {
        closures << { block ->
            withCredentials([usernamePassword(credentialsId: config.githubCredentialId, passwordVariable: 'GITHUB_TOKEN', usernameVariable: 'GITHUB_USERNAME')]) {
                def restClient = new RestClient(baseUrl: 'https://api.github.com', authToken:GITHUB_TOKEN)
                def github = new GithubClient(restClient)
                config.github = github

                block()
            }
        }
    }

    // Add JIRA credentials
    if (config.jiraCredentialId) {
        closures << { block ->
            withCredentials([usernamePassword(credentialsId: config.jiraCredentialId, passwordVariable: 'JIRA_PASSWORD', usernameVariable: 'JIRA_USERNAME')]) {
                withEnv(["JIRA_TOKEN=${env.JIRA_PASSWORD}"]) {
                    config.jiraUsername = env.JIRA_USERNAME
                    config.jiraPassword = env.JIRA_PASSWORD

                    withEnv(["JIRA_HOST=${config.jiraHost}"]) {
                        block()
                    }
                }
            }
        }
    }

    // Add TestRail credentials
    if (config.testRailCredentialId) {
        closures << { block ->
            withCredentials([usernamePassword(credentialsId: config.testRailCredentialId, passwordVariable: 'TESTRAIL_PASSWORD', usernameVariable: 'TESTRAIL_USERNAME')]) {
                config.testRailUsername = env.TESTRAIL_USERNAME
                config.testRailPassword = env.TESTRAIL_PASSWORD

                block()
            }
        }
    }

    // Add BrowserStack credentials (avoid marking build as passed)
    if (config.browserStackCredentialId) {
        closures << { block ->
            def environmentVariables = []

            browserstack(config.browserStackCredentialId) {
                environmentVariables << 'BROWSERSTACK_ACCESS_KEY=' + env.BROWSERSTACK_ACCESS_KEY
                environmentVariables << 'BROWSERSTACK_ACCESSKEY=' + env.BROWSERSTACK_ACCESSKEY
                environmentVariables << 'BROWSERSTACK_USER=' + (env.BROWSERSTACK_USER - "-jenkins")
                environmentVariables << 'BROWSERSTACK_USERNAME=' + (env.BROWSERSTACK_USERNAME - "-jenkins")
            }

            withEnv(environmentVariables) {
                block()
            }
        }
    }

    // Add LambdaTest credentials
    if (config.lambdaTestCredentialId) {
        closures << { block ->
            withCredentials([usernamePassword(credentialsId: config.lambdaTestCredentialId, usernameVariable: 'LT_USERNAME', passwordVariable: 'LT_ACCESS_KEY')]) {
                block()
            }
        }
    }

    // Run passed `closure` in context of `QePipelineSteps`
    closures << {
        // TODO: discuss with someone from OPS to setup MAVEN and JAVA SDK as a tools
        // def MAVEN_HOME = tool name: 'maven', type: 'maven'
        // ...
        withEnv(['MAVEN_OPTS=-Djansi.force=true', 'PATH+MVN=~/maven/bin:/opt/apache-maven-3.3.9/bin:/etc/maven/bin:/opt/apache-maven-3.5.4/bin:/usr/local/bin']) {
            def steps = new QePipelineSteps()
            if (config) {
                steps.withConfig(config)
            }

            closure.setDelegate(steps)
            closure.setResolveStrategy(Closure.DELEGATE_FIRST)
            closure.call()
        }
    }

    // Run computed chain
    def chainToCall = {}
    for (def i = closures.size() - 1; i >= 0; i--) {
        chainToCall = closures[i].curry(chainToCall)
    }

    chainToCall()
}

return this
