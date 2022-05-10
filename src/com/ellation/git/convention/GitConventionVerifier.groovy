package com.ellation.git.convention

import com.ellation.git.GitHelper
import com.ellation.git.GitInfo
import com.ellation.git.convention.exception.GitConventionRuleFailedException
import com.ellation.git.convention.exception.MultipleGitConventionRulesFailedException

/**
 * Provides capabilities to ensure that Git convention is respected for a range of commits and
 * provides combined verification result.
 */
final class GitConventionVerifier implements Serializable {
    private final def steps
    private final GitConvention convention

    GitConventionVerifier(pipelineSteps, GitConvention convention) {
        this.steps = pipelineSteps
        this.convention = convention
    }

    /**
     * Verifies if all rules in convention are fulfilled for range of commits from a given branch.
     * @param branchName Which branch commits belong to
     * @param startCommit Start commit from a branch
     * @param endCommit End commit from a branch.
     * @return
     */
    def verifyCommitsRange(String branchName, String startCommit, String endCommit) {
        def exceptions = []
        def helper = new GitHelper(steps)
        def emails = helper.getCommitsAuthorEmails(startCommit, endCommit)
        def messages = helper.getCommitData(startCommit, endCommit)
        messages.eachWithIndex { String message, int i ->
            try {
                convention.verify(
                        new GitInfo(
                                commitMessage: message,
                                branchName: branchName,
                                authorEmail: emails[i]
                        )
                )
            } catch (GitConventionRuleFailedException exception) {
                exceptions.add(exception.message)
            }
        }
        if (exceptions.size() > 0) {
            throw new MultipleGitConventionRulesFailedException(buildRulesFailedMessage(exceptions))
        }
    }

    /**
     * Prepares error message in following format:
     * <pre>
     * <code>
     *
     * Commit message:
     * '1st commit body here'
     *
     * Git convention rules verification failed:
     * - Commit message subject should not end with a period.
     * - Commit message must contain JIRA ticket ID which matches branch name prefix.
     * - etc.
     *
     * ---------------
     *
     * Commit message:
     * '2nd commit body here'
     *
     * Git convention rules verification failed:
     * - Commit message subject should not end with a period.
     * - Commit message must contain JIRA ticket ID which matches branch name prefix.
     * - etc.
     *
     * ---------------
     * </code>
     * </pre>
     * @param errors array of multiple git convention rules verification errors
     */
    private static String buildRulesFailedMessage(List<String> errors) {
        def delimiter = """
                        |
                        |---------------
                        """.trim().stripMargin()
        return errors
                .collect { System.lineSeparator() + it + System.lineSeparator() }
                .join("$delimiter") + delimiter
    }
}
