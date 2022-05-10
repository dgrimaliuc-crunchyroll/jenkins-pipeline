@Library('ellation') _
import com.ellation.web.Config
import com.ellation.ServicePipeline
import org.jenkinsci.plugins.workflow.libs.Library
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

Config config = new Config(env, params)
ServicePipeline servicePipeline  = new ServicePipeline()

servicePipeline.withCredentials(config.credentials)
servicePipeline.fromBranch(config.branch)

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
    stage("Build") {
        buildJobs = servicePipeline.buildServicesInParallel(config)
    }
    stage("Update Service AMIs") {
        buildJobs.each { serviceName, buildJob ->
            servicePipeline.updateServiceAMI(serviceName, buildJob)
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
        servicePipeline.deploy(config.waitTime)
    }
    stage("Post Deploy Test") {
        if (config.testTarget != 'none') {
          servicePipeline.test(config.testTarget, buildJobs[config.service])
        }
    }
    newRelic.publishServiceDeployToNewRelic(servicePipeline, servicePipeline.getBuildJobCommitV2(buildJobs[config.service]))
    if (config.notifications) {
        //can not use a simpler form because of JENKINS-26481
        String[] parts = config.notifications.split(",")
        for (int index = 0; index < parts.size(); index++) {
            slackSend(channel: parts[index], message: "${config.service} has finished on ${config.environment}")
        }
    }
}
