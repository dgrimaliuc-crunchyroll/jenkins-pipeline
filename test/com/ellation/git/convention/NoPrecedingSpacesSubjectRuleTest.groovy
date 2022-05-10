package com.ellation.git.convention

import com.ellation.git.GitInfo
import com.ellation.git.convention.rule.NoPrecedingSpacesSubjectRule
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

class NoPrecedingSpacesSubjectRuleTest {
    NoPrecedingSpacesSubjectRule rule

    @Before
    void setUp() throws Exception {
        rule = new NoPrecedingSpacesSubjectRule()
    }

    @Test
    void commitMessageSubjectDoesNotHavePrecedingWhiteSpaces() throws Exception {
        def commitMessages = [
                "Subject",
                '''|Subject
                   |
                   |Jira: CXAND-1234
                   |Reviewer:
                   |PR: GH-xx'''.stripMargin()
        ]

        commitMessages.each {
            assertTrue("${rule.description()}\n${it}", rule.verify(new GitInfo(commitMessage: it)))
        }
    }

    @Test
    void commitMessageSubjectHasPrecedingWhitespaces() throws Exception {
        def commitMessages = [
                " Subject",
                '''|    Subject
                   |
                   |Jira: CXAND-1234
                   |Reviewer:
                   |PR: GH-xx'''.stripMargin()
        ]
        commitMessages.each {
            assertFalse("${rule.description()}\n${it}", rule.verify(new GitInfo(commitMessage: it)))
        }
    }
}
