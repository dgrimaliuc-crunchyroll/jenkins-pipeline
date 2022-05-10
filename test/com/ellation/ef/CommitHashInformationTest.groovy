package com.ellation.ef

import com.ellation.ef.exception.InvalidCommitHashException
import org.junit.Test
import static org.junit.Assert.*

class CommitHashInformationTest {

    static final COMMIT_HASH_TEST       = '04636b64'
    static final COMMIT_HASH_SINGLE_KEY_TEST       = 'test-instance=04636b64'

    static final COMMIT_HASH_MULTI_TEST = 'test-instance=04636b64,computer-science=a51e9bf'
    static final INVALID_MULIT_COMMIT_HASH_TEST = 'test-instance=04636b64=asdasda,computer-science=a51e9bf'

    @Test
    void singleHash() {
        CommitHashInformation commitInfo = new CommitHashInformation(COMMIT_HASH_TEST)

        assertFalse(commitInfo.isMultiple())
    }

    @Test
    void multipleHash() {
        CommitHashInformation commitInfo = new CommitHashInformation(COMMIT_HASH_MULTI_TEST)

        assertTrue(commitInfo.isMultiple())
        assertFalse(commitInfo.isSingleEntryWithKey())
    }

    @Test
    void canGetValue() {
        CommitHashInformation commitInfo = new CommitHashInformation(COMMIT_HASH_TEST)

        assertEquals(COMMIT_HASH_TEST, commitInfo.commitHash)
    }

    @Test
    void canObtainRepositoryInformation() {
        CommitHashInformation commitInfo = new CommitHashInformation(COMMIT_HASH_MULTI_TEST)

        assertEquals(2, commitInfo.commits.size())

        assertEquals('04636b64', commitInfo.commits['test-instance'])
        assertEquals('a51e9bf', commitInfo.commits['computer-science'])
    }

    @Test(expected = InvalidCommitHashException)
    void invalidValues() {
        CommitHashInformation test = new CommitHashInformation(INVALID_MULIT_COMMIT_HASH_TEST)
        test.getCommits()
    }

    @Test
    void weCanDetectSingleKeys() {
        CommitHashInformation test = new CommitHashInformation(COMMIT_HASH_SINGLE_KEY_TEST)

        assertTrue(test.isSingleEntryWithKey())
        assertFalse(test.isMultiple())
    }

    @Test
    void keyInformationIsRemovedWithSingleKey() {
        CommitHashInformation test = new CommitHashInformation(COMMIT_HASH_SINGLE_KEY_TEST)

        assertEquals(COMMIT_HASH_TEST, test.commitHash)
    }
}
