package com.ellation.git.convention.exception

/**
 * Custom exception which is thrown when multiple Git convention rules verifications fail.
 */
class MultipleGitConventionRulesFailedException extends Exception {
    MultipleGitConventionRulesFailedException(String message) {
        super(message)
    }
}
