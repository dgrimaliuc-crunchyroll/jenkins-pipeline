@Library('ellation') _

import com.ellation.pipeline.ServicePipeline
import com.ellation.web.Config
import org.jenkinsci.plugins.workflow.libs.Library
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

Config config = etp.webConfig()
ServicePipeline servicePipeline = etp.servicePipeline(this, config)
def ticket
servicePipeline.setJobFailureBehavior {
    ticket?.fail()
}

servicePipeline.setTestFailureBehavior {
    echo("Test failed!")
    if (config.postDeployTestFailNotifications != "") {
        slackSend(channel: "${config.postDeployTestFailNotifications}", message: "${config.service}-pipeline-${config.environment} post-deploy tests failed")
    }
}

node("universal") {
    Map<String, RunWrapper> buildJobs = [:]
    ticket = config.canCreateTicket() ? deployticket(config) : null

    stage("Clean workspace") {
        cleanWs()
    }

    stage("Unit Test") {
        servicePipeline.unitTests()
    }

    stage("Build") {
        ticket?.transition(deployticket.states.BUILD)
        servicePipeline.build(config)
        buildJobs = servicePipeline.getServiceBuildJobs()
    }

    stage("Update Service AMIs") {
        servicePipeline.updateServiceVersion(false, true)
    }

    stage("API Docs") {
        servicePipeline.generateApiDocs()
    }

    if (servicePipeline.schemaStoreIntegrationEnabled()) {
        stage("Schema Store Upload") {
            servicePipeline.uploadJsonSchema()
        }
    }

    stage("Deploy") {
        ticket?.transition(deployticket.states.STAGING_DEPLOY)
        deployticket.addStagingInformation(ticket)
        servicePipeline.deploy()
        deployticket.addRelevantLinks(ticket, buildJobs)
    }

    stage("Post Deploy Test") {
        if (config.testTarget.toLowerCase() != "none") {
            ticket?.transition(deployticket.states.AUTOMATED_TESTS)
            servicePipeline.postDeployTests()
            ticket?.transition(deployticket.states.MANUAL_QA)
        } else {
            ticket?.transition(deployticket.states.STAGING_TO_MANUAL_QA)
        }
        ticket?.clearManualQAColumn()
    }
    stage("Mark Stable Service AMIs") {
        if (config.runPostDeployTest == true) {
            servicePipeline.updateServiceVersion(true, true)
        } else {
            echo("Skipping since no post deploy tests were run or environment is not staging.")
        }
    }
    stage("Notifications") {
        servicePipeline.publishNotifications()
    }
    stage("Artifact Information") {
        servicePipeline.displayArtifactInformation()
    }
}
