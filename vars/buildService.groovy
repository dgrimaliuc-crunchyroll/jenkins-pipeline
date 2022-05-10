import com.ellation.git.GitRepoUrl
import com.ellation.history.ServiceHistoryEntry
import com.ellation.registry.ServiceRegistryEntry
import com.ellation.web.Config

/**
 * Builds the service's and subservices' build ami images for the old Jenkins Build machine
 * @param buildBranch
 * @param entry ServiceRegistryEntry of the main service
 * @param subServiceEntries List of ServiceRegistryEntries representing the subservices
 * @return Map of service's and subservices' service history entries created from the build process
 * NOTE: This function to be deprecated due to bad parameters being used
 */
Map<String, ServiceHistoryEntry> buildForOldJenkins(Config config, String gitCredentials, ServiceRegistryEntry entry,
                                                    List<ServiceRegistryEntry> subServiceEntries) {
    Map<String, String> serviceDockerImageTags = buildDockerImages(config.branch, gitCredentials, entry, subServiceEntries)
    Map<String, ServiceHistoryEntry> serviceHistoryEntries = buildAmis(config.branch, gitCredentials, entry, subServiceEntries,
            serviceDockerImageTags, "", true, config)
    return serviceHistoryEntries
}

/**
 * Builds the service's and subservices' build ami images
 * @param buildBranch
 * @param gitCredentials should be the SSH credentials key
 * @param entry ServiceRegistryEntry of the main service
 * @param subServiceEntries List of ServiceRegistryEntries representing the subservices
 * @return Map of service's and subservices' service history entries created from the build process
 */
Map<String, ServiceHistoryEntry> build(String buildBranch, String gitCredentials, String account,
                                       ServiceRegistryEntry entry, List<ServiceRegistryEntry> subServiceEntries) {
    Map<String, String> serviceDockerImageTags = buildDockerImages(buildBranch, gitCredentials, entry, subServiceEntries)
    Map<String, ServiceHistoryEntry> serviceHistoryEntries = buildAmis(buildBranch, gitCredentials, entry,
            subServiceEntries, serviceDockerImageTags, account)
    return serviceHistoryEntries
}

/**
 * Build all the service's and subservices' docker images for the build environment
 * @param buildBranch
 * @param gitCredentials
 * @param serviceRegistryEntries Map of service registry information for the service and subservices
 * @return Map of service's and subservices' docker image tag created for the build process
 */
private Map<String, String>  buildDockerImages(String buildBranch, String gitCredentials, ServiceRegistryEntry entry,
                                               List<ServiceRegistryEntry> subServiceEntries) {
    Map<String, Closure> buildDockerImageJobs = [:]
    Map<String, String> serviceDockerImageTags = [:]
    List<ServiceRegistryEntry> serviceEntries = []
    serviceEntries.add(entry)
    serviceEntries.addAll(subServiceEntries)
    serviceEntries.each { serviceEntry ->
        String serviceNameLoop = serviceEntry.getService()
        // All subservices should be using the same repository as the main service, otherwise the definition has changed
        // and we'll need a new implementation of our build step
        GitRepoUrl gitRepoLoop = new GitRepoUrl(entry.getRepository())
        buildDockerImageJobs[serviceNameLoop] = {
            // Need to create separate folders for parallel jobs to run buildDockerImage, otherwise they all
            // will use the same directory for git checkout
            dir("docker-workspace-${serviceNameLoop}") {
                serviceDockerImageTags[serviceNameLoop] = buildDockerImageJob(buildBranch, serviceNameLoop,
                        gitCredentials, gitRepoLoop.getSshUrl())

            }
        }
    }
    parallel(buildDockerImageJobs)
    return serviceDockerImageTags
}

/**
 * Build the docker image for the service
 * @param buildBranch
 * @param service
 * @param gitCredentials
 * @param repositoryUrl
 * @return Tag of docker image created
 */
