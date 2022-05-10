package com.ellation.slack.api.model

import com.google.gson.annotations.SerializedName

class SlackProfile implements Serializable {
    @SerializedName("email")
    String email
}
