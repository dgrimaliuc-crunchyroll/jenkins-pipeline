import com.ellation.history.ServiceHistoryEntry
import groovy.json.JsonSlurper

/**
 * Create a new entry in the service's history. Use this version if your pipeline has checked out repos and you can
 * provide a list of paths to those folders. This function automates having to create the repository name to git commit
 * hash mapping for you.
 * @param team
 * @param account AWS account
 * @param serviceName
 * @param amiId AMI ID of an image in AWS of this service
 * @param pipelineNumber jenkins pipeline number that made the AMI
 * @param pathsToRepos List of strings. Each string is a path to a folder where a repo was checked out to.
 * @param region which AWS region to store this information in
 */
void setEntry(String team, String account, String serviceName, String amiId, String pipelineNumber,
              List<String> pathsToRepos, String region="us-west-2") {
    ServiceHistoryEntry entry = new ServiceHistoryEntry(serviceName, amiId, pipelineNumber)
    for(String path in pathsToRepos) {
        dir(path) {
            String getRepoNameScript = "basename -s .git `git config --get remote.origin.url`"
            String repoName = sh(returnStdout: true, script:getRepoNameScript)
            String getCommitHashScript = "git rev-parse HEAD"
            String commitHash = sh(returnStdout: true, script:getCommitHashScript)
            entry.setRepositoryCommitHash(repoName.trim(), commitHash.trim())
        }
    }
    setEntryInSSM(team, account, serviceName, entry, region)
}

/**
 * Create a new entry in this service's history. Use this function if you want to manually create the map of
 * repo names to git commit hashes.
 * @param team
 * @param account AWS account
 * @param serviceName
 * @param amiId AMI ID of an image in AWS of this service
 * @param pipelineNumber jenkins pipeline number that made the AMI
 * @param repositoryCommitHash Map of the repository name as key and the git commit hash as value
 * @param region
 */
void setEntry(String team, String account, String serviceName, String amiId, String pipelineNumber,
              Map<String, String> repositoryCommitHash, String region="us-west-2") {
    ServiceHistoryEntry entry = new ServiceHistoryEntry(serviceName, amiId, pipelineNumber)
    entry.setRepositoryCommitHash(repositoryCommitHash)
    setEntryInSSM(team, account, serviceName, entry, region)
}

/**
 * Sets the ServiceHistoryEntry string version into AWS SSM parameter store
 * @param team
 * @param account AWS account
 * @param serviceName
 * @param entry ServiceHistoryEntry object with all the details
 * @param region
 */
private void setEntryInSSM(String team, String account, String serviceName, ServiceHistoryEntry entry, String region) {
    String value = entry.toSSMString()
    String setEntryScript = "aws ssm put-parameter --name '/image-id-versions/${team}/${account}/${serviceName}' " +
            "--value '${value}' --type StringList --overwrite --region ${region}"
    sh(script: setEntryScript)
}

/**
 * Retrieves entry from service's history. Acts as a wrapper to hide the details from caller that this is coming
 * from SSM just to keep calls consistent.
 * @param team
 * @param account AWS account
 * @param serviceName
 * @param version what version of the service's history to get, don't specify the number if you want the latest
 * @param region which AWS region is the service history stored in AWS SSM
 * @return ServiceHistoryEntry that encapsulates all the information
 */
ServiceHistoryEntry getEntry(String team, String account, String serviceName, String version = "", String region="us-west-2") {
    return getEntryFromSSM(team, account, serviceName, version, region)
}

/**
 * Retrieve an entry of the service's history
 * @param team
 * @param account AWS account
 * @param serviceName
 * @param version what version of the service's history to get, don't specify the number if you want the latest
 * @param region which AWS region is the service history stored in AWS SSM
 * @return ServiceHistoryEntry that encapsulates all the information
 */
private ServiceHistoryEntry getEntryFromSSM(String team, String account, String serviceName, String version = "", String region="us-west-2") {
    // Grab the parameter, version may be specified or not
    String getEntryScript = "aws ssm get-parameter " +
            "--name '/image-id-versions/${team}/${account}/${serviceName}"
    // If the version is specified, need to append a colon with version
    if (version) {
        getEntryScript += ":${version}"
    }
    getEntryScript += "' --region ${region}"

    // Parse the response, and grab the value which has all the information of the service AMI at a point in time
    String response = sh(returnStdout: true, script: getEntryScript)
    JsonSlurper slurper = new JsonSlurper()
    def json = slurper.parseText(response)
    String value = json["Parameter"]["Value"]
    value = value.trim()
    int versionNumber = json["Parameter"]["Version"]

    // Convert the String of the service history information into this class object
    ServiceHistoryEntry entry = ServiceHistoryEntry.parseSSMString(serviceName, value)
    entry.setVersion(versionNumber)
    return entry
}