private String buildDockerImageJob(String buildBranch, String service, String gitCredentials, String repositoryUrl) {
    String tag = "build-${service}"
    gitWrapper.checkoutRepository(branch: buildBranch, credentialsId: gitCredentials, url: repositoryUrl)
    // Because some repos need to grab other repos within the organization, grab the RSA private key into this directory
    // so Docker can add it to the image
    // Need to use SSH SECRET TEXT to supply the credentials for git. Trying to manipulate the string
    // and pass it doesn't work and exposes the private key in text in the console log.
    withCredentials([string(credentialsId: "${env.ETP_SSH_SECRET_TEXT}", variable: 'secretText')]) {
        sh(returnStdout: true, script: "docker build --rm --build-arg SSH_KEY=\"${secretText}\" --tag=${tag} " +
                "--quiet --file=./jenkins.Dockerfile .")
    }
    return tag
}

/**
 * Build all the amis for the services and subservices
 * @param buildBranch
 * @param gitCredentials
 * @param entry
 * @param serviceDockerImageTags
 * @return Map of the service and its corresponding ServiceHistoryEntry
 */
private Map<String, ServiceHistoryEntry> buildAmis(String buildBranch, String gitCredentials,
                                                   ServiceRegistryEntry entry,
                                                   List<ServiceRegistryEntry> subServiceEntries,
                                                   Map<String, String> serviceDockerImageTags,
                                                   String account = "", boolean isETPv1 = false,
                                                   Config config = null) {
    Map<String, Closure> buildJobs = [:]
    Map<String, ServiceHistoryEntry> serviceHistoryEntries = [:]
    List<ServiceHistoryEntry> serviceEntries = []
    serviceEntries.add(entry)
    serviceEntries.addAll(subServiceEntries)
    serviceEntries.each { serviceEntry ->
        String serviceNameLoop = serviceEntry.getService()
        buildJobs[serviceNameLoop] = {
            ServiceRegistryEntry serviceRegistryEntryLoop = serviceEntry
            String tagLoop = serviceDockerImageTags[serviceNameLoop]
            dir("workspace-${serviceNameLoop}") {
                String releasePath = pwd() + "/release"
                serviceHistoryEntries[serviceNameLoop] = buildBinary(buildBranch, gitCredentials, serviceNameLoop,
                        entry, tagLoop)
                String amiId
                dir("packer-buildAmi") {
                    if (isETPv1) {
                        amiId = buildAmiInOldJenkinsBuild(gitCredentials, serviceRegistryEntryLoop, releasePath,
                                serviceHistoryEntries[serviceNameLoop], config)
                    } else {
                        amiId = buildAmi(gitCredentials, account, serviceRegistryEntryLoop, releasePath)
                    }
                }
                serviceHistoryEntries[serviceNameLoop].setAmiId(amiId)
            }
        }
    }
    parallel(buildJobs)
    return serviceHistoryEntries
}

/**
 * Build the service's binary from all the repositories and wraps it up into release.tar.gz file in this directory
 * @param buildBranch
 * @param gitCredentials
 * @param service name of the service being built (could be the subservice)
 * @param serviceRegistryEntry contains information about the main service
 * @param serviceDockerImageTag
 * @return ServiceHistoryEntry representing a service whose binaries were built
 */
private ServiceHistoryEntry buildBinary(String buildBranch, String gitCredentials, String service,
                                        ServiceRegistryEntry serviceRegistryEntry, String serviceDockerImageTag) {
    // Git checkout the main repository and other repositories in their own folders at the root level of this service's
    // workspace
    GitRepoUrl gitRepo = new GitRepoUrl(serviceRegistryEntry.getRepository())
    ServiceHistoryEntry serviceHistoryEntry = new ServiceHistoryEntry(serviceRegistryEntry.service, env.BUILD_NUMBER)
    gitWrapper.checkoutRepository(branch: buildBranch, credentialsId: gitCredentials, url: gitRepo.getSshUrl())
    addRepositoryCommitHash(serviceHistoryEntry)
    // Can ignore this section of code cause it doesn't work, we never finished the ability to handle a service
    // that uses more than one github repo to build the binary
    serviceRegistryEntry.getOtherRepositories().each { repositoryUrl ->
        GitRepoUrl otherGitRepo = new GitRepoUrl(repositoryUrl)
        dir("./${otherGitRepo.getRepoName()}") {
            gitWrapper.checkoutRepository(branch: buildBranch, credentialsId: gitCredentials, url: otherGitRepo.getSshUrl())
            addRepositoryCommitHash(serviceHistoryEntry)
        }
    }
    // End ignore

    sh(returnStdout: true, script: "mkdir -p release")
    String releaseDirectory = pwd() + "/release"
    String makeTarget = "package-${service}"
    String script = "docker run --rm " +
            "--volume=${releaseDirectory}:/release ${serviceDockerImageTag} ${makeTarget}"
    sh(returnStdout: true, script: script)
    return serviceHistoryEntry
}

