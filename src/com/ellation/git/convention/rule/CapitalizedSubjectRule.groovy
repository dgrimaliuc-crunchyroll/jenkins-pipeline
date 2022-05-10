package com.ellation.git.convention.rule

import com.ellation.git.GitInfo

/**
 * Git convention rule which verifies that git commit message subject starts with capital letter.
 */
class CapitalizedSubjectRule extends GitConventionRule {
    @Override
    boolean verify(GitInfo gitInfo) {
        return (gitInfo.commitMessage[0] as char).isUpperCase()
    }

    @Override
    String description() {
        return "Commit message subject must start with capital letter"
    }
}
