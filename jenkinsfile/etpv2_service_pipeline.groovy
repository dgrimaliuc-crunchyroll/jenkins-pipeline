import com.ellation.history.ServiceHistoryEntry
import com.ellation.registry.ServiceRegistryEntry

@Library('ellation') _

node("docker") {
    String service = env.ETP_SERVICE?.trim()
    String account = env.ETP_ACCOUNT?.trim()
    String buildBranch = env.BUILDBRANCH?.trim() ?: "master"
    String deployBranch = env.DEPLOYBRANCH?.trim() ?: "master"
    String gitCredentials = env.ETP_SSH_KEY_CREDENTIALS_ID?.trim()
    String region = env.ETP_REGION?.trim
    String environment = env.ETP_ENVIRONMENT?.trim()
    boolean buildPhaseExists = true
    ServiceRegistryEntry mainServiceEntry
    List<ServiceRegistryEntry> subServiceEntries = []

    stage("Clean workspace") {
        cleanWs()
    }

    stage("Setup") {
        if (!gitCredentials) {
            error(message: "ETP_SSH_KEY_CREDENTIALS_ID environment variable not set in Jenkins Environment")
        }
        if (!service) {
            error(message: "ETP_SERVICE environment variable not set in Jenkins Environment")
        }
        if (!account) {
            error(message: "ETP_ACCOUNT environment variable not set in Jenkins Environment")
        }
        if (!region) {
            region = "us-west-2"
            echo("Warning: ETP_REGION environment variable not set in Jenkins Environment. Defaulting to us-west-2")
        }
        if (!environment) {
            error(message: "ETP_ENVIRONMENT environment variable not set in Jenkins Environment")
        }
        dir("packer") {
            gitWrapper.checkoutRepository(branch: "master", credentialsId: gitCredentials, url: "git@github.com:crunchyroll/packer.git")
        }
        dir("ellation-infrastructure-live") {
            gitWrapper.checkoutRepository(branch: "${deployBranch}",
                credentialsId: gitCredentials,
                url: "git@github.com:crunchyroll/ellation-infrastructure-live.git")
            // Parse the service_registry.yaml for the main service
            mainServiceEntry = serviceRegistry.parseServiceRegistryYaml("service_registry.yaml", service)
            for (String subService in mainServiceEntry.getSubServices()) {
                ServiceRegistryEntry subServiceEntry = serviceRegistry.parseServiceRegistryYaml("service_registry.yaml",
                        subService)
                subServiceEntries.add(subServiceEntry)
            }
            // Service has no repos to build from
            if (mainServiceEntry.getRepository() == "") {
                buildPhaseExists = false
            }
        }
    }

    String team = mainServiceEntry.getTeam()
    Map<String, ServiceHistoryEntry> serviceHistoryEntries = [:]
    stage("Build") {
        if(buildPhaseExists) {
            serviceHistoryEntries = buildService.build(buildBranch, gitCredentials, account, mainServiceEntry,
                    subServiceEntries)
        } else {
            ServiceRegistryEntry serviceHistoryEntry = serviceHistory.getEntry(team, account, "${service}/foundation")
            serviceHistoryEntries[service] = serviceHistoryEntry
            for(String subService in mainServiceEntry.getSubServices()) {
                ServiceRegistryEntry subServiceHistoryEntry = serviceHistory.getEntry(team, account,
                        "${subService}/foundation")
                serviceHistoryEntries[subService] = subServiceHistoryEntry
            }
        }
    }

    stage("Set AMI ID in SSM") {
        String pipelineNumber = env.BUILD_NUMBER
        serviceHistoryEntries.each { serviceName, serviceHistoryEntry ->
            String amiId = serviceHistoryEntry.getAmiId()
            serviceHistory.setEntry(team, account, "${serviceName}/build", amiId, pipelineNumber,
                    serviceHistoryEntry.getRepositoryCommitHash())
        }
    }

    stage("Deploy") {
        // Need discussion on how to deploy a service and subservices with terragrunt
//        serviceHistoryEntries.each { serviceName, serviceHistoryEntry ->
//            String amiId = serviceHistoryEntry.getAmiId()
//            dir("ellation-infrastructure-live/${team}/${account}/${region}/${environment}/${serviceName}") {
//                sh("terragrunt -var 'ami=${amiId}' apply")
//            }
//        }
    }

    stage("Post Deploy Test") {
        // read service registry, run tests
    }

    stage("Notifications") {

    }

    stage("Artifact Information") {

    }
}