/**
 * Call this function in a Git repo, and it will create an entry in the ServiceHistoryEntry mapping the repo name
 * to the commit hash
 * @param entry ServiceHistoryEntry that should store the commmit hashes of the repo
 */
private void addRepositoryCommitHash(ServiceHistoryEntry entry) {
    String repoName = getRepoName()
    String commitHash = getCommitHash()
    entry.setRepositoryCommitHash(repoName, commitHash)
}

/**
 * Call this function in a Git repo and it will return the repo name
 * @return String repo name
 */
private String getRepoName() {
    String getRepoNameScript = "basename -s .git `git config --get remote.origin.url`"
    String repoName = sh(returnStdout: true, script:getRepoNameScript)
    // Get rid of hidden newlines
    return repoName.trim()
}

/**
 * Call this function in a Git repo and it will return the commit hash
 * @return String commit hash
 */
private String getCommitHash() {
    String getCommitHashScript = "git rev-parse HEAD"
    String commitHash = sh(returnStdout: true, script:getCommitHashScript)
    // Get rid of hidden newlines
    return commitHash.trim()
}

/**
 * Build the service AMI with packer. This method should be called inside the release directory of the service
 * so that when creating the AMI of the service, it can grab the path of the release.tar.gz file to the createAmi
 * Jenkins step.
 * NOTE: This is for old jenkins
 * @param gitCredentials
 * @param entry represents this service's entry in the service registry
 * @param releasePath path to the release folder that packer will use
 * @return Service's AMI ID
 */
private String buildAmiInOldJenkinsBuild(String gitCredentials, ServiceRegistryEntry serviceRegistryEntry, String releasePath,
                                         ServiceHistoryEntry serviceHistoryEntry, Config config) {
    gitWrapper.checkoutRepository(branch: "master", credentialsId: gitCredentials, url: "git@github.com:crunchyroll/packer.git")
    // Append / at the end of the path to tell packer we want to copy all the contents inside
    // the folder not include the folder itself
    String packerTemplate
    println("Using OS: ${config.baseImageOS}")
    if (config.baseImageOS == "amazon-linux-2") {
        packerTemplate = "ops/ellationeng/jenkins/packer_amzn_build_ami_pipeline_template.json"
    } else {
        packerTemplate = "ops/ellationeng/jenkins/packer_build_ami_template.json"
    }

    String amiId = createAmi.createBuildAmiInOldJenkinsBuild(serviceRegistryEntry, packerTemplate,
            releasePath + "/", serviceHistoryEntry, config)
    return amiId
}

/**
 * Build the service AMI with packer. This method should be called inside the release directory of the service
 * so that when creating the AMI of the service, it can grab the path of the release.tar.gz file to the createAmi
 * Jenkins step.
 * @param gitCredentials
 * @param account this service is to be deployed in
 * @param entry represents this service's entry in the service registry
 * @param releasePath path to the release folder that packer will use
 * @return
 */
private String buildAmi(String gitCredentials, String account, ServiceRegistryEntry entry, String releasePath) {
    gitWrapper.checkoutRepository(branch: "master", credentialsId: gitCredentials, url: "git@github.com:crunchyroll/packer.git")
    // Append / at the end of the path to tell packer we want to copy all the contents inside
    // the folder not include the folder itself
    String amiId = createAmi.createBuildAmi("ops/el-mgmt-global/jenkins/packer_build_ami_template.json", account, entry,
            releasePath + "/")
    return amiId
}
