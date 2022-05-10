import groovy.json.JsonOutput

import com.ellation.git.GitRepoUrl
import com.ellation.web.Config

/**
 * Create parameters for the Lambda Projects in CircleCI that are using the lambda orb and invoke the project's
 * pipeline in CircleCI.
 * @param config
 * @param isEdge
 */
void triggerLambdaPipeline(Config config, boolean isEdge) {
    Map<String, Object> params = [
            "parameters": [
                    "manual_trigger": true,
                    "is_edge": isEdge
            ],
            "branch": "${config.branch}"
    ]
    triggerPipeline(config, params)
}

/**
 * Curl call the project's pipeline in the CircleCI so that new artifacts are made
 * @param config
 * @param params
 */
private void triggerPipeline(Config config, Map<String, Object> params) {
    GitRepoUrl gitRepoUrl = new GitRepoUrl("${config.repository}")
    withCredentials([string(credentialsId: "circleci_api_token_for_jenkins", variable: 'api_token')]) {
        String command = "curl -u \"${api_token}:\" -X POST --header \"Content-Type: application/json\" " +
                "-d \'${JsonOutput.toJson(params)}\' " +
                "https://circleci.com/api/v2/project/github/crunchyroll/${gitRepoUrl.repoName}/pipeline"
        echo(command)
        sh(returnStdout: true, script: command)
    }
}
