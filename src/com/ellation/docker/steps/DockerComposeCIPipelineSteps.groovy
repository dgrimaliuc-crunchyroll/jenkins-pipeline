#!/usr/bin/groovy
package com.ellation.docker.steps

import com.ellation.git.GitCommitAuthorEmailProvider
import com.ellation.slack.SlackIdLookup
import com.ellation.slack.SlackMessageColor
import com.ellation.slack.api.EllationSlackApi
import groovy.transform.Field

@Field String dcFileName
@Field String dcProjectName
@Field String etpSSHSecretText
@Field String dcServiceName
@Field String slackApiToken
@Field String fallbackSlackChannel
@Field String buildURL
@Field String classCoverageTargets = '50, 50, 50'
@Field String conditionalCoverageTargets = '50, 50, 50'
@Field String lineCoverageTargets = '50, 50, 50'
@Field String methodCoverageTargets = '50, 50, 50'
@Field String fileCoverageTargets = '50, 50, 50'
@Field String packageCoverageTargets = '50, 50, 50'

def build() {
    withCredentials([string(credentialsId: "${this.etpSSHSecretText}", variable: 'secretText')]) {
        sh "docker-compose --project-name ${this.dcProjectName} --file ${this.dcFileName} build --build-arg SSH_KEY=\"${secretText}\" --force-rm"
    }
}

def execTarget(String targetName) {
    sh "docker-compose --project-name ${this.dcProjectName} --file ${this.dcFileName} run ${this.dcServiceName} ${targetName}"
}

def codeCheck() {
    execTarget("code-check")
}

def unitTestWithCoverage() {
    execTarget("test-unit-with-coverage")
}

def publishCoverage() {
    cobertura(
            autoUpdateHealth: false,
            autoUpdateStability: false,
            classCoverageTargets: this.classCoverageTargets,
            coberturaReportFile: ".cover/coverage.xml",
            conditionalCoverageTargets: this.conditionalCoverageTargets,
            enableNewApi: true,
            failUnstable: true,
            failUnhealthy: true,
            failNoReports: true,
            fileCoverageTargets: this.fileCoverageTargets,
            lineCoverageTargets: this.lineCoverageTargets,
            maxNumberOfBuilds: 0,
            methodCoverageTargets: this.methodCoverageTargets,
            packageCoverageTargets: this.packageCoverageTargets,
            zoomCoverageChart: false
    )
}

def installDatabaseSchema() {
    execTarget("install-schema")
}

def seedDatabase() {
    execTarget("seed-database")
}

def integrationTests() {
    execTarget("test-all")
}

/**
 * Notifies individual (or team) who broke the build.
 */
def notify(String currentBuildResult) {
    if (currentBuildResult != 'SUCCESS') {
        notifyBuildFailedViaSlack()
    }
}

def notifyBuildFailedViaSlack() {
    def email = new GitCommitAuthorEmailProvider(this).email().trim()
    def slackApi = EllationSlackApi.getInstance(this, this.slackApiToken)
    def slackId = new SlackIdLookup(slackApi).fromEmail(email, this.fallbackSlackChannel)
    def message = "Build failed ${this.buildURL}"
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

def destroy() {
    sh "docker-compose --project-name ${this.dcProjectName} --file ${this.dcFileName} down --rmi \"local\" --volumes --timeout 10"
}

return this
