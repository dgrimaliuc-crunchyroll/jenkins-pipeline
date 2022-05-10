@Library('ellation') _
import org.jenkinsci.plugins.workflow.libs.Library

import com.ellation.web.Config

Config config = new Config(env, params)
ArrayList<String> cloudfrontDistros = (env.CLOUDFRONT_DISTROS) ? env.CLOUDFRONT_DISTROS.trim().split(",") : []
for(int i = 0; i < cloudfrontDistros.size(); i++) {
    cloudfrontDistros[i] = cloudfrontDistros[i].trim()
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
        circleCi.triggerLambdaPipeline(config, true)
    }

    stage("Wait for lambda package to show up in S3") {
        int numberOfItems = 0
        // Wait 10 minutes before giving up
        for(int i = 0; i < 10; i++) {
            String result = sh(returnStdout: true, script: "aws s3 ls --summarize s3://ellation-cx-global-dist-lambda-edge/lambdas/${config.service}/${commitHash}/ | grep 'Total Objects' | cut -d ':' -f 2 | tr -d ' '").trim()
            numberOfItems = Integer.parseInt(result)
            echo("Number of items: ${numberOfItems}")
            if (numberOfItems < 1) {
                sleep 60
            } else {
                break
            }
        }
        if (numberOfItems == 0) {
            error("Lambda pakage never showed up in S3. Check CircleCI to see what's going on with the build.")
        }
    }

    List<String> lambdas = []
    stage("Find lambdas") {
        String templateString = efCf.renderLambdaTemplate(config.service, config.environment, config.deployBranch)
        Map<String, Object> templateJson = jsonHelper.stringToJson(templateString)
        lambdas = cloudformation.findLambdas(templateJson)
    }

    stage("Update commit Hash") {
        lambdas.each { lambdaName ->
            efVersion.setCommitHash(lambdaName, config.environment, commitHash, false)
        }
    }

    stage("Deploy") {
        efCf.deployLambda(config.service, config.environment, config.deployBranch)
    }

    // Update version number for lambda if lambda edge
    // Tests should run on the $LATEST version, and we publish the version after that
    stage("Publish lambda@edge") {
        lambdas.each { lambdaName ->
            def publishVersionScript = "aws --profile ellationeng --region us-east-1 lambda publish-version --function-name ${config.environment}-${lambdaName} | jq '.Version' -r"
            String versionNumber = sh(script: publishVersionScript, returnStdout: true).trim()
            efVersion.setVersionNumber(lambdaName, config.environment, versionNumber, commitHash, false)
        }
    }

    stage("Update Cloudfront Stacks") {
        // Update the cloudformation stack of the cloudfront stacks if lambda edge
        cloudfrontDistros.each { cloudfront ->
            efCf.deployFixture(cloudfront, config.environment)
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
