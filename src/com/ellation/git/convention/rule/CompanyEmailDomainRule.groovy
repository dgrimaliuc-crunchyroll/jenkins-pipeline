package com.ellation.git.convention.rule

import com.ellation.git.GitInfo

/**
 * Git convention rule which verifies that git commit message subject starts with capital letter.
 */
class CompanyEmailDomainRule extends GitConventionRule {
    public static final List<String> ALLOWED_EMAIL_DOMAINS = [
            "@ellation.com", "@crunchyroll.com", "@users.noreply.github.com"
    ]

    @Override
    boolean verify(GitInfo gitInfo) {
        if (gitInfo.authorEmail.trim().isEmpty()) {
            return false
        }
        return ALLOWED_EMAIL_DOMAINS.any { gitInfo.authorEmail.endsWith(it) }
    }

    @Override
    String description() {
        return "Commit's author email must use one of the allowed domains" +
                " ${ALLOWED_EMAIL_DOMAINS.toString()}"
    }
}
