package com.ellation.git

import hudson.plugins.git.GitSCM
import org.junit.Test

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

class GitRepoUrlTransformerTest {

    static final HTTPS_REPO = "https://github.com/crunchyroll/cx-app-android.git"
    static final SSH_REPO = "git@github.com:crunchyroll/cx-app-android.git"
    static final NOT_A_REPO = "not-a-git-repo-url"

    public static final String MSG_REPO_UPDATED = "Updated repo URL from HTTPS to SSH"
    public static final String MSG_FAILED_TO_IDENTIFY_REPO = "Could not identify repo url"
    public static final String MSG_ALREADY_SSH = "Repo might be using SSH connection type already"

    class GitRepoUrlTransformerDouble extends GitRepoUrlTransformer {

        def consoleMessages = []

        GitRepoUrlTransformerDouble(Object pipelineSteps) {
            super(pipelineSteps)
        }

        @Override
        protected void updateToSsh(GitRepoUrlTransformer.GitRepo repo) {
            consoleMessages.add(MSG_REPO_UPDATED)
        }

        @Override
        protected void printToConsole(String message) {
            consoleMessages.add(message)
        }
    }

    def transformer = new GitRepoUrlTransformerDouble({})

    @Test
    void transformsToSsh() throws Exception {
        transformer.transformAndSet([HTTPS_REPO] as GitSCM)

        println transformer.consoleMessages

        assertTrue("Expected '$MSG_REPO_UPDATED' message in logs, but it was not found",
                isMessagePrintedToConsole(transformer, MSG_REPO_UPDATED))
    }

    @Test
    void canNotTransformWrongUrl() throws Exception {
        transformer.transformAndSet([NOT_A_REPO] as GitSCM)

        println transformer.consoleMessages

        assertTrue("Expected '$MSG_FAILED_TO_IDENTIFY_REPO' message in logs, but it was not found",
                isMessagePrintedToConsole(transformer, MSG_FAILED_TO_IDENTIFY_REPO))
        assertFalse("'$MSG_REPO_UPDATED' was found in logs, which means repo was updated when it should not.",
                isMessagePrintedToConsole(transformer, MSG_REPO_UPDATED))
    }

    @Test
    void doesNotTransformToSshIfAlreadySsh() throws Exception {
        transformer.transformAndSet([SSH_REPO] as GitSCM)

        println transformer.consoleMessages

        assertTrue("Expected '$MSG_FAILED_TO_IDENTIFY_REPO' message in logs, but it was not found",
                isMessagePrintedToConsole(transformer, MSG_FAILED_TO_IDENTIFY_REPO))
        assertTrue("Expected '$MSG_ALREADY_SSH' message in logs, but it was not found",
                isMessagePrintedToConsole(transformer, MSG_ALREADY_SSH))
    }

    private boolean isMessagePrintedToConsole(GitRepoUrlTransformerDouble transformer,
                                              String consoleMessage) {
        boolean containsMessage = false
        transformer.consoleMessages.each {
            if ((it as String).contains(consoleMessage)) {
                containsMessage = true
                return true
            }
        }
        return containsMessage
    }

}
