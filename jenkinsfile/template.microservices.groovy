@Library('ellation') _

def config    = etp.webConfig()
def pipeline  = etp.pipeline()
def ticket

pipeline.withCredentials(config.credentials)
pipeline.fromBranch(config.branch)

pipeline.whenTestFails {
    if (config.environment == "staging") {
        currentBuild.description = currentBuild.description + ' triggered rollback'
        build job: 'etp-service-rollback', parameters: [string(name: 'ENVIRONMENT', value: config.environment), \
                string(name: 'SERVICE', value: config.service), string(name: 'SUBSERVICES', value: config.subservices)], wait: false
    }

}

pipeline.onFailure {
    ticket?.comment("Pipeline has failed. Check pipeline url for more details")
    ticket?.fail()
}

pipeline(config) {
    if (config.technology) {
        unitTest(config.repository, config.technology)
    }

    if (config.canCreateTicket()) {
        ticket = deployticket(config)
    }

    ticket?.transition(deployticket.states.BUILD)

    node('universal') {
        def microServices =[:]
        def amiList = [:]
        microServices["${config.service}"] = {
            def serviceBuildJob = build job: "${config.service}-build", parameters: [string(name: 'ENVIRONMENT', value: "${config.environment}"), string(name: 'BUILDBRANCH', value: "${config.branch}")]
            pipeline.buildJobs["${config.service}"] = serviceBuildJob
            // Get ami-id from build output
            def script = "curl --silent '${serviceBuildJob.getAbsoluteUrl()}consoleText' | grep 'amazon-ebs' | grep 'AMI' | grep -Po 'ami-[a-zA-Z0-9]+'| grep -v 'tags' | cut -d':' -f 3 | tail -n1"
            def amiId = sh(
                script: script,
                returnStdout: true
            ).trim()

            println("AMI ID: ${amiId}")
            pipeline.listAmiId["${config.service}"] = "${amiId}"

            // TODO Move to a method
            def buildUrl = "${serviceBuildJob.getAbsoluteUrl()}api/json"
            def response = httpRequest buildUrl
            def slurper = new groovy.json.JsonSlurper()
            def json = slurper.parseText(response.getContent())
            def sha = null

            for (def action in json.actions) {
                if (action._class == "hudson.plugins.git.util.BuildData") {
                    sha = action.lastBuiltRevision.SHA1
                }
            }

            if (!sha) {
                throw new Exception("Could not determine git commit")
            }
            println("GIT COMMIT: ${sha}")

            // Null these out because jenkins cannot serialize slurper objects
            slurper = null
            json = null

            try{
                ellation_formation.set("${config.service}", "${config.environment}", amiId, [commit_hash: sha , build: "${env.BUILD_ID}", noprecheck: true])
            } catch (Exception e) {
                echo "Failed to update ellation formation version."
            }
        }

        // Add build jobs for each ETP_SUBSERVICES
        def subServices = config.subservices.split(",")
        for (int i = 0; i < subServices.length; i++){
            def index = i
            def svc = subServices[index]
            microServices["${svc}"] = {
                def serviceBuildJob = build job: "${svc}-build", parameters: [string(name: 'ENVIRONMENT', value: "${config.environment}"), string(name: 'BUILDBRANCH', value: "${env.BUILDBRANCH}")]
                pipeline.buildJobs["${svc}"] = serviceBuildJob
                // Get ami-id from build output
                def script = "curl --silent '${serviceBuildJob.getAbsoluteUrl()}consoleText' | grep 'amazon-ebs' | grep 'AMI' | grep -Po 'ami-[a-zA-Z0-9]+'| grep -v 'tags' | cut -d':' -f 3 | tail -n1"
                def amiId = sh(
                    script: script,
                    returnStdout: true
                ).trim()

                println("AMI ID: ${amiId}")
                pipeline.listAmiId["${svc}"] = "${amiId}"

                ellation_formation.set("${svc}", "${config.environment}", amiId, [noprecheck: true])
            }
        }

        stage("Build") {
            echo "Building all services"
            failFast: true
            parallel microServices
        }

        stage("Docgen") {
            if ( config.repository && config.environment != 'prod') {
                generateApiDocs(config.repository)
            }
        }

        // Deploy pipeline
        ticket?.transition(deployticket.states.STAGING_DEPLOY)
        deploy(config.waitTime as int)

        ticket?.addBuildInformation(pipeline)

        if (config.testTarget != 'none') {
            ticket?.transition(deployticket.states.AUTOMATED_TESTS)
            test(config.testTarget)
            ticket?.transition(deployticket.states.MANUAL_QA)
        } else {
            ticket?.transition(deployticket.states.STAGING_TO_MANUAL_QA)
        }

        stage("Artifacts") {
            echo "Deploy complete for the following services"
            pipeline.listAmiId.each{ k, v -> println "${k} AMI-ID : ${v}"}
        }

        deployticket.clearColumn("Manual QA", ticket, config.service)

        markStable()

        newRelic.publishServiceDeployToNewRelic(pipeline, pipeline.getBuildJobCommit())

        if (config.notifications) {
            def parts = config.notifications.split(",")
            for (i = 0; i < parts.length; i++) {
                slackSend channel: parts[i], message: "${config.service} has finished on ${config.environment}"
            }
        }
    }
}
