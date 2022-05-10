package com.ellation.git.convention

import com.ellation.git.GitInfo
import com.ellation.git.convention.rule.JiraTicketMatchesBranchNameRule
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

class JiraTicketMatchesBranchNameRuleTest {
    JiraTicketMatchesBranchNameRule rule

    @Before
    void setUp() throws Exception {
        rule = new JiraTicketMatchesBranchNameRule()
    }

    @Test
    void commitMessageContainsJiraTicket() throws Exception {
        def branchName = "CXAND-1234-test-branch"
        def commitMessage =
                '''|Subject
                   |
                   |Jira: CXAND-1234
                   |Reviewer:
                   |PR: GH-xx'''.stripMargin()

        assertTrue(rule.verify(new GitInfo(commitMessage: commitMessage, branchName: branchName)))
    }

    @Test
    void commitMessageContainsWrongJiraTicket() throws Exception {
        def branchName = "CXAND-1234-test-branch"
        def commitMessage =
                '''|Subject
                   |
                   |Jira: CXAND-1111
                   |Reviewer:
                   |PR: GH-xx'''.stripMargin()

        assertFalse(rule.verify(new GitInfo(commitMessage: commitMessage, branchName: branchName)))
    }

    @Test
    void branchNameInWrongFormat() throws Exception {
        def branchName = "featureX"
        def commitMessage =
                '''|Subject
                   |
                   |Jira: CXAND-1234
                   |Reviewer:
                   |PR: GH-xx'''.stripMargin()

        assertFalse(rule.verify(new GitInfo(commitMessage: commitMessage, branchName: branchName)))
    }

    @Test
    void commitMessageHasNoJiraTicket() throws Exception {
        def branchName = "CXAND-1234-test-branch"
        def commitMessage = "Subject"

        assertFalse(rule.verify(new GitInfo(commitMessage: commitMessage, branchName: branchName)))
    }
}
