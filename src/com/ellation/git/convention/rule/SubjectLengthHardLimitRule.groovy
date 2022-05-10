package com.ellation.git.convention.rule

import com.ellation.git.GitInfo

/**
 * Git convention rule which verifies that git commit message subject
 * has no more than 72 characters.
 */
class SubjectLengthHardLimitRule extends GitConventionRule {
    private static final int SUBJECT_LENGTH_LIMIT = 72

    @Override
    boolean verify(GitInfo gitInfo) {
        def commitMessageLines = gitInfo.commitMessage.split(System.lineSeparator())
        if (commitMessageLines.size() < 1) {
            return false
        }
        return commitMessageLines[0].length() <= SUBJECT_LENGTH_LIMIT
    }

    @Override
    String description() {
        return "Commit message subject exceeded hard limit of $SUBJECT_LENGTH_LIMIT characters"
    }
}
