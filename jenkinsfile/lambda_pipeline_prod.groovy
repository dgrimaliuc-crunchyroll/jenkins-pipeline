@Library('ellation') _
import org.jenkinsci.plugins.workflow.libs.Library

import com.ellation.ef.ServiceHistory
import com.ellation.web.Config

// Define the parameters of the pipeline. If you edit the parameters, it will be changed back to this when you run it.
properties(
    [parameters([
        string(name: "COMMIT_HASH", defaultValue: "", description: "The commit_hash to deploy", trim: true)
    ])
])

String environment = "prod"
String service = env.ETP_SERVICE?.trim() ? env.ETP_SERVICE.trim() : ""
String commitHash = params.COMMIT_HASH?.trim() ? params.COMMIT_HASH?.trim() : ""
Config config = new Config(env, params)

node("universal") {

    stage("Clean workspace") {
        cleanWs()
    }

    if (service == "") {
        error("field ETP_SERVICE was not defined or an empty string")
    }

    if (commitHash == "") {
        error("parameter COMMIT_HASH was not defined or an empty string")
    }

    List<String> lambdas = []
    stage("Find lambdas") {
        String templateString = efCf.renderLambdaTemplate(service, environment)
        Map<String, Object> templateJson = jsonHelper.stringToJson(templateString)
        lambdas = cloudformation.findLambdas(templateJson)

        // In multi lambda stacks, we need to add the parent service even though it's not a real lambda for commit hash
        // history for DPLY tickets
        if (!lambdas.contains(service)) {
            lambdas.add(service)
        }
    }

    stage("Update commit Hash") {
        lambdas.each { lambdaName ->
            ServiceHistory history = efVersion.history(lambdaName, "staging", "commit-hash")
            def stagingVersion = history.entries.find{
                it["value"] == commitHash && it["status"] == "stable"
            }
            if (stagingVersion == null) {
                error("Could not find stable staging version with commit-hash ${commitHash}")
            }
            efVersion.setCommitHash(lambdaName, environment, commitHash)
        }
    }

    stage("Deploy") {
        efCf.deployLambda(service, environment)
        if(awsApiGateway.checkApiGatewayDeploymentEnvVars()) {
            awsApiGateway.createDeployment(config)
        }
    }
}
