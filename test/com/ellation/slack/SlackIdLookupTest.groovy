package com.ellation.slack

import com.ellation.slack.api.SlackApi
import com.ellation.slack.api.model.SlackMember
import com.ellation.slack.api.model.SlackProfile
import com.ellation.slack.api.model.SlackUsers
import org.junit.Test

import static org.junit.Assert.assertEquals

class SlackIdLookupTest {

    def testSlackMembers = [
            new SlackMember(
                    id: "ABCDEFGHI",
                    profile: new SlackProfile(email: "veaceslav.gaidarji@ellation.com")
            ),
            new SlackMember(
                    id: "JKLMNOPQR",
                    profile: new SlackProfile(email: "first.last@ellation.com")
            )
    ]

    class SlackApiStub implements SlackApi {
        private final List<SlackMember> members

        SlackApiStub(List<SlackMember> members) {
            this.members = members
        }

        @Override
        SlackUsers getUsers() {
            return new SlackUsers(members: members)
        }
    }

    @Test
    void providesId() throws Exception {
        def lookup = new SlackIdLookup(new SlackApiStub(testSlackMembers))

        def slackId = lookup.fromEmail("veaceslav.gaidarji@ellation.com", "#vrv-android")

        assertEquals("@ABCDEFGHI", slackId)
    }

    @Test
    void providesFallbackChannelId() throws Exception {
        def lookup = new SlackIdLookup(new SlackApiStub(testSlackMembers))

        def slackId = lookup.fromEmail("no.such.email@ellation.com", "#vrv-android")

        assertEquals("#vrv-android", slackId)
    }
}
