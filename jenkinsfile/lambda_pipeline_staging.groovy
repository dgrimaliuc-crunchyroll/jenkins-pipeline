@Library('ellation') _
import org.jenkinsci.plugins.workflow.libs.Library

import com.ellation.deploy.DeployTicket
import com.ellation.web.Config

Config config = new Config(env, params)
DeployTicket ticket

node("universal") {

    stage("Clean workspace") {
        cleanWs()
    }

    // TODO: we need to close the previous OPEN ticket if it exists, otherwise we will have duplicate tickets
    if (config.canCreateTicket()) {
        ticket = deployticket(config)
    }

    // Even though building is already done before this pipeline starts, we need to transition ticket
    // from SELECTED_FOR_DEPLOY -> BUILD -> STAGING_DEPLOY
    // else we get errors from JIRA
    ticket?.transition(deployticket.states.BUILD)

    List<String> lambdas = []
    stage("Find lambdas") {
        String templateString = efCf.renderLambdaTemplate(config.service, config.environment)
        Map<String, Object> templateJson = jsonHelper.stringToJson(templateString)
        lambdas = cloudformation.findLambdas(templateJson)

        // In multi lambda stacks, we need to add the parent service even though it's not a real lambda for commit hash
        // history for DPLY tickets
        if (!lambdas.contains(config.service)) {
            lambdas.add(config.service)
        }
    }

    String commitHash = ""
    stage("Update commit Hash") {
        commitHash = config.commitHash
        // Return error if commit hash is not provided
        if (commitHash == "") {
            error(message: "No commit hash provided")
        }
        // Make sure that the commit hash belongs to the current repo
        gitWrapper.checkoutRepository(branch: "master", credentialsId: env.ETP_SSH_KEY_CREDENTIALS_ID,
                url: config.repository)
        String isValidHashScript = "git cat-file -t " + config.commitHash
        String isValidHash = sh(returnStdout: true, script:isValidHashScript)
        isValidHash = isValidHash.trim()
        if (isValidHash != "commit") {
            error(message: "Commit provided is not in master branch of repository")
        }
        lambdas.each { lambdaName ->
            efVersion.setCommitHash(lambdaName, config.environment, commitHash, false)
        }
    }

    stage("API Docs") {
        generateApiDocs.generate(config.service, config.environment, config.branch, config.repository)
    }

    stage("Deploy") {
        ticket?.transition(deployticket.states.STAGING_DEPLOY)
        efCf.deployLambda(config.service, config.environment)
        if(awsApiGateway.checkApiGatewayDeploymentEnvVars()) {
            awsApiGateway.createDeployment(config)
        }
        deployticket.addStagingInformation(ticket)
        deployticket.addRelevantLinks(ticket)
    }

    stage("Post Deploy Test") {
        ticket?.transition(deployticket.states.AUTOMATED_TESTS)

        String testJob = "${config.service}-automated-test-staging"

        // What parameters need to be passed to the test job?
        def params = [string(name: "COMMIT_HASH", value: commitHash)]

        try {
            if (Jenkins.instance.getItemByFullName(testJob) != null) {
                build(job: testJob, parameters: params)
            } else {
                // If test does not exist, echo and let it pass
                echo("The ${testJob} job was not found. Will consider this a test pass due to how things operate.")
            }
        } catch (Exception e) {
            // If test failed, close jira ticket and stop pipeline
            ticket?.comment("Pipeline has failed. Check pipeline url for more details")
            ticket?.failAndClose()
            // Call Rollback
            rollback(lambdas, config.service, config.environment)
            throw e
        }

        ticket?.transition(deployticket.states.MANUAL_QA)
        ticket?.clearManualQAColumn()
    }

    stage("Set stable") {
        lambdas.each { lambdaName ->
            efVersion.setCommitHash(lambdaName, config.environment, commitHash)
        }
    }

    if (config.notifications) {
        //can not use a simpler form because of JENKINS-26481
        String[] parts = config.notifications.split(",")
        for (int index = 0; index < parts.size(); index++) {
            slackSend(channel: parts[index], message: "${config.service} has finished on ${config.environment}")
        }
    }
}

def rollback(List<String> lambdas, String service, String environment) {
    lambdas.each { lambdaName ->
        efVersion.rollbackCommitHash(lambdaName, environment)
    }
    efCf.deployLambda(service, environment)
    if(awsApiGateway.checkApiGatewayDeploymentEnvVars()) {
        awsApiGateway.createDeployment(config)
    }
}
