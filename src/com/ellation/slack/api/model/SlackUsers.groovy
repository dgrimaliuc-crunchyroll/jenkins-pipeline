package com.ellation.slack.api.model

import com.google.gson.annotations.SerializedName

/**
 * Base model for Slack users response.
 * Contains only fields which are used and doesn't map all fields from the Slack API response.
 */
class SlackUsers implements Serializable {
    @SerializedName("ok")
    Boolean ok
    @SerializedName("members")
    List<SlackMember> members
}
