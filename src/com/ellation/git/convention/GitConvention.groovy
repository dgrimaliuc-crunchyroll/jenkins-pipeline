package com.ellation.git.convention

import com.ellation.git.GitInfo
import com.ellation.git.convention.exception.GitConventionRuleFailedException
import com.ellation.git.convention.rule.GitConventionRule

/**
 * Provides capabilities to ensure that Git convention is respected.
 * Different teams might have different agreements and so different conventions.
 */
abstract class GitConvention implements Serializable {
    /**
     * List of rules to verify.
     */
    abstract List<GitConventionRule> rules()

    /**
     * Verifies if all rules in convention are fulfilled.
     */
    def verify(GitInfo gitInfo) throws GitConventionRuleFailedException {
        def errors = []
        rules().each { rule ->
            if (!rule.verify(gitInfo)) {
                errors.add(rule.description())
            }
        }
        if (!errors.isEmpty()) {
            throw new GitConventionRuleFailedException(buildRulesFailedMessage(gitInfo, errors))
        }
    }

    /**
     * Prepares error message in following format:
     * <pre>
     * <code>Commit message:
     * 'commit body here'
     *
     * Git convention rules verification failed:
     * - Commit message subject should not end with a period.
     * - Commit message must contain JIRA ticket ID which matches branch name prefix.
     * - etc.
     * </code>
     * </pre>
     * @param gitInfo information about git commit
     * @param errors array of git convention rules verification errors
     */
    private static String buildRulesFailedMessage(GitInfo gitInfo, List<String> errors) {
        return """
            |
            |Commit message:
            |'${stripTrailingSpaces(gitInfo.commitMessage)}'
            |
            |Git convention rules verification failed:
            |${errors.collect { "- $it" }.join(System.lineSeparator())}
            """.trim().stripMargin()
    }

    /**
     * Strips all trailing spaces from the source string.
     */
    private static String stripTrailingSpaces(String source) {
        return source.replaceFirst("\\s++\$", "")
    }
}
