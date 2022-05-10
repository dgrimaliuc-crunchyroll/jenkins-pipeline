@Library('ellation') _

node("universal") {
    stage("Setup") {
        String gitCredentials = env.ETP_SSH_KEY_CREDENTIALS_ID?.trim()
        if (!gitCredentials) {
            error(message: "ETP_SSH_KEY_CREDENTIALS_ID environment variable not set in Jenkins Environment")
        }
        // Checkout all the git repos
        dir("chef") {
            gitWrapper.checkoutRepository(branch: "master", credentialsId: gitCredentials,
                    url: "git@github.com:crunchyroll/ellation_formation.git")
        }
        dir("packer") {
            gitWrapper.checkoutRepository(branch: "master", credentialsId: gitCredentials,
                    url: "git@github.com:crunchyroll/packer.git")
        }

        // Berks vendor to grab all the chef libraries
        dir("chef") {
            sh("berks vendor ./vendor/cookbooks --berksfile ./chef/library/Berksfile")
        }
    }
    String amiId
    stage("Create base AMI image with Packer") {
        dir("chef") {
            // Template path is based on the packer REPO. If the path changes, you'll need to update the code here.
            String baseTemplate = "${WORKSPACE}/packer/ops/el-mgmt-global/jenkins/packer_base_ami_template.json"
            amiId = createAmi.createBaseAmi(baseTemplate)
        }
    }
    stage("Set service history entry") {
        String pipelineNumber = env.BUILD_NUMBER
        serviceHistory.setEntry("ops", "el-mgmt-global", "base", amiId, pipelineNumber, ["chef"])
    }
}
