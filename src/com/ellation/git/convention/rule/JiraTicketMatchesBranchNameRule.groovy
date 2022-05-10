package com.ellation.git.convention.rule

import com.ellation.git.GitInfo

/**
 * Git convention rule which verifies
 * that git commit message contains a ticket ID which matches branch name prefix.
 */
class JiraTicketMatchesBranchNameRule extends GitConventionRule {
    @Override
    boolean verify(GitInfo gitInfo) {
        def groups = gitInfo.branchName.split("-")
        String[] commitMessageLines = gitInfo.commitMessage.split(System.lineSeparator())
        if (groups == null || groups.size() < 2) {
            // branch name should have at least 2 groups like "CXAND-1234-"
            return false
        }
        def minimumLinesAllowed = 3
        if (commitMessageLines.size() < minimumLinesAllowed) {
            // commit message is not multiline and has no Jira ticket
            return false
        }

        def ticketId = "${groups[0]}-${groups[1]}"
        return commitMessageLines[2] == "Jira: $ticketId"
    }

    @Override
    String description() {
        return "Commit message must contain JIRA ticket ID which matches branch name prefix"
    }
}
