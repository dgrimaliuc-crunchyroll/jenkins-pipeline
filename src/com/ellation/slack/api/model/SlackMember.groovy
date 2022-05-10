package com.ellation.slack.api.model

import com.google.gson.annotations.SerializedName

class SlackMember implements Serializable {
    @SerializedName("id")
    String id
    @SerializedName("profile")
    SlackProfile profile
}
