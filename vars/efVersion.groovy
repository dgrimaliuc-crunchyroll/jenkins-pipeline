import com.ellation.ef.ServiceHistory
/**
 * Rolls back an ami service for an environment to the previous stable version.
 * @param service
 * @param environment
 * @return String output of the command
 */
String rollbackAmiId(String service, String environment) {
    String arguments = "${service} ami-id ${environment} --noprecheck --rollback --commit"
    return efVersionCommand(arguments)
}

/**
 * Gets the AMI ID of a service for an environment
 * @param service
 * @param environment
 * @return the ami-id as a String
 */
String getAmiId(String service, String environment) {
    String arguments = "${service} ami-id ${environment} --get"
    return efVersionCommand(arguments)
}

/**
 * Set the ami id for a service
 * @param service
 * @param environment
 * @param targetAmiId
 * @return String output of the command
 */
String setAmiId(String service, String environment, String targetAmiId, String commitHashes = "",
                boolean noPreCheck = true, boolean stable = true) {
    String arguments = "${service} ami-id ${environment} --set ${targetAmiId} --build ${env.BUILD_NUMBER} --commit"
    if(commitHashes) {
        arguments += " --commit_hash \"${commitHashes}\""
    }
    if (noPreCheck) {
        arguments += " --noprecheck"
    }
    if (stable) {
        arguments += " --stable"
    }
    return efVersionCommand(arguments)
}

/**
 * Set the commit-hash for a service
 * @param service
 * @param environment
 * @param commitHash
 * @return String output of the command
 */
String setCommitHash(String service, String environment, String commitHash, boolean stable = true) {
    String arguments = "${service} commit-hash ${environment} --set ${commitHash} --build ${env.BUILD_NUMBER} --commit"
    if (stable) {
        arguments += " --stable"
    }

    // Always noprecheck
    arguments += " --noprecheck"

    return efVersionCommand(arguments)
}

/**
 * Get the commit-hash for a service
 * @param service
 * @param environment
 * @return String output of the command
 */
String getCommitHash(String service, String environment) {
    return get(service, environment, "commit-hash")
}

/**
 * Rolls back a commit-hash service for an environment to the previous stable version.
 * @param service
 * @param environment
 * @return String output of the command
 */
String rollbackCommitHash(String service, String environment) {
    return rollback(service, environment, "commit-hash")
}

/**
 * Set the version-number for a service
 * @param service
 * @param environment
 * @param version-number
 * @return String output of the command
 */
String setVersionNumber(String service, String environment, String versionNumber, String commitHash, boolean stable = true) {
    String arguments = "${service} version-number ${environment} --set ${versionNumber}  --commit_hash ${commitHash} " +
            "--build ${env.BUILD_NUMBER} --commit"
    if (stable) {
        arguments += " --stable"
    }

    // Always noprecheck
    arguments += " --noprecheck"

    return efVersionCommand(arguments)
}

/**
 * Rolls back a commit-hash service for an environment to the previous stable version.
 * @param service
 * @param environment
 * @return String output of the command
 */
String rollbackVersionNumber(String service, String environment) {
    return rollback(service, environment, "version-number")
}

/**
 * Get a key for a service
 * @param service
 * @param environment
 * @return String the key value for the services
 */
String get(String service, String environment, String keyType) {
    String arguments = "${service} ${keyType} ${environment} --get"
    return efVersionCommand(arguments)
}

/**
 * Rolls back keyType for an environment to the previous stable version.
 * @param service
 * @param environment
 * @param keyType
 * @return String output of the command
 */
String rollback(String service, String environment, String keyType) {
    String arguments = "${service} ${keyType} ${environment} --noprecheck --rollback --commit"
    return efVersionCommand(arguments)
}

/**
 * Return the history text of a service
 * @param service
 * @param environment
 * @param keyType ami-id, dist-hash, or something else
 * @return String of all the history entries
 */
String historyText(String service, String environment, String keyType) {
    String arguments = "${service} ${keyType} ${environment} --history text"
    return efVersionCommand(arguments)
}

/**
 * Return the history object of a service
 * @param service
 * @param environment
 * @param keyType ami-id, dist-hash, or something else
 * @return com.ellation.ef.ServiceHistory
 */
ServiceHistory history(String service, String environment, String keyType) {
    String arguments = "${service} ${keyType} ${environment} --history text"
    return new ServiceHistory(historyText(service, environment, keyType))
}

/**
 * Calls the actual ef-version command
 * Assumes ef-version command or ef-open pip package is installed on this machine
 * @param arguments
 * @return the String output of this command
 */
private String efVersionCommand(String arguments) {
    dir("efVersion") {
        String script = "ef-version ${arguments} --devel"
        gitWrapper.checkoutRepository(branch: "master", credentialsId: env.ETP_SSH_KEY_CREDENTIALS_ID,
                url: env.ELLATION_FORMATION_REPO_URL)
        sshagent([env.ETP_SSH_KEY_CREDENTIALS_ID]) {
            return sh(script: "${script}", returnStdout: true).trim()
        }
    }
}
