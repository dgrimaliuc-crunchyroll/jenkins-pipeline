package com.ellation.slack.api

/**
 * Ellation Slack API client.
 */
class EllationSlackApi extends DefaultSlackApi {
    private static EllationSlackApi client

    private EllationSlackApi(pipelineSteps, String baseUrl, String token) {
        super(pipelineSteps, baseUrl, token)
    }

    /**
     * @param token Slack token which used to perform Slack API requests.
     * @return
     */
    static EllationSlackApi getInstance(pipelineSteps, String token) {
        if (client == null) {
            client = new EllationSlackApi(pipelineSteps, "https://ellation.slack.com/", token)
        }
        return client
    }
}
