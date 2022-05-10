@Library('ellation') _

node("universal") {
    String service = env.ETP_SERVICE?.trim()
    String account = env.ETP_ACCOUNT?.trim()
    def serviceRegistryEntry
    stage("Setup") {
        String gitCredentials = env.ETP_SSH_KEY_CREDENTIALS_ID?.trim()
        if (!gitCredentials) {
            error(message: "ETP_SSH_KEY_CREDENTIALS_ID environment variable not set in Jenkins Environment")
        }
        if (!service) {
            error(message: "ETP_SERVICE environment variable not set in Jenkins Environment")
        }
        if (!account) {
            error(message: "ETP_ACCOUNT environment variable not set in Jenkins Environment")
        }
        // Checkout all the git repos
        dir("chef") {
            gitWrapper.checkoutRepository(branch: "master",
                credentialsId: gitCredentials,
                url: "git@github.com:crunchyroll/ellation_formation.git")
        }
        dir("packer") {
            gitWrapper.checkoutRepository(branch: "master", credentialsId: gitCredentials,
                    url: "git@github.com:crunchyroll/packer.git")
        }

        dir("service_registry") {
            gitWrapper.checkoutRepository(branch: "master", credentialsId: gitCredentials,
                    url: "git@github.com:crunchyroll/ellation-infrastructure-live.git")
            serviceRegistryEntry = serviceRegistry.parseServiceRegistryYaml("service_registry.yaml", service)
            if(!serviceRegistryEntry.getChefRole()) {
                error("Service ${service} in service_registry.yaml has no chef role defined.")
            }
        }

        // Berks vendor to grab all the chef libraries
        dir("chef") {
            sh("berks vendor ./vendor/cookbooks --berksfile ./chef/library/Berksfile")
        }
    }
    String amiId
    stage("Create foundational AMI") {
        // cd into the chef directory in order to help setup packer
        dir("chef") {
            String serviceTemplate = "${WORKSPACE}/packer/ops/el-mgmt-global/jenkins/packer_foundation_ami_template.json"
            String chefRole = serviceRegistryEntry.getChefRole()
            String cookbooksDirectory = pwd() + "/vendor/cookbooks"
            String rolesDirectory = pwd() + "/chef/roles"
            amiId = createAmi.createFoundationAmi(serviceTemplate, service, chefRole, cookbooksDirectory,
                    rolesDirectory)
        }
    }
    stage("Set service history entry") {
        String pipelineNumber = env.BUILD_NUMBER
        String team = serviceRegistryEntry.getTeam()
        serviceHistory.setEntry(team, account, "${service}/foundation", amiId, pipelineNumber, ["chef"])
    }
}
