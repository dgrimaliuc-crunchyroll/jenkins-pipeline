/**
 *
 * @param service
 * @param environment
 * @param amiId
 * @param flags map with the following optional keys: stable, commit_hash, build
 * @return
 */
def set (service, environment, amiId, flags = [:]) {

    def arguments = "${service} ami-id ${environment} --set ${amiId} --commit"

    if (flags.stable && flags.stable == true) {
        arguments = arguments + " --stable"
    }

    if (flags.commit_hash ) {
        arguments = arguments + " --commit_hash ${flags.commit_hash}"
    }

    if (flags.build ) {
        arguments = arguments + " --build ${flags.build}"
    }
    if (flags.pipeline_build ) {
        arguments = arguments + " --pipeline_build ${flags.pipeline_build}"
    }
    if (flags.noprecheck && flags.noprecheck == true) {
        arguments = arguments + " --noprecheck"
    }

    return efversion(arguments)

}

/**
 * @param service specifies what service to build
 * @param environment specifies on what environment we set the ami
 * @param commit_hash specifies the commit_hash
 * @param stable bool, set to true to mark the dist-hash as stable
 */
def setDistHash(service, environment, commit_hash, stable = false)
{
    def arguments = "${service} dist-hash ${environment} --set ${commit_hash} --commit_hash ${commit_hash} --commit"

    if (stable) {
        arguments += " --stable --location https://s3-us-west-2.amazonaws.com/ellation-cx-${environment}-static/${service}/dist-hash" +
                " --noprecheck"
    } else {
        arguments += " --noprecheck"
    }
    efversion(arguments)
}

/**
 * Debug utility to get current ami for a service
 */
def getAmiId(service, environment) {

    def arguments = "${service} ami-id ${environment}  --get"
    def AMI_ID = efversion(arguments)

    if (AMI_ID == null) {
        throw new Exception("Could not get AMI ID for ${service} on ${environment}")
    }

    AMI_ID
}

/**
 * Returns the dist-hash for a particular service
 * @param service name of the service
 * @param environment name of the environment
 */
def getDistHash(service, environment) {
    def arguments = "${service} dist-hash ${environment} --get"
    return efversion(arguments)
}

/**
 * Returns a history object with all the entries
 * @param service
 * @param environment
 * @return com.ellation.ef.ServiceHistory
 */
def history(service, environment) {
    def arguments = "--history text $service ami-id $environment"
    def serviceHistory = efversion(arguments)
    return new com.ellation.ef.ServiceHistory(serviceHistory)
}


/**
 * Returns a dist-hash history object with all the entries
 * @param service
 * @param environment
 * @return com.ellation.ef.ServiceHistory
 */
def distHashHistory(service, environment) {
    def arguments = "--history text $service dist-hash $environment"
    def serviceHistory = efversion(arguments)
    return new com.ellation.ef.ServiceHistory(serviceHistory)
}

/**
 * Appends the argument to ef_version and returns the output
 * so that ef_version can be run
 * @param String arguments
 */
private def efversion(arguments) {
    dir("ellation_formation") {
        gitWrapper.checkoutRepository(branch: "master", credentialsId: env.ETP_SSH_KEY_CREDENTIALS_ID,
                url: env.ELLATION_FORMATION_REPO_URL)
        def command = "ef-version $arguments"
        return sh(script: "$command", returnStdout: true).trim()
    }
}

/**
 * Wrapper around rollback
 * The command is correct, but it if what is currently set in registry is stable
 * it will not change the registry.
 */
def rollback(service, environment) {
    def arguments = "${service} ami-id ${environment} --rollback --commit"
    efversion(arguments)
}

/**
 * Wrapper for the promote
 * @param amiId
 * @param region
 */
void promote(String amiId, String account, String profile, String region="us-west-2") {

    sh """aws --profile ${profile} --region ${region} ec2 modify-image-attribute --image-id ${amiId} --launch-permission '{"Add":[{"UserId":"${account}"}]}' """
    tagResource(amiId, 'Prod', 'true', profile, region)
}

/**
 * Tag the ec2 resource with a Key, Value
 * @param resourceId
 * @param tagKey
 * @param tagValue
 */
void tagResource(String resourceId, String tagKey, String tagValue, String profile, String region="us-west-2") {
    sh("aws --profile ${profile} --region ${region} ec2 create-tags --resources ${resourceId} --tags Key=${tagKey},Value=${tagValue}")
}

return this
