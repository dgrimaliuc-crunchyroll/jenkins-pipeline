import com.ellation.git.GitRepoUrl

/**
 * Attempt to generate the API Documentation
 *
 * @param service
 * @param environment
 * @param buildBranch
 * @param repositoryUrl service repo that contains the swagger folder or a Makefile that will generate and upload
 * the api documentation
 */
void generate(String service, String environment, String buildBranch, String repositoryUrl) {
    dir("Swagger") {
        GitRepoUrl gitRepo = new GitRepoUrl(repositoryUrl)
        String gitCredentials = env.ETP_SSH_KEY_CREDENTIALS_ID
        git(branch: buildBranch, credentialsId: gitCredentials, url: gitRepo.getSshUrl())

        if (fileExists("Makefile")) {
            def result = sh(returnStatus: true, script: "make -q docgen")
            if (result == 2) {
                println("docgen target doesn't exist in Makefile.")
            } else {
                sh(script: "make docgen")
            }
        } else {
            println("No Makefile detected.")
        }

        if (fileExists("docs/swagger")) {
            def awsSyncCommand = "aws s3 sync --delete docs/swagger/ " +
                    "s3://ellation-cx-${environment}-static/docs/swagger/${service}/ " +
                    "--cache-control max-age=300"
            sh(script: awsSyncCommand)
        } else {
            println("Swagger directory not detected.")
        }
    }
}
