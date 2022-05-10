package com.ellation.jenkins

import com.cloudbees.groovy.cps.NonCPS
import com.ellation.git.GitRepoUrl
import groovy.json.JsonSlurper

/**
 * This class extracts build information from json output.
 */
class BuildInformation implements Serializable {
    private String content
    private Map<String, GitRepoUrl> commits = [:]
    private String lastSha = null

    /**
     * @param content the json api response of a jenkins build
     */
    BuildInformation(String content) {
        this.content = content
        loadGitRepositories()
    }

    /**
     * Returns last sha recorded in the build output.
     * This method has the same return value as previous implementation of   getBuildJobCommitV2 from
     * com.ellation.ServicePipeline
     * @return last sha used in the build
     */
    String getLastSha() {
        if (lastSha == null) {
            throw new NoSuchElementException("Could not determine git commit")
        }
        return lastSha
    }

    Map<String, GitRepoUrl> getCommits() {
        return commits
    }

    @NonCPS
    private void loadGitRepositories() {
        JsonSlurper slurper = new JsonSlurper()
        def json = slurper.parseText(content)
        for (def action in json.actions) {
            if (action._class == "hudson.plugins.git.util.BuildData") {
                String remoteUrls = action.remoteUrls[0]
                lastSha = action.lastBuiltRevision.SHA1
                commits[lastSha] = new GitRepoUrl(remoteUrls)
            }
        }
    }
}
