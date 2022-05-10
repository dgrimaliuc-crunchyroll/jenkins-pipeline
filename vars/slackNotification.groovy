#!/usr/bin/groovy
import com.ellation.git.GitCommitAuthorEmailProvider
import com.ellation.slack.SlackIdLookup
import com.ellation.slack.SlackMessageColor
import com.ellation.slack.api.EllationSlackApi

/**
 * Notifies individual (or team) who broke the build.
 */
def notifyBuildFailed(String channelId) {
    def message = "Build failed ${env.BUILD_URL}"
    notify(message, channelId)
}

def notifyBuildFailed(String channelId, String failedStep) {
    def message = "Build failed at step: *$failedStep*. ${env.BUILD_URL}console"
    notify(message, channelId)
}

def notifyChannelBuildFailed(String channelId) {
    def message = "Build failed ${env.BUILD_URL}"
    notifyViaSlack(message, channelId, SlackMessageColor.DANGER.toString())
}

def notifyViaSlack(String message, String channel, String color) {
    echo "Sending message to $channel via Slack."
    slackSend channel: channel,
            color: color,
            message: message,
            teamDomain: env.SLACK_TEAM_DOMAIN,
            token: env.SLACK_TOKEN
}

private def notify(String message, String channelId) {
    def email = new GitCommitAuthorEmailProvider(this).email().trim()
    def slackApi = EllationSlackApi.getInstance(this, "${env.SLACK_API_TOKEN}")
    def slackId = new SlackIdLookup(slackApi).fromEmail(email, channelId)
    notifyViaSlack(message, slackId, SlackMessageColor.DANGER.toString())
}

return this
