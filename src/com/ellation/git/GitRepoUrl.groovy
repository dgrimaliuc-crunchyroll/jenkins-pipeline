package com.ellation.git

import com.ellation.jenkins.exception.InvalidRepositoryException

/**
 * This is a simple Data Object that breaks repository information
 */
class GitRepoUrl implements Serializable {

    /**
     * Property that holds the owner of the repository, like 'crunchyroll'
     */
    final String owner = null

    /**
     * Repository name, without git extension, like 'pipeline'
     */
    final String repoName = null

    /**
     * full https url, like https://github.com/crunchyroll/pipeline.git
     */
    final String httpsUrl = null

    /**
     * ssh location, like git@github.com:crunchyroll/pipeline.git
     */
    final String sshUrl  = null

    /**
     * Constructor
     * @param repository can be the https or ssh location of a repository
     * Examples:
     * github.com/crunchyroll/test-instance
     * https://github.com/crunchyroll/test-instance.git
     * git@github.com:crunchyroll/test-instance.git
     * @throws InvalidRepositoryException When the string does not match expected format
     */
    GitRepoUrl(String repository) {
        def match = (repository =~ /^(.*?)github\.com[:\/](.+?)\/(.+?)(\.git|\/)?$/)
        if (match.size() == 0 ) {
            throw new InvalidRepositoryException("Invalid repository format $repository")
        }

        owner    = match[0][2]
        repoName = match[0][3]
        httpsUrl = "https://github.com/${owner}/${repoName}.git"
        sshUrl   = "git@github.com:${owner}/${repoName}.git"
    }
}
