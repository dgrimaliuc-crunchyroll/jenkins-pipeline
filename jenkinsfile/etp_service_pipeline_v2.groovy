@Library('ellation') _

import com.ellation.web.Config
import com.ellation.history.ServiceHistoryEntry
import com.ellation.registry.ServiceRegistryEntry
import com.ellation.pipeline.JsonSchemaServicePipelineCommonSteps

Config config = etp.webConfig()
def ticket
Map<String, ServiceHistoryEntry> serviceHistoryEntries = [:]

node("docker") {
    // Fixes issue with ef-open tools for Jenkins Docker
    env.JENKINS_DOCKER = "TRUE"
    ticket = config.canCreateTicket() && config.deployWithHarness == false ? deployticket(config) : null

    try {
        stage("Build") {
            ticket?.transition(deployticket.states.BUILD)
            ServiceRegistryEntry serviceEntry
            List<ServiceRegistryEntry> subServiceEntries = []
            dir("service_registry") {
                gitWrapper.checkoutRepository(branch: config.deployBranch, credentialsId: env.ETP_SSH_KEY_CREDENTIALS_ID,
                        url: "git@github.com:crunchyroll/ellation_formation.git")
                serviceEntry = serviceRegistry.parseServiceRegistryJson("./service_registry.json", config.service)
                for (String subService in serviceEntry.getSubServices()) {
                    ServiceRegistryEntry subServiceEntry = serviceRegistry.parseServiceRegistryJson("./service_registry.json", subService)
                    subServiceEntries.add(subServiceEntry)
                }
            }
            String gitCredentials = env.ETP_SSH_KEY_CREDENTIALS_ID
            String buildBranch = config.branch
            // Add parent service logic for knowing what ami to build off of for the build AMI
            // String parentService = ""
            serviceHistoryEntries = buildService.buildForOldJenkins(config, gitCredentials, serviceEntry,
                    subServiceEntries)
        }

        stage("Update Service AMIs") {
            serviceHistoryEntries.each { serviceName, serviceHistoryEntry ->
                String commitHash = serviceHistoryEntry.toRepositoryCommitHashString()
                boolean noPreCheck = true
                boolean stable = false
                efVersion.setAmiId(serviceName, config.environment, serviceHistoryEntry.getAmiId(), commitHash,
                        noPreCheck, stable)
            }
        }

        stage("API Docs") {
            generateApiDocs.generate(config.service, config.environment, config.branch, config.repository)
        }

        JsonSchemaServicePipelineCommonSteps jsonSchemaService = new JsonSchemaServicePipelineCommonSteps(config, this)
        if (jsonSchemaService.isSchemaStoreIntegrationEnabled()) {
            stage("Schema Store Upload") {
                jsonSchemaService.upload(serviceHistoryEntries[config.service])
            }
        }

        stage("Deploy") {
            if (config.deployWithHarness) {
                println("Deploying with Harness")
                serviceHistoryEntries.each { serviceName, serviceHistoryEntry ->
                    ellation_formation.tagResource(serviceHistoryEntry.getAmiId(), "Name", serviceName, "ellationeng")
                    ellation_formation.tagResource(serviceHistoryEntry.getAmiId(), "Deployment", "Harness", "ellationeng")
                }
                // End stage early when deploying with harness
                return
            }

            ticket?.transition(deployticket.states.STAGING_DEPLOY)
            deployticket.addStagingInformation(ticket)
            int waitTime = config.waitTime
            efCf.deployService(config.service, config.environment, config.deployBranch)
            deployticket.addRelevantLinks(ticket)
            println "waiting for ${waitTime}"
            sleep waitTime
        }

        // End pipeline early when deploying with harness
        if (config.deployWithHarness) {
            return
        }

        // Notify NewRelic that a deploy occurred to the services
        serviceHistoryEntries.each { serviceName, serviceHistoryEntry ->
            newRelic.publishServiceDeployToNewRelicV2(config.environment, serviceName,
                    serviceHistoryEntry.toRepositoryCommitHashString())
        }

        stage("Post Deploy Test") {
            if (config.runPostDeployTest) {
                ticket?.transition(deployticket.states.AUTOMATED_TESTS)
                String jobName = "${config.service}-automated-test-${config.environment}"
                if (!Jenkins.instance.getItemByFullName(jobName)) {
                    println("${jobName} test job not found, post deploy test will fail.")
                }
                String currentDate = new Date().format("yyyy-MM-dd")
                String runValue = "${config.service}-pipeline-${env.BUILD_NUMBER} ${currentDate}"
                String commitHash = serviceHistoryEntries[config.service].toRepositoryCommitHashString()
                def params = [string(name: "run", value: runValue),
                              string(name: "COMMIT_HASH", value: commitHash)]
                try {
                    build(job: jobName, parameters: params)
                } catch (Exception error) {
                    println("Post deploy test failed. Rolling back.")
                    def parameters = [string(name: "ENVIRONMENT", value: config.environment),
                                      string(name: "SERVICE", value: config.service),
                                      string(name: "SUBSERVICES", value: config.subservices),
                                      string(name: "PERCENT", value: "100")]
                    if (config.runRollback) {
                        build(job: "etp-service-rollback", parameters: parameters)
                    }
                    ticket?.comment("Service failed post deploy test. Check pipeline url for more details")
                    ticket?.failAndClose()
                    throw error
                }
                ticket?.transition(deployticket.states.MANUAL_QA)
            } else {
                ticket?.transition(deployticket.states.STAGING_TO_MANUAL_QA)
            }
            ticket?.clearManualQAColumn()
        }

        stage("Mark Stable Service AMIs") {
            if (config.runPostDeployTest == true) {
                serviceHistoryEntries.each { serviceName, serviceHistoryEntry ->
                    String commitHash = serviceHistoryEntry.toRepositoryCommitHashString()
                    boolean noPreCheck = true
                    boolean stable = true
                    efVersion.setAmiId(serviceName, config.environment, serviceHistoryEntry.getAmiId(), commitHash,
                            noPreCheck, stable)
                }
            } else {
                echo("Skipping since no post deploy tests were run or environment is not staging.")
            }
        }
    } catch (Exception error) {
        if (config.environment == "staging") {
            slackSend(channel: "#deploy-staging", message: "${config.service}-pipeline-${config.environment} failed")
        }
        throw error
    }

    stage("Notifications") {
        if (config.notifications.length() > 0) {
            for (String channelName in config.notifications.split(',')) {
                slackSend(channel: channelName.trim(), message: "${config.service} has finished on ${config.environment}")
            }
        }
    }

    stage("Artifact Information") {
        serviceHistoryEntries.each { name, serviceHistoryEntry ->
            echo("name is ${name}")
            echo("ami is ${serviceHistoryEntry.getAmiId()}")
            echo("pipeline number is ${serviceHistoryEntry.getPipelineRunNumber()}")
            serviceHistoryEntry.getRepositoryCommitHash().each { repoName, commitHash ->
                echo("${repoName} : ${commitHash}")
            }
        }
    }

    cleanWs()
}
