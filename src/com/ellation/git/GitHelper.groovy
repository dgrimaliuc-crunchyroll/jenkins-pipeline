package com.ellation.git

class GitHelper implements Serializable {

    def steps

    GitHelper(pipelineSteps) {
        steps = pipelineSteps
    }

    /**
     * Must be run in the same dir as the git repo.
     * Return an array of change sets,  each change set has the key and commitId to be compatible with
     * build.changeSets[i].items. At the moment, changeSets can not be used because 1, we can not identify the git repo
     * if we do several checkouts and 2, we can not get the changeset since last successful build
     * @param startCommit
     * @param endCommit
     * @return Map
     */
    Map getChangelog(newCommit, oldCommit) {
        Map meta = [
                'new_commit' : newCommit,
                'old_commit' : oldCommit,
                'has_changes': false,
                'commits'    : [],
        ]

        if (newCommit == oldCommit) {
            return meta
        }

        def changes = getCommitData(oldCommit, newCommit, GitFormat.HASHES_AND_BODIES)

        meta['has_changes'] = true
        meta['commits'] = changes.collect { CommitInfo.from(it) }
        return meta
    }

    String[] getCommitData(startCommit, endCommit, GitFormat format = GitFormat.BODIES) {
        // null byte (x00) is used to separate commits' messages bodies
        // this is unique separator which can't be found in commit messages ever (prohibited by git)
        String command = ""
        if (startCommit != "None") {
            command = "git log --format=tformat:${format.value}%x00 $startCommit..$endCommit"
        } else {
            command = "git log --format=tformat:${format.value}%x00 $endCommit"
        }
        return steps
                .sh(script: command, returnStdout: true)
                .split("\0${System.lineSeparator()}")
                .findAll { it.trim().size() > 0 }
    }

    String[] getCommitsAuthorEmails(startCommit, endCommit) {
        def command = "git log --format=tformat:%ae $startCommit..$endCommit"
        return steps
                .sh(script: command, returnStdout: true)
                .split(System.lineSeparator())
                .collect { it.trim() }
    }

    protected String getCommitMessageBody(commit) {
        return steps.sh(script: "git show -s --format=%B $commit", returnStdout: true)
    }

    protected String getCommitAuthorEmail(commit) {
        return steps.sh(script: "git --no-pager show -s --format=%ae $commit", returnStdout: true).trim()
    }

    String getCurrentCommit() {
        return steps.sh(script: "git log -n 1 --pretty=format:'%H'", returnStdout: true).trim()
    }

    String[] getChangedFiles(parentBranch = "master", commitHash = null) {
        def gitCommitHash = commitHash ?: getCurrentCommit()
        steps.sh("git config --add remote.origin.fetch +refs/heads/${parentBranch}:refs/remotes/origin/${parentBranch}")
        steps.sh("git fetch --no-tags")
        /**
        Notes on the below command:
            1) Diff-filter is used to hide deleted files as they cannot be tested during ci.
            2) The three dots between the parentBranch and gitCommitHash creates a diff against the merge-base of our branch rather
               than the HEAD of the parentBranch.
            3) Diff-filter does not work correctly on the centos7 default git v1.8.3.1-20.el7, make sure git > 2.0 is installed from ius.
        **/
        return steps.sh(returnStdout: true, script: "git diff --name-only --diff-filter=d origin/${parentBranch}...${gitCommitHash}").split()
    }

    /**
     * Get all changed filepath in a pr grouped by directory.
     *
     * @return object with all changed files in the pr, grouped by root-level dir
     *   ex. ['root': ['README.md'], 'src': ['src/bin/myscript.groovy', 'src/conf/my.conf']]
     */
    Map getChangedFilesGrouped(parentBranch = "master", commitHash = null) {
        def gitCommitHash = commitHash ?: getCurrentCommit()
        def sourceChanged = getChangedFiles(parentBranch, gitCommitHash)
        def changedFiles = [:]
        def splitFile
        def rootDir

        changedFiles['root'] = []
        sourceChanged.each { file ->
            splitFile = file.tokenize("/")
            rootDir = splitFile[0]
            if (splitFile.size() > 1) {
                if (changedFiles.containsKey(rootDir)) {
                    changedFiles[rootDir].add(file)
                } else {
                    changedFiles[rootDir] = [file]
                }
            } else {
                changedFiles['root'].add(file)
            }
        }

        return changedFiles
    }

}
