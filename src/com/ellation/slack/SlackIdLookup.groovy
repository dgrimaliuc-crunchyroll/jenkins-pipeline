package com.ellation.slack

import com.ellation.slack.api.SlackApi

/**
 * Provides different methods of Slack ID retrieval.
 */
class SlackIdLookup implements Serializable {

    private final SlackApi slackApi

    SlackIdLookup(SlackApi slackApi) {
        this.slackApi = slackApi
    }

    /**
     * Returns Slack ID for provided user email.
     * @param email user email
     * @param fallbackId id used in case when nothing found for provided email
     * @return returned ID can be in "@person.slack.id" or "#channel.name" format
     */
    String fromEmail(String email, String fallbackId) {
        def users = slackApi.users
        for (def member : users?.members) {
            if (member?.profile?.email == email) {
                return "@${member?.id}"
            }
        }
        return fallbackId
    }
}
