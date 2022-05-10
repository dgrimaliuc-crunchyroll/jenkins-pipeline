package com.ellation.git.convention.exception

/**
 * Custom exception which is thrown when Git convention rule verification fails.
 */
class GitConventionRuleFailedException extends Exception {
    GitConventionRuleFailedException(String message) {
        super(message)
    }
}
