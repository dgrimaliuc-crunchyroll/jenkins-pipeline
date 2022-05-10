/**
 * Wrapper for the git checkout to make it easier to use.
 */

/**
 * Checkout code from GIT by providing the named parameters with checks in place. Will then call the actual method.
 * @param args Map that must contain branch, credentials ID, and github URL (ssh version works too).
 * Mimics 3 of the parameters from the git step.
 * @return git object on checkout result
 */
def checkoutRepository(Map<String, Object> args) {
    boolean error = false
    if (!args.containsKey("branch")) {
        println("Missing parameter branch, using master/main as default branch.")
        args["branch"] = null
    }
    if (!args.containsKey("credentialsId")) {
        println("Missing parameter credentialsId")
        error = true
    }
    if (!args.containsKey("url")) {
        println("Missing parameter url")
        error = true
    }
    if (error) {
        error("Call to this method missing parameter(s), cannot continue")
    }

    return checkoutRepository(args["branch"], args["credentialsId"], args["url"])
}

/**
 * Checkout code from GIT being able to fallback to default master/main branch
 *
 * @param branch the branch or commit to checkout
 * @param credentials the credentials stored in Jenkins to do a Git Checkout
 * @param gitUrl the git URL of the repository
 * @return git object on checkout result
 */
def checkoutRepository(String branch, String credentials, String gitUrl) {
    println("Called checkout with branch: ${branch} repository: ${gitUrl}")
    def result
    if (branch) {
        result = git(branch: branch, credentialsId: credentials, url: gitUrl)
    } else {
        // Fallback to accommodate both default branches
        println("Branch not specified, trying out master/main")
        try {
            result = git(branch: "master", credentialsId: credentials, url: gitUrl)
        } catch (Exception error) {
            println("Failed to checkout master branch, trying main branch")
            result = git(branch: "main", credentialsId: credentials, url: gitUrl)
        }
    }

    return result
}
