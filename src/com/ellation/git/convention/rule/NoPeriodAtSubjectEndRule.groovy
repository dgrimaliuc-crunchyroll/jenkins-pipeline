package com.ellation.git.convention.rule

import com.ellation.git.GitInfo

/**
 * Git convention rule which verifies that git commit message subject has no period at the end.
 */
class NoPeriodAtSubjectEndRule extends GitConventionRule {
    @Override
    boolean verify(GitInfo gitInfo) {
        def commitMessageLines = gitInfo.commitMessage.split(System.lineSeparator())
        if (commitMessageLines.size() < 1) {
            return false
        }
        return commitMessageLines[0][-1] != "."
    }

    @Override
    String description() {
        return "Commit message subject should not end with a period"
    }
}
