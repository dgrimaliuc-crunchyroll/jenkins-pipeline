package com.ellation.git

import org.junit.Test

import static junit.framework.TestCase.assertTrue
import static org.junit.Assert.*

/**
 * @group unit
 */
class GitHelperTest {

    class GitHelperDouble extends GitHelper {

        GitHelperDouble(Object pipelineSteps) {
            super(pipelineSteps)
        }

        @Override
        String getCommitAuthorEmail(Object commit) {
            return "veaceslav.gaidarji@ellation.com"
        }

        @Override
        String getCommitMessageBody(Object commit) {
            return "Commit body"
        }

        @Override
        String[] getCommitData(Object startCommit, Object endCommit, GitFormat format = GitFormat.BODIES) {
            return [
                    '''Subject #1
                        |
                        |Body #2'''.stripMargin(),
                    '''Subject #2
                        |
                        |Body #2'''.stripMargin()
            ]
        }

        @Override
        String[] getCommitsAuthorEmails(Object startCommit, Object endCommit) {
            return ["email1@test.com", "email2@test.com"]
        }
    }

    class GitHelperWithFormattedCommitBody extends  GitHelperDouble {

        GitHelperWithFormattedCommitBody(Object pipelineSteps) {
            super(pipelineSteps)
        }

        @Override
        String[] getCommitData(Object startCommit, Object endCommit, GitFormat format = GitFormat.BODIES) {
            return [
                    '''0229545
                   Subject #1
                   JIRA: JIRA-31832
                        |
                        |Body #2'''.stripMargin(),
                    '''Subject #2
                        |
                        |Body #2'''.stripMargin()
            ]
        }

    }

    GitHelperDouble gitHelper = new GitHelperDouble(new Object())

    @Test
    void testChangelogForSameCommit() {
        def helper = new GitHelperWithFormattedCommitBody(this)

        def changeInfo = helper.getChangelog('samecommit', 'samecommit')

        assertFalse(changeInfo['has_changes'])
        assertEquals(0, changeInfo['commits'].size())
    }

    @Test
    void getChangelogInformation() {
        def helper  = new GitHelperWithFormattedCommitBody(this)

        def changeInfo = helper.getChangelog('newcommit', 'oldcommit')

        assertEquals(2, changeInfo['commits'].size())
        assertTrue(changeInfo['has_changes'])
        assertEquals('newcommit', changeInfo['new_commit'])
        assertEquals('oldcommit', changeInfo['old_commit'])

        def changes = changeInfo['commits']

        assertEquals('JIRA-31832', changes.first()['ticket'])
        assertEquals('0229545', changes.first()['commitId'])
    }

    @Test
    void commitAuthorEmail() {
        assertEquals("veaceslav.gaidarji@ellation.com", gitHelper.getCommitAuthorEmail())
    }

    @Test
    void commitMessageBody() {
        assertEquals("Commit body", gitHelper.getCommitMessageBody())
    }
}
