package com.ellation.jenkins

import org.junit.Test

import static org.junit.Assert.assertEquals

class BuildInformationTest {

    @Test
    void lastSha() {
        String expectedCommit = '60c70d45c79eb36ea2624b13abd8374e299fafc8'
        BuildInformation buildInfo = getTestBuildInformation()

        String lastSha = buildInfo.getLastSha()

        assertEquals(expectedCommit, lastSha)
    }

    @Test
    void multipleRepositories() {
        BuildInformation buildInfo = getTestBuildInformation()

        def commits = buildInfo.getCommits()

        assertEquals(2, commits.size())
        assertEquals("evs-s3helper", commits['997fb08b9327d1de2117eac1c8ccbe47bcb6127c'].repoName)
        assertEquals("evs-nginx-vod", commits['60c70d45c79eb36ea2624b13abd8374e299fafc8'].repoName)
    }

    private BuildInformation getTestBuildInformation() {
        String buildOutput = this.getClass().getResource('/com/ellation/jenkins/buildJobOutput.json').text
        BuildInformation buildInfo = new BuildInformation(buildOutput)
        buildInfo
    }

}
