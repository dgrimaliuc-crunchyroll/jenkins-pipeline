package com.ellation.git

import com.ellation.email.EmailProvider

/**
 * Provides author email from git commit.
 */
class GitCommitAuthorEmailProvider implements EmailProvider {
    def steps

    GitCommitAuthorEmailProvider(pipelineSteps) {
        steps = pipelineSteps
    }

    /**
     * Takes into consideration merge commits and
     * returns pre-merge commit author email from the current branch.
     */
    String email() {
        def commit = findMergeCommitFirstParent() ?: "HEAD"
        return steps.sh(script: "git show -s --format='%ae' $commit", returnStdout: true).trim()
    }

    /**
     * Identifies if current commit is a merge commit (2+ parent commits).
     * @return first parent commit of a merge commit (last commit from source branch before merge)
     * or empty String if current commit is not a merge.
     */
    String findMergeCommitFirstParent() {
        String output = steps.sh(script: "git cat-file -p HEAD", returnStdout: true).trim()
        def matcher = (output =~ /parent [0-9a-f].*/)
        // merge commit has 2+ parent commits
        if (matcher.size() > 1) {
            return ((String) matcher[0]).replace("parent ", "")
        }
        return ""
    }
}
