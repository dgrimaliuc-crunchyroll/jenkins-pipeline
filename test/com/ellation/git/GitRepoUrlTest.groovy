package com.ellation.git

import com.ellation.jenkins.exception.InvalidRepositoryException
import org.junit.Test
import static org.junit.Assert.assertEquals

class GitRepoUrlTest {
    @Test
    void ownerExtraction() {
        GitRepoUrl repo = new GitRepoUrl('https://github.com/crunchyroll/evs-s3helper.git')

        assertEquals('crunchyroll', repo.owner)
        assertEquals('evs-s3helper', repo.repoName)
        assertEquals('https://github.com/crunchyroll/evs-s3helper.git', repo.httpsUrl)
    }

    @Test
    void gitExensionIsOptional() {
        GitRepoUrl repo = new GitRepoUrl('https://github.com/crunchyroll/evs-s3helper')

        assertEquals('crunchyroll', repo.owner)
        assertEquals('evs-s3helper', repo.repoName)
        assertEquals('https://github.com/crunchyroll/evs-s3helper.git', repo.httpsUrl)
    }

    @Test
    void trailingSlash() {
        GitRepoUrl repo = new GitRepoUrl('https://github.com/crunchyroll/evs-s3helper/')

        assertEquals('crunchyroll', repo.owner)
        assertEquals('evs-s3helper', repo.repoName)
        assertEquals('https://github.com/crunchyroll/evs-s3helper.git', repo.httpsUrl)
    }

    @Test(expected = InvalidRepositoryException)
    void nonGithubUrlWillNotWork() {
        new GitRepoUrl("www.google.com")
    }

    @Test
    void sshConversion() {
        String expected = 'git@github.com:crunchyroll/pipeline.git'

        GitRepoUrl repo = new GitRepoUrl('https://github.com/crunchyroll/pipeline.git')

        assertEquals(expected, repo.sshUrl)
    }

    @Test
    void httpsConversion() {
        String expected = 'https://github.com/crunchyroll/pipeline.git'

        GitRepoUrl repo = new GitRepoUrl('git@github.com:crunchyroll/pipeline.git')

        assertEquals(expected, repo.httpsUrl)
    }
}
