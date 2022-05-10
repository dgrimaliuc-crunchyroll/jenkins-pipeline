package com.ellation.git.convention

import com.ellation.git.GitInfo
import com.ellation.git.convention.rule.CompanyEmailDomainRule
import org.junit.Test

import static junit.framework.TestCase.assertTrue
import static org.junit.Assert.assertFalse

class CompanyEmailDomainRuleTest {
    @Test
    void commitAuthorEmailMatchesEllationDomain() throws Exception {
        def rule = new CompanyEmailDomainRule()

        def gitAuthor = "veaceslav.gaidarji@ellation.com"
        assertTrue(rule.verify(new GitInfo(authorEmail: gitAuthor)))
    }

    @Test
    void commitAuthorEmailMatchesCrunchyrollDomain() throws Exception {
        def rule = new CompanyEmailDomainRule()

        def gitAuthor = "veaceslav.gaidarji@crunchyroll.com"
        assertTrue(rule.verify(new GitInfo(authorEmail: gitAuthor)))
    }

    @Test
    void commitAuthorEmailMatchesGithubDomain() throws Exception {
        def rule = new CompanyEmailDomainRule()

        def gitAuthor = "378493+veaceslav.gaidarji@users.noreply.github.com"
        assertTrue(rule.verify(new GitInfo(authorEmail: gitAuthor)))
    }

    @Test
    void commitAuthorEmailDoesNotMatchCompanyDomain() throws Exception {
        def rule = new CompanyEmailDomainRule()

        def gitAuthor = "test@test.com"
        assertFalse(rule.verify(new GitInfo(authorEmail: gitAuthor)))
    }
}
