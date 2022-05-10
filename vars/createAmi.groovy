import com.ellation.history.ServiceHistoryEntry
import com.ellation.registry.ServiceRegistryEntry
import com.ellation.web.Config

/**
 * Call this when creating a base ami. Relies on environment variables set in Jenkins.
 * @param template path to the packer template file
 * @return the ami id generated
 */
String createBaseAmi(String template) {
    String parameters = "-var chef_role=${env.PACKER_BASE_CHEF_ROLE}"
    return createAmi(template, parameters)
}

/**
 * Call this when creating a service's foundation ami
 * @param template path to the packer template file
 * @param serviceName
 * @param chefRole
 * @param cookbookPaths
 * @param rolesPath
 * @return the ami id generated
 */
String createFoundationAmi(String template, String serviceName, String chefRole, String cookbookPaths,
                           String rolesPath) {
    // All service ami's build off of the base ami
    ServiceHistoryEntry serviceHistoryEntry = serviceHistory.getEntry("ops", "el-mgmt-global", "base")
    String baseAmiId = serviceHistoryEntry.getAmiId()
    // Replace all periods . in the service name with dash -
    String serviceNameForPacker = serviceName.replaceAll("\\.", "-")
    String parameters = "-var source_ami=${baseAmiId} -var chef_role=${chefRole} " +
            "-var service_name=${serviceNameForPacker} -var ami_name=${serviceName}/foundation " +
            "-var cookbook_paths=${cookbookPaths} -var roles_path=${rolesPath}"
    return createAmi(template, parameters)
}

/**
 * Call this when creating a service's build ami
 * @param template path to the packer template file
 * @param account AWS account
 * @param serviceRegistryEntry
 * @param packerReleasePath where the service's release .jar files are located
 * @return the ami id generated
 */
String createBuildAmi(String template, String account, ServiceRegistryEntry serviceRegistryEntry,
                      String packerReleasePath) {
    // The main service (parent service) and subservices should be using the same foundation AMI
    String serviceName = serviceRegistryEntry.getService()
    String team = serviceRegistryEntry.getTeam()
    ServiceHistoryEntry serviceHistoryEntry = serviceHistory.getEntry(team, account, "${serviceName}/foundation")
    String foundationAmiId = serviceHistoryEntry.getAmiId()
    String parameters = "-var service_name=${serviceName} -var source_ami=${foundationAmiId} " +
            "-var ami_name=${serviceName}/build -var packer_release_path=${packerReleasePath}"
    return createAmi(template, parameters)
}

/**
 * This is a custom method made in order to support ETPv1 environment because in order to create the build AMI,
 * it needs to obtain the service's ami-id to build off of through curl.
 * @param serviceRegistryEntry
 * @param templatePath path to the packer template file
 * @param packerReleasePath where the service's release .jar files are located
 * @param config Config class of all the environment variable settings passed in from the job
 * @return the ami id generated
 */
String createBuildAmiInOldJenkinsBuild(ServiceRegistryEntry serviceRegistryEntry, String templatePath, String packerReleasePath,
                                       ServiceHistoryEntry serviceHistoryEntry, Config config) {
    // There is a nuance we discovered with services and subservices. Some services have subservices that have their
    // own ami jobs
    // Some subservices use their parent service ami. There is no clear distinction in the current
    // service_registry.json except for the custom Jenkins URL in the build job that grabs the latest successful
    // ami job. A new field needs to be added in the service_registry.json and .yaml that makes that distinction so
    // the pipeline code knows what to do when building the service's build ami.
    String service = serviceRegistryEntry.getService()
    String parentServiceAmi = serviceRegistryEntry.getParentServiceAmi()
    String serviceAmiName = "${service}-ami"
    if (parentServiceAmi) {
        serviceAmiName = "${parentServiceAmi}-ami"
    }

    // Based on the image being used, specify which ssh user the packer template should use
    if (config.baseImageOS == "amazon-linux-2") {
        config.setSshUser("ec2-user")
    } else {
        config.setSshUser("centos")
    }

    // Had to rely on curl instead of making an httpRequest because when trying to echo the response body
    // into a pipe for grep using sh(), Jenkins bash kept thinking the next string line was a command
    // Don't add a / after the env.JENKINS_URL, the environment variable has a trailing slash on it already
    String script = "curl --silent ${env.JENKINS_URL}job/${serviceAmiName}/lastSuccessfulBuild/consoleText | " +
            "tail -n 15 | grep -m 1 -o -E 'ami-[A-Za-z0-9]+'"
    String foundationAmiId = sh(script: script, returnStdout: true).trim()
    // Due to the nature of parsing folder names being difficult if it has periods in them, replace all periods with
    // dashes, even if it's a subservice
    String service_dir = service.replaceAll("\\.", "-")
    String buildBranch = config.branch
    // Because there's no service that has more than one repo using the new pipeline can safely assume
    // the String returned is just one repoName=gitCommitHash
    String[] result = serviceHistoryEntry.toRepositoryCommitHashString().split('=')
    String commitHash = result[1]
    String dev = "True"
    if (["prod", "staging"].contains(config.environment)) {
        dev = "False"
    }
    String parameters = "-var service_name=${service} -var service_dir_name=${service_dir} " +
            "-var source_ami=${foundationAmiId} -var subnet_id=${env.PACKER_SUBNET_ID} " +
            "-var vpc_id=${env.PACKER_VPC_ID} -var region=${env.PACKER_AWS_REGION} -var ami_name=${service}/build " +
            "-var packer_release_path=${packerReleasePath} -var git_branch=${buildBranch} " +
            "-var git_commit=${commitHash} -var dev=${dev} -var ssh_user=${config.getSshUser()}"
    return createAmi(templatePath, parameters)
}

/**
 * Actual function that calls the packer binary and obtains the ami id.
 * @param template path to the packer template file
 * @param parameters extra parameters passed to packer for user variables or options
 * @return the ami id generated, or an error if packer fails
 */
private String createAmi(String templatePath, String parameters="") {
    String region = env.PACKER_AWS_REGION
    String packerBuildScript = "packer build ${parameters} -var region=${region} ${templatePath} | tee packer_result.txt"
    sh(script: packerBuildScript)

    // Need to have the || true, otherwise the return code is non zero and Jenkins thinks something went wrong,
    // even though we set the returnStdout to true, a return code status is still checked
    String checkForErrorsScript = "tail -n 15 packer_result.txt | grep -m 1 -o -E 'Builds finished but no artifacts were created' || true"
    String errorMessage = sh(returnStdout: true, script: checkForErrorsScript)
    if (errorMessage.trim()) {
        error("Something went wrong with packer.")
    }

    // Grab the last 3 lines, look for the line that states the region, get rid of trailing and leading whitespace, and
    // grab the portion that is the ami-id
    /** Example input:
     ==> Builds finished. The artifacts of successful builds are:
     --> amazon-ebs: AMIs were created:
     us-west-2: ami-0f45e03de1bdc2480
     */
    // Need to have the || true, otherwise the return code is non zero and Jenkins thinks something went wrong,
    // even though we set the returnStdout to true, a return code status is still checked
    String getAmiIdScript = "tail -n 15 packer_result.txt | grep -m 1 -o -E 'ami-[A-Za-z0-9]+' || true"
    String amiId = sh(returnStdout: true, script: getAmiIdScript)
    amiId = amiId.trim()
    if(!amiId) {
        error("No AMI ID generated from packer.")
    }

    // Cleanup
    String deletePackerResult = "rm -f packer_result.txt"
    sh(script: deletePackerResult)

    return amiId
}
