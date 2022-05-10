package com.ellation.slack.api

@Grapes([
        @Grab(group = 'com.squareup.okhttp3', module = 'okhttp', version = '3.9.1'),
        @Grab(group = 'com.google.code.gson', module = 'gson', version = '2.7')
])
import com.ellation.slack.api.model.SlackUsers
import com.google.gson.Gson
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Represents default Slack API client implementation.
 */
class DefaultSlackApi implements SlackApi, Serializable {
    final String baseUrl
    final String token
    def pipelineSteps

    protected DefaultSlackApi(pipelineSteps, String baseUrl, String token) {
        this.pipelineSteps = pipelineSteps
        this.baseUrl = baseUrl
        this.token = token
    }

    /**
     * Returns Slack users based on provided token.
     * Token is generated from Slack team and members from that team will be returned.
     */
    @Override
    SlackUsers getUsers() {
        String response = new OkHttpClient.Builder()
                .build()
                .newCall(createUsersRequest())
                .execute()
                .body()
                .string()
        def usersResponse = new Gson().fromJson(response, SlackUsers)
        if (!usersResponse.ok) {
            pipelineSteps.echo "Failed to get Slack users: \"$response\""
        }
        return usersResponse
    }

    Request createUsersRequest() {
        HttpUrl.Builder urlBuilder = HttpUrl.parse("${baseUrl}/api/users.list").newBuilder()
        urlBuilder.addQueryParameter("token", token)
        return new Request.Builder()
                .url(urlBuilder.build().toString())
                .build()
    }
}
