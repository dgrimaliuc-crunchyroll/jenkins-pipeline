package com.ellation.git.convention

import com.ellation.git.GitInfo
import com.ellation.git.convention.rule.CapitalizedSubjectRule
import org.junit.Test

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

class CapitalizedSubjectRuleTest {
    @Test
    void commitMessageStartsWithCapitalLetter() throws Exception {
        def rule = new CapitalizedSubjectRule()

        assertTrue(rule.verify(new GitInfo(commitMessage: "Subject")))
    }

    @Test
    void commitMessageDoesNotStartWithCapitalLetter() throws Exception {
        def rule = new CapitalizedSubjectRule()

        assertFalse(rule.verify(new GitInfo(commitMessage: "subject")))
    }
}
