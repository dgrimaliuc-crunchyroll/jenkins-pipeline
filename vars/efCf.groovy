/**
 * Call ef-cf to deploy a service. Because it does a git clone here, best that this step be called in its own
 * directory
 * @param service
 * @param environment
 * @param deployBranch
 * @param percent
 * @return String output of the deploy
 */
String deployService(String service, String environment, String deployBranch = "master", String percent = null) {
    String arguments = "cloudformation/services/templates/${service}.json ${environment} --poll --commit"
    if (percent) {
        arguments += " --percent ${percent}"
    }
    return efCfCommand(deployBranch, arguments)
}

/**
 * Call ef-cf to deploy a fixture. Because it does a git clone here, best that this step be called in its own
 * directory
 * @param service
 * @param environment
 * @param deployBranch
 * @return String output of the deploy
 */
String deployFixture(String service, String environment, String deployBranch = "master") {
    String arguments = "cloudformation/fixtures/templates/${service}.json ${environment} --poll --commit"
    return efCfCommand(deployBranch, arguments)
}

/**
 * Call ef-cf to deploy a lambda. Lambdas a bit of a special case due to our lack of enforcement lambdas being declared
 * as fixtures or services being located in the fixtures or services folder. Because it does a git clone here,
 * best that this step be called in its own directory.
 * @param service
 * @param environment
 * @param deployBranch
 * @return String output of the deploy
 */
String deployLambda(String service, String environment, String deployBranch = "master") {
    String arguments = findLambdaTemplate(service)
    // If it's not a service or fixture, ef-cf will error out and the pipeline will fail

    arguments += " ${environment} --poll --commit"

    return efCfCommand(deployBranch, arguments)
}

/**
 * Calls ef-cf to render the lambda cloudformation template without any junk data. Because it does a git clone here,
 * best that this step be called in its own directory.
 * @param service
 * @param environment
 * @param deployBranch
 * @return String pure JSON form of the template with all the symbols rendered, no extra header data
 */
String renderLambdaTemplate(String service, String environment, String deployBranch = "master") {
    String arguments = findLambdaTemplate(service, deployBranch)
    // If it's not a service or fixture, ef-cf will error out and the pipeline will fail

    arguments += " ${environment} --render"

    String templateString = efCfCommand(deployBranch, arguments)
    List<String> templateStringSplit = templateString.trim().split("\n")
    while(true) {
        if (templateStringSplit[0].startsWith("WARNING")) {
            templateStringSplit = templateStringSplit.drop(1)
        }
        else {
            templateString = templateStringSplit.join("\n")
            break
        }
    }
    return templateString
}

/**
 * Find the template file's path for this lambda service
 * @param lambdaService
 * @return String the template file's path for this lambda
 */
private String findLambdaTemplate(String lambdaService, String deployBranch = "master") {
    dir("efCf-find-lambda") {
        gitWrapper.checkoutRepository(branch: deployBranch, credentialsId: env.ETP_SSH_KEY_CREDENTIALS_ID,
                url: env.ELLATION_FORMATION_REPO_URL)
        // Find out if this lambda is a fixture or service
        String templatePath = "cloudformation/fixtures/templates/${lambdaService}.json"
        boolean fixtureExists = fileExists(templatePath)
        if (fixtureExists) {
            return templatePath
        } else {
            return "cloudformation/services/templates/${lambdaService}.json"
        }
        // If it's not a service or fixture, ef-cf will error out and the pipeline will fail
    }
}

/**
 * Call the actual ef-cf command
 * Assumes ef-cf command or ef-open pip package is installed on this machine
 * @param arguments
 * @return
 */
private String efCfCommand(String deployBranch, String arguments) {
    dir("efCf-Deploy") {
        gitWrapper.checkoutRepository(branch: deployBranch, credentialsId: env.ETP_SSH_KEY_CREDENTIALS_ID,
                url: env.ELLATION_FORMATION_REPO_URL)
        String script = "ef-cf ${arguments} --devel"
        sshagent([env.ETP_SSH_KEY_CREDENTIALS_ID]) {
            return sh(script: "${script}", returnStdout: true).trim()
        }
    }
}
