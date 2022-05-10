package com.ellation.git.convention

import com.ellation.git.GitInfo
import com.ellation.git.convention.rule.NoPeriodAtSubjectEndRule
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

class NoPeriodAtSubjectEndRuleTest {
    NoPeriodAtSubjectEndRule rule

    @Before
    void setUp() throws Exception {
        rule = new NoPeriodAtSubjectEndRule()
    }

    @Test
    void commitMessageSubjectEndsWithPeriod() throws Exception {
        def wrongCommitMessages = [
                "Subject.",
                '''|Subject.
                   |
                   |Jira: CXAND-1234
                   |Reviewer:
                   |PR: GH-xx'''.stripMargin()
        ]

        for (int i = 0; i < wrongCommitMessages.size(); i++) {
            assertFalse(
                    "${rule.description()}\n${wrongCommitMessages[i]}",
                    rule.verify(new GitInfo(commitMessage: wrongCommitMessages[i]))
            )
        }
    }

    @Test
    void commitMessageSubjectDoesNotEndWithPeriod() throws Exception {
        assertTrue(rule.verify(new GitInfo(commitMessage: "Subject")))
    }
}
