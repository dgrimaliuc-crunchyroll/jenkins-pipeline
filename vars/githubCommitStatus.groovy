#!/usr/bin/groovy

def call(context, message, state, repoUrl = null, commitSha = null) {
    if (!repoUrl) {
        sh "git config --get remote.origin.url > .git/remote-url"
        repoUrl = readFile(".git/remote-url").trim()
    }

    if (!commitSha) {
        sh "git rev-parse HEAD > .git/current-commit"
        commitSha = readFile(".git/current-commit").trim()
    }

    step([
            $class            : "GitHubCommitStatusSetter",
            contextSource     : [$class: "ManuallyEnteredCommitContextSource", context: context],
            reposSource       : [$class: "ManuallyEnteredRepositorySource", url: repoUrl],
            commitShaSource   : [$class: "ManuallyEnteredShaSource", sha: commitSha],
            statusResultSource: [$class: "ConditionalStatusResultSource", results: [
                    [$class: "AnyBuildResult", message: message, state: state]
            ]]
    ])
}
