package com.ellation.slack.api

import com.ellation.slack.api.model.SlackUsers

/**
 * Slack API contract.
 */
interface SlackApi {
    /**
     * Returns Slack users.
     */
    SlackUsers getUsers()
}
