package com.ellation.git.convention

import com.ellation.git.GitInfo
import com.ellation.git.convention.rule.NewLineAfterSubjectRule
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

class NewLineAfterSubjectRuleTest {
    NewLineAfterSubjectRule rule

    @Before
    void setUp() throws Exception {
        rule = new NewLineAfterSubjectRule()
    }

    @Test
    void commitMessageSubjectSeparatedByNewLineFromBody() throws Exception {
        assertTrue(rule.verify(
                new GitInfo(commitMessage: "Subject"))
        )
        assertTrue(rule.verify(
                new GitInfo(commitMessage: '''|Subject
                                              |
                                              |Body
                                           '''.stripMargin()))
        )
    }

    @Test
    void commitMessageSubjectIsNotSeparatedByNewLineFromBody() throws Exception {
        assertFalse(rule.verify(
                new GitInfo(commitMessage: '''|Subject
                                              |Body
                                           '''.stripMargin())))
    }
}
