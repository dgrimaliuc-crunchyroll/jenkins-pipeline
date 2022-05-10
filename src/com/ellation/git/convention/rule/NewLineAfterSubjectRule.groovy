package com.ellation.git.convention.rule

import com.ellation.git.GitInfo

/**
 * Git convention rule which verifies
 * that git commit message subject is separated from body by new line.
 */
class NewLineAfterSubjectRule extends GitConventionRule {
    @Override
    boolean verify(GitInfo gitInfo) {
        def commitLines = gitInfo.commitMessage.split(System.lineSeparator())
        int count = commitLines.length

        if (count == 1) {
            // nothing to analyze here
            return true
        }

        return commitLines[0].trim() != "" && commitLines[1].trim() == ""
    }

    @Override
    String description() {
        return "Commit message subject must be separated with new line from commit body"
    }
}
