package com.ellation.git

/**
 * Contains various information about Git commit and branch.
 */
class GitInfo implements Serializable {
    String commitMessage = ""
    String branchName = ""
    String authorEmail = ""

    /**
     * Factory method which simplifies GitInfo object's fields population.
     */
    static GitInfo from(def pipelineSteps, String commitId, String branchName) {
        def helper = new GitHelper(pipelineSteps)
        return new GitInfo(
                commitMessage: helper.getCommitMessageBody(commitId),
                branchName: branchName,
                authorEmail: helper.getCommitAuthorEmail(commitId)
        )
    }
}
