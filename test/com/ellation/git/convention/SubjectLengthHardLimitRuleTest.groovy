package com.ellation.git.convention

import com.ellation.git.GitInfo
import com.ellation.git.convention.rule.SubjectLengthHardLimitRule
import org.junit.Test

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

class SubjectLengthHardLimitRuleTest {
    @Test
    void commitMessageSubjectDoesNotExceedHardLimit() throws Exception {
        def rule = new SubjectLengthHardLimitRule()

        def subject72 = "${"a" * 72}"
        def wrongCommitMessages = [
                subject72,
                """|$subject72
                   |
                   |Jira: CXAND-1234""".stripMargin()
        ]

        for (int i = 0; i < wrongCommitMessages.size(); i++) {
            assertTrue(
                    "${rule.description()}\n${wrongCommitMessages[i]}",
                    rule.verify(new GitInfo(commitMessage: wrongCommitMessages[i]))
            )
        }
    }

    @Test
    void commitMessageSubjectExceedsHardLimit() throws Exception {
        def rule = new SubjectLengthHardLimitRule()

        def commit73 = """${"a" * 73}
                        |
                        |Jira: CXAND-1234
                        """.trim().stripMargin()

        assertFalse(rule.verify(new GitInfo(commitMessage: commit73)))
    }
}
