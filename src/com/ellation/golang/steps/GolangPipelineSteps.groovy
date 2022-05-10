#!/usr/bin/groovy
package com.ellation.golang.steps

import com.ellation.git.GitCommitAuthorEmailProvider
import com.ellation.golang.config.GolangProjectConfig
import com.ellation.slack.SlackIdLookup
import com.ellation.slack.SlackMessageColor
import com.ellation.slack.api.EllationSlackApi
import groovy.transform.Field

@Field GolangProjectConfig projectConfig

/**
 * Pass Golang project config from outside using this method
 * at the beginning of your pipeline.
 */
void withConfig(GolangProjectConfig config) {
    projectConfig = config
}

def notify(String currentBuildResult) {
    if (currentBuildResult != 'SUCCESS') {
        notifyBuildFailedViaSlack()
    }
    cleanWs cleanWhenFailure: false
}

def runCodeStyleChecks() {
    sh 'go get golang.org/x/lint/golint'
    sh 'make code-check'
}

def unitTests() {
    sh "mkdir -p '.cover'"
    sh 'go get github.com/axw/gocov/gocov'
    sh 'go get github.com/AlekSi/gocov-xml'

    sh 'make vendor'
    sh "gocov test -covermode='count' -coverpkg=./... ./... --tags='unit' | gocov-xml > '.cover/coverage.xml'"
}

def publishCoverage() {
    cobertura(
            autoUpdateHealth: false,
            autoUpdateStability: false,
            coberturaReportFile: ".cover/coverage.xml",
            conditionalCoverageTargets: projectConfig.conditionalCoverageTargets,
            enableNewApi: true,
            failUnstable: false,
            failUnhealthy: true,
            failNoReports: true,
            lineCoverageTargets: projectConfig.lineCoverageTargets,
            maxNumberOfBuilds: 0,
            methodCoverageTargets: projectConfig.methodCoverageTargets,
            zoomCoverageChart: false
    )

    //TODO: figure out a way to publish test coverage change in PR
    // recordTestCoverage()
    // compareAndPublishTestCoverage()
}

/**
 * Notifies individual (or team) who broke the build.
 */
def notifyBuildFailedViaSlack() {
    def email = new GitCommitAuthorEmailProvider(this).email().trim()
    def slackApi = EllationSlackApi.getInstance(this, "${env.SLACK_API_TOKEN}")
    def slackId = new SlackIdLookup(slackApi).fromEmail(email, projectConfig.slackFallbackChannelId)
    def message = "Build failed ${env.BUILD_URL}"
    notifyViaSlack(message, slackId, SlackMessageColor.DANGER.toString())
}

def notifyViaSlack(String message, String channel, String color) {
    echo "Sending message to $channel via Slack."
    slackSend channel: channel,
            color: color,
            message: message,
            teamDomain: env.SLACK_TEAM_DOMAIN,
            token: env.SLACK_TOKEN
}

return this
