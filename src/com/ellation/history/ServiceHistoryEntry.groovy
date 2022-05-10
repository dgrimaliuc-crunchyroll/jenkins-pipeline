package com.ellation.history

/**
 * Stores one entry from a service's history. Fields of this class follow the format of the data stored in AWS SSM
 * https://wiki.tenkasu.net/pages/viewpage.action?pageId=546701472
 */
class ServiceHistoryEntry implements Serializable {
    /**
     * Property representing the name of the service
     */
    final String service
    /**
     * Property representing the ami id of the service at a point of time in history
     */
    String amiId
    /**
     * Property representing the pipeline number this service was run in
     */
    final String pipelineRunNumber
    /**
     * Property representing the environment of this entry
     */
    String environment
    /**
     * Version number of this service history entry. In SSM, it's the version number field.
     */
    private String version
    /**
     * Stores the repositories that are used to make this build.
     * NOTE: This field is probably going to be deprecated or re-purposed to just store one repo name and its
     * corresponding repository url
     */
    private Map<String, String> repositoryCommitHash

    /**
     * Constructor used for when you want to create a new entry in the service's history and you have some of the
     * information
     * @param service
     * @param pipelineRunNumber
     */
    ServiceHistoryEntry(String service, String pipelineRunNumber) {
        this(service, "", pipelineRunNumber)
    }

    /**
     * Constructor used for when you want to create a new entry in the service's history and you have the information
     * to put together (TO BE DEPRECATED)
     * @param service
     * @param amiId
     * @param pipelineRunNumber
     */
    ServiceHistoryEntry(String service, String amiId, String pipelineRunNumber) {
        this.service = service
        this.amiId = amiId
        this.pipelineRunNumber = pipelineRunNumber
        this.version = "0"
        this.repositoryCommitHash = [:]
    }

    /**
     * Set the version of the entry in AWS SSM
     * @param version
     */
    void setVersion(int version) {
        this.version = version.toString()
    }

    /**
     * Get the version of the entry in AWS SSM
     * @return
     */
    int getVersion() {
        return Integer.parseInt(version)
    }

    /**
     * Grab the map of repo names to commit hashes
     * @return
     */
    Map<String, String> getRepositoryCommitHash() {
        return repositoryCommitHash
    }

    /**
     * Set the repo name and the commit hash built from that repo, will replace an existing entry or add a new one
     * @param repository shortname of the repo
     * @param commitHash commit hash from the repo used to built this ami
     */
    void setRepositoryCommitHash(String repository, String commitHash) {
        repositoryCommitHash[repository] = commitHash
    }

    /**
     * Set all the repo name and commit hashes, replaces all the existing contents with the Map given
     * @param repositoryCommitHash Existing map of repo name to commit hashes
     */
    void setRepositoryCommitHash(Map<String, String> repositoryCommitHash) {
        this.repositoryCommitHash = repositoryCommitHash
    }

    /**
     * Return a string representing all the repos
     * @return String
     */
    String toRepositoryCommitHashString() {
        String result = repositoryCommitHash.inject([]) { tempList, repoName, commitHash ->
            tempList << "${repoName}=${commitHash}"
        }.join(',')
        return result
    }

    /**
     * Requires that the AMI ID and pipeline run number have valid values, with optional repo short names to
     * commit hashes be set in order to produce a valid StringList entry to be stored in AWS SSM
     * NOTE: StringList denotes that all the "values" be comma separated for AWS SSM
     * @return
     */
    String toSSMString() {
        String result = "ami-id=${amiId},pipeline-number=${pipelineRunNumber}"
        // Cannot implement a closure and pass it to sort, Jenkins does something funky and makes the sort function
        // return back an Integer. Use the default sort since it sorts based on keys which is what we want
        Map<String, String> sortedRepositoryCommitHash = repositoryCommitHash.sort()
        sortedRepositoryCommitHash.each { repoName, commitHash ->
            result += ",${repoName}=${commitHash}"
        }
        return result
    }

    /**
     * Class method to create a new ServiceHistoryEntry given the service name and String value from AWS SSM
     * @param service
     * @param entryString Must be a StringList (comma separated strings). No guarantee all the fields will be there,
     * especially if the entryString is malformed.
     * @return ServiceHistoryEntry object with no guarantee that all the fields have been filled out
     */
    static ServiceHistoryEntry parseSSMString(String service, String entryString) {
        String amiId = ""
        String pipelineRunNumber = ""
        Map<String, String> repositoryCommitHash = [:]
        List<String> values = entryString.split(",")
        for (String currentValue in values) {
            // Relying on a string array instead of a tuple () because our Jenkins Build is running on an old version
            // that doesn't support the return of tuple results
            String[] result = currentValue.split("=")
            String key = result[0]
            String value = result[1]
            if (key.equalsIgnoreCase("ami-id")) {
                amiId = value
            } else if (key.equalsIgnoreCase("pipeline-number")) {
                pipelineRunNumber = value
            } else {
                repositoryCommitHash[key] = value
            }
        }
        ServiceHistoryEntry entry = new ServiceHistoryEntry(service, amiId, pipelineRunNumber)
        entry.setRepositoryCommitHash(repositoryCommitHash)
        return entry
    }
}
