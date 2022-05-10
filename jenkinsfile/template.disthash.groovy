@Library('ellation') _
import com.ellation.web.Config
import com.ellation.ServicePipeline
import org.jenkinsci.plugins.workflow.libs.Library
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper
import com.ellation.deploy.DeployTicket

Config config = new Config(env, params)
ServicePipeline servicePipeline  = new ServicePipeline()
DeployTicket ticket

servicePipeline.withCredentials(config.credentials)
servicePipeline.fromBranch(config.branch)

servicePipeline.onFailure {
    ticket?.comment("Pipeline has failed. Check pipeline url for more details")
    ticket?.failAndClose()
}

node("universal") {
    Map<String, RunWrapper> buildJobs = [:]
    // Make this a class with a constructor, don't use a call - 02-06-2019 Vince
    servicePipeline(config)

    stage("Clean workspace") {
        cleanWs()
    }

    stage("Unit Test") {
        if(config.technology) {
            servicePipeline.unitTest(config.repository, config.technology)
        }
    }
    if (config.canCreateTicket()) {
        ticket = deployticket(config)
    }
    stage("Build") {
        ticket?.transition(deployticket.states.BUILD)
        buildJobs = servicePipeline.buildServiceDistHash()
    }
    stage("Update Dist Hash") {
      buildJobs.each { serviceName, buildJob ->
          servicePipeline.updateDistHash(serviceName, buildJobs[serviceName])
      }
    }
    if (servicePipeline.schemaStoreIntegrationEnabled()) {
        stage("Schema Store Upload") {
            servicePipeline.uploadJsonSchemaDistHash()
        }
    }
    stage("Deploy") {
        ticket?.transition(deployticket.states.STAGING_DEPLOY)
        servicePipeline.deployDistHash(config.waitTime as int)
        deployticket.addRelevantLinks(ticket, buildJobs)
        deployticket.addStagingInformation(ticket)

    }
    stage("Post Deploy Test") {
        if (config.testTarget != 'none') {
            ticket?.transition(deployticket.states.AUTOMATED_TESTS)
            servicePipeline.test(config.testTarget, buildJobs[config.service])
            ticket?.transition(deployticket.states.MANUAL_QA)
        } else {
            ticket?.transition(deployticket.states.STAGING_TO_MANUAL_QA)
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
