@Library('ellation') _
import org.jenkinsci.plugins.workflow.libs.Library

import com.ellation.web.Config

// Define the parameters of the pipeline. If you edit the parameters, it will be changed back to this when you run it.
properties(
    [parameters([
        string(name: "BUILDBRANCH", defaultValue: "master", description: "Branch of Lambda repo to build from", trim: true),
        string(name: "DEPLOYBRANCH", defaultValue: "master", description: "Branch of ellation_formation to deploy", trim: true),
        string(name: "UPDATE_LAMBDA", defaultValue: "*", description: "Name of the lambda(s) to update. Comma " +
                "separated if you want to update multiple lambdas. Name of the lambda should match in the service registry.", trim: true)
    ])
])

Config config = new Config(env, params)
ArrayList<String> lambdasToUpdate = env.UPDATE_LAMBDA?.trim() ? env.UPDATE_LAMBDA.trim().split(",") : []
for(int i = 0; i < lambdasToUpdate.size(); i++) {
    lambdasToUpdate[i] = lambdasToUpdate[i].trim()
}

node("universal") {

    stage("Clean workspace") {
        cleanWs()
    }

    String commitHash = ""
    stage("Get commit hash") {
        gitWrapper.checkoutRepository(branch: config.branch, credentialsId: env.ETP_SSH_KEY_CREDENTIALS_ID,
                url: config.repository)
        String getCommitHashScript = "git rev-parse HEAD"
        commitHash = sh(returnStdout: true, script:getCommitHashScript)
        commitHash = commitHash.trim()
    }

    stage("CircleCi Build") {
        circleCi.triggerLambdaPipeline(config, false)
    }

    stage("Wait for lambda package to show up in S3") {
        int numberOfItems = 0
        // Wait 10 minutes before giving up
        for(int i = 0; i < 10; i++) {
            String result = sh(returnStdout: true, script: "aws s3 ls --summarize s3://ellation-cx-global-dist/lambdas/${config.service}/${commitHash}/ | grep 'Total Objects' | cut -d ':' -f 2 | tr -d ' '").trim()
            numberOfItems = Integer.parseInt(result)
            echo("Number of items: ${numberOfItems}")
            if (numberOfItems < 1) {
                sleep 60
            } else {
                break
            }
        }
        if (numberOfItems == 0) {
            error("Lambda package never showed up in S3.  Check CircleCI to see what's going on with the build.")
        }
    }

    List<String> lambdas = []
    stage("Find lambdas") {
        String templateString = efCf.renderLambdaTemplate(config.service, config.environment, config.deployBranch)
        Map<String, Object> templateJson = jsonHelper.stringToJson(templateString)
        lambdas = cloudformation.findLambdas(templateJson)

        // In multi lambda stacks, we need to add the parent service even though it's not a real lambda for commit hash
        // history for DPLY tickets
        if (!lambdas.contains(config.service)) {
            lambdas.add(config.service)
        }

        // If we wish to update some of the lambdas and not all of them
        if (lambdasToUpdate && !("*" in lambdasToUpdate)) {
            for (String lambdaName in lambdasToUpdate) {
                if (!(lambdaName in lambdas)) {
                    error("Lambda ${lambdaName} does not exist in this stack.")
                }
            }
            lambdas.clear()
            lambdas.addAll(lambdasToUpdate)
        }
    }

    stage("Update commit Hash") {
        lambdas.each { lambdaName ->
            efVersion.setCommitHash(lambdaName, config.environment, commitHash, false)
        }
    }

    stage("API Docs") {
        generateApiDocs.generate(config.service, config.environment, config.branch, config.repository)
    }

    stage("Deploy") {
        efCf.deployLambda(config.service, config.environment, config.deployBranch)
        if(awsApiGateway.checkApiGatewayDeploymentEnvVars()) {
            awsApiGateway.createDeployment(config)
        }
    }

    stage("Post Deploy Test") {

        String testJob = "${config.service}-automated-test-proto0"

        // What parameters need to be passed to the test job?
        def params = [string(name: "COMMIT_HASH", value: commitHash)]

        if (Jenkins.instance.getItemByFullName(testJob) != null) {
            build(job: testJob, parameters: params)
        } else {
            // If test does not exist, echo and let it pass
            echo("The ${testJob} job was not found. Will consider this a test pass due to how things operate.")
        }
    }
}
