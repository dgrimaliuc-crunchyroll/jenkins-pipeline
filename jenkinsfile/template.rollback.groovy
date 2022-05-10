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

servicePipeline.whenTestFails {
    // Regardless of rollback, notify about failure
    if (config.postDeployTestFailNotifications != "") {
        slackSend(channel: "${config.postDeployTestFailNotifications}", message: "${config.service}-pipeline-${config.environment} post-deploy tests failed")
    }

    currentBuild.description = currentBuild.description + ' triggered rollback'
    build job: 'etp-service-rollback', parameters: [string(name: 'ENVIRONMENT', value: config.environment), \
              string(name: 'SERVICE', value: config.service), string(name: 'SUBSERVICES', value: config.subservices)], wait: false
}

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
        if (config.technology) {
            servicePipeline.unitTest(config.repository, config.technology)
        }
    }
    if (config.canCreateTicket()) {
        ticket = deployticket(config)
    }
    stage("Build") {
        ticket?.transition(deployticket.states.BUILD)
        buildJobs = servicePipeline.buildServicesInParallel(config)
    }
    stage("Update Service AMIs") {
        buildJobs.each { serviceName, buildJob ->
            servicePipeline.updateServiceAMI(serviceName, buildJob, [pipeline_build: currentBuild.number])
        }
    }
    stage("API Docs") {
        if(config.repository && config.environment != 'prod') {
            servicePipeline.generateApiDocs(config.repository)
        }
    }
    if (servicePipeline.schemaStoreIntegrationEnabled()) {
        stage("Schema Store Upload") {
            servicePipeline.uploadJsonSchemaAMI()
        }
    }
    stage("Deploy") {
        ticket?.transition(deployticket.states.STAGING_DEPLOY)
        servicePipeline.deploy(config.waitTime)
        deployticket.addStagingInformation(ticket)
        deployticket.addRelevantLinks(ticket, buildJobs)
    }
    stage("Post Deploy Test") {
        if (config.testTarget != 'none') {
            ticket?.transition(deployticket.states.AUTOMATED_TESTS)
            servicePipeline.test(config.testTarget, buildJobs[config.service])
            ticket?.transition(deployticket.states.MANUAL_QA)
        } else {
            ticket?.transition(deployticket.states.STAGING_TO_MANUAL_QA)
        }
        ticket?.clearManualQAColumn()
    }
    stage("Mark Stable Service AMIs") {
        buildJobs.each { serviceName, buildJob ->
            servicePipeline.markStableV2(serviceName, buildJob, [pipeline_build: currentBuild.number])
        }
    }
    newRelic.publishServiceDeployToNewRelic(servicePipeline, servicePipeline.getBuildJobCommitV2(buildJobs[config.service]))
    if(config.notifications) {
        def parts = config.notifications.split(",")

        for (i =0; i<parts.length;i++) {
            slackSend channel: parts[i], message: "${config.service} has finished on ${config.environment}"
        }
    }
}
