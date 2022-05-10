package com.ellation.git

import org.junit.Test
import static org.junit.Assert.assertEquals

class CommitInfoTest {

    @Test
    void testDefaults() {
        def commit = new CommitInfo()
        assertEquals("", commit.commitId)
        assertEquals("", commit.msg)
        assertEquals("N/A", commit.ticket)
    }

    @Test
    void allLabelsTest() {
        def commitMessage = "b6541c06b5e675f0136471b961b51cdf83ee29ee\n" +
                "Tc 106/close duplicate tickets (#104)\n" +
                "\n" +
                "* Adds a label with the name of the main component to the created ticket\n" +
                "* Once a service gets to Manual QA, close all stale tickets from the \"Manual QA\" column\n" +
                "\n" +
                "JIRA: TC-106\n" +
                "Reviewer: @momoneko , @bantiuc \n" +
                "PR: GH-104"

        def commit = CommitInfo.from(commitMessage)
        assertEquals("b6541c06b5e675f0136471b961b51cdf83ee29ee", commit.commitId)
        assertEquals("Tc 106/close duplicate tickets (#104)", commit.msg)
        assertEquals("TC-106", commit.ticket)
    }

    @Test
    void preDeployLabelTest() {
        def instructions = "do this at predeploy"

        ["Pre-Deploy", "Pre-dePloy", "predeploy"].each {
            def info = CommitInfo.from(getCommitMessage(instructions, it))

            assertEquals(instructions, info.preDeploy)
        }
    }

    @Test
    void postDeployLabelTest() {
        def instructions = "do this at post deploy"

        ["Post-Deploy", "Post-dePloy", "postdeploy"].each {
            def info = CommitInfo.from(getCommitMessage(instructions, it))

            assertEquals(instructions, info.postDeploy)
        }
    }

    @Test
    void jiraLabelTest() {
        def ticketNumber = "WHOPS-123"
        ['JIRA', 'jIra', 'jira '].each {
            def commit = CommitInfo.from(getCommitMessage(ticketNumber, it))

            assertEquals(ticketNumber, commit.ticket)
        }
    }

    @Test
    void librariesAreEmptyByDefault() {
        def commit = CommitInfo.from(this.getClass().getResource('/com/ellation/git/nonStandardCommit').text)

        assertEquals(0, commit.libraries.size())
    }

    @Test
    void librariesAreInitializedIfDetected() {
        def expectedTag = 'https://github.com/crunchyroll/test-instance/releases/tag/0.1.0'
        def commit = CommitInfo.from(this.getClass().getResource('/com/ellation/git/standardCommitWithLibrary').text)

        assertEquals(1, commit.libraries.size())
        assertEquals(expectedTag, commit.libraries[0])
    }

    @Test
    void multipleLibrariesAreAllowed() {
        def expectedTag = 'https://github.com/crunchyroll/test-instance/releases/tag/0.1.0'

        def commit = CommitInfo.from(this.getClass().getResource('/com/ellation/git/standardCommitWithMultipleLibraries').text)

        assertEquals(2, commit.libraries.size())
        assertEquals(expectedTag, commit.libraries[0])
    }

    @Test
    void multipleLibrariesOnSameLine() {
        def expectedTag = 'https://github.com/crunchyroll/test-instance/releases/tag/0.1.0'

        def commit = CommitInfo.from(this.getClass().getResource('/com/ellation/git/commitWithMultipleLibrariesSameLine').text)

        assertEquals(expectedTag, commit.libraries[0])
        assertEquals(2, commit.libraries.size())
    }

    private GString getCommitMessage(String fieldText = "", String label = "somelabel") {
        def commitBody = """
            12213123213
            Subject
            $label: $fieldText
            """
        return commitBody
    }

}
