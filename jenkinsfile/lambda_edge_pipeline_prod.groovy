@Library('ellation') _
import org.jenkinsci.plugins.workflow.libs.Library

import com.ellation.ef.ServiceHistory

// Define the parameters of the pipeline. If you edit the parameters, it will be changed back to this when you run it.
properties(
    [parameters([
        string(name: "COMMIT_HASH", defaultValue: "", description: "The commit_hash to deploy", trim: true)
    ])
])

String environment = "prod"
String service = env.ETP_SERVICE?.trim() ? env.ETP_SERVICE.trim() : ""
String commitHash = params.COMMIT_HASH?.trim() ? params.COMMIT_HASH?.trim() : ""

ArrayList<String> cloudfrontDistros = (env.CLOUDFRONT_DISTROS) ? env.CLOUDFRONT_DISTROS?.split(",") : []
for(int i = 0; i < cloudfrontDistros.size(); i++) {
    cloudfrontDistros[i] = cloudfrontDistros[i].trim()
}

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

    if (cloudfrontDistros.size() < 1) {
        error("No cloudfront distros were specified for a lambda edge")
    }

    List<String> lambdas = []
    stage("Find lambdas") {
        String templateString = efCf.renderLambdaTemplate(service, environment)
        Map<String, Object> templateJson = jsonHelper.stringToJson(templateString)
        lambdas = cloudformation.findLambdas(templateJson)
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

    }
    stage("Publish lambda@edge") {
        lambdas.each { lambdaName ->
            String publishVersionScript = "aws --profile ellation --region us-east-1 lambda publish-version --function-name ${environment}-${lambdaName} | jq '.Version' -r"
            String versionNumber = sh(script: publishVersionScript, returnStdout: true).trim()
            efVersion.setVersionNumber(lambdaName, environment, versionNumber, commitHash)
        }

    }

    stage("Update Cloudfront Stacks") {
        // Update the cloudformation stack of the cloudfront stacks if lambda edge
        cloudfrontDistros.each { cloudfront ->
            efCf.deployFixture(cloudfront, environment)
        }
    }
}
