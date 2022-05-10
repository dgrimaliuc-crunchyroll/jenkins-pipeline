package com.ellation.git

/**
 * Can transform HTTPS repo URL to SSH URL and update git remote.
 * Currently, supports only `HTTPS -> SSH` transformation.
 */
class GitRepoUrlTransformer implements Serializable {
    private static final String TAG = GitRepoUrlTransformer
    def steps

    GitRepoUrlTransformer(pipelineSteps) {
        steps = pipelineSteps
    }

    /**
     * Transforms HTTPS repo URL to SSH URL and updates git remote.
     * @param scm Git SCM global variable available in Pipeline/Multibranch Pipeline type of jobs.
     *
     * @see <a href="https://github.com/jenkinsci/git-plugin/blob/10bd76cd29a81e95e34dad0da62041e21a13f2fd/src/main/java/hudson/plugins/git/GitSCM.java">GitSCM.java</a>
     */
    def transformAndSet(scm) {
        GitRepo repo = getGitRepo(scm)
        if (repo != GitRepo.UNKNOWN) {
            updateToSsh(repo)
        }
    }

    /**
     * Identifies information about Git repository like URL, user name, repo name.
     * @param scm Git SCM global variable available in Pipeline/Multibranch Pipeline type of jobs.
     * @return GitRepo
     */
    protected GitRepo getGitRepo(scm) {
        String repoUrl = repoUrlFromScm(scm)
        if (!isRepoHttpsUrl(repoUrl)) {
            printToConsole("[$TAG] Could not identify repo url.\n[$TAG] Repo might be using SSH connection type already.")
            return GitRepo.UNKNOWN
        }

        def groupsAfterBaseUrl = repoUrl.split("https://github.com/")[1].split("/")

        String user = groupsAfterBaseUrl[0]
        if (!user?.trim()) {
            printToConsole("[$TAG] Could not identify user.")
            return GitRepo.UNKNOWN
        }

        String repoWithGitSuffix = groupsAfterBaseUrl[1]
        String repoName = repoWithGitSuffix.substring(0, repoWithGitSuffix.indexOf(".git"))

        if (!repoName?.trim()) {
            printToConsole("[$TAG] Could not identify repo.")
            return GitRepo.UNKNOWN
        }

        return new GitRepo(repoUrl, user, repoName)
    }

    /**
     * Update Git repository remote URL.
     * @param repo GitRepo object with info about the repo
     */
    protected void updateToSsh(GitRepo repo) {
        def newRepoUrl = "git@github.com:${repo.user}/${repo.repoName}.git"
        steps.echo("[$TAG] Changing repo url from\n '${repo.repoFullUrl}'\n to \n '${newRepoUrl}'")
        steps.sh(script: "git remote set-url origin ${newRepoUrl}", returnStdout: true).trim()
    }

    protected void printToConsole(String message) {
        steps.echo(message)
    }

    /**
     * Verifies if given repo url is HTTPS format.
     */
    private static boolean isRepoHttpsUrl(String repoUrl) {
        repoUrl ==~ "(http(s)?)(:(//)?)([\\w\\.@\\:/\\-~]+)(\\.git)(/)?"
    }

    /**
     * Extracts repo url from Git SCM
     * @param scm
     */
    private String repoUrlFromScm(scm) {
        scm.getUserRemoteConfigs()[0].getUrl()
    }

    static final class GitRepo implements Serializable {
        def repoFullUrl
        def user
        def repoName

        static final def UNKNOWN = new GitRepo(null, null, null)

        GitRepo(repoUrl, user, repo) {
            this.repoFullUrl = repoUrl
            this.user = user
            this.repoName = repo
        }
    }
}
