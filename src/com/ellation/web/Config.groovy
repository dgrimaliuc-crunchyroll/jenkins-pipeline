package com.ellation.web

import com.ellation.git.GitRepoUrl
import com.ellation.registry.ServiceRegistryEntry
import com.ellation.web.exception.WrongServiceEntryException

/**
 * Stores all the environment variables in jenkins and parameters specified in the build job.
 */
class Config implements Serializable {

    public int waitTime
    public String commitHash
    public String service
    public String environment
    public String serviceType
    public int buildNumber
    public String testTarget
    public String notifications
    public String postDeployTestFailNotifications
    public String repository
    public String component
    public String technology
    public String credentials
    public String subservices
    public String branch
    public String deployBranch
    public String mainBranchName
    public boolean createTicket
    public boolean runBuild
    public boolean runDeploy
    public boolean runPostDeployTest
    public boolean runRollback
    public boolean runBuildV2
    public boolean deployWithHarness
    public String baseImageOS
    public boolean multipleRepository = false
    public Map<String, String> repos = [:]

    // Properties
    String sshUser

    String jsonSchemaBuildCommand
    String jsonSchemaPathDistHash
    boolean schemaStoreIntegrationEnabled

    /**
     * Constructor
     * @param env Map of key,value environment variables in Jenkins or in the Jenkins jobs combined
     * @param params Map of key,value parameter variables in the Jenkins job
     */
    Config(env, params = [:]) {
        // Mandatory fields
        if (!env.ETP_SERVICE) {
            throw new IllegalArgumentException("ETP_SERVICE env var is not defined")
        }
        if (!env.ETP_ENVIRONMENT) {
            throw new IllegalArgumentException("ETP_ENVIRONMENT env var is not defined")
        }
        service         = env.ETP_SERVICE.trim()
        environment     = env.ETP_ENVIRONMENT.trim()

        mainBranchName  = env.MAIN_BRANCH_NAME ? env.MAIN_BRANCH_NAME.trim() : 'master'
        if (mainBranchName != 'master' && mainBranchName != 'main') {
            throw new IllegalArgumentException("MAIN_BRANCH_NAME has to be main or master")
        }

        // Optional fields
        serviceType   = env.ETP_SERVICE_TYPE?.trim() ? env.ETP_SERVICE_TYPE.trim() : "http_service"
        testTarget    = (env.ETP_TEST) ? env.ETP_TEST.trim() : service
        waitTime      = (env.ETP_WAIT) ? env.ETP_WAIT as int : 0
        notifications = env.ETP_SLACK?.trim() ? env.ETP_SLACK.trim() : ""
        postDeployTestFailNotifications = env.ETP_POST_DEPLOY_TEST_FAIL_SLACK?.trim() ? env.ETP_POST_DEPLOY_TEST_FAIL_SLACK.trim() : ""
        repository    = env.ETP_REPOSITORY?.trim() ? env.ETP_REPOSITORY.trim() : ""
        technology    = env.ETP_TECHNOLOGY?.trim() ? env.ETP_TECHNOLOGY.trim() : ""
        credentials   = env.ETP_CREDENTIALS?.trim() ? env.ETP_CREDENTIALS.trim() : ""
        component     = env.ETP_COMPONENT?.trim() ? env.ETP_COMPONENT.trim() : ""
        subservices   = env.ETP_SUBSERVICES?.trim() ? env.ETP_SUBSERVICES.trim() : ""
        buildNumber   = env.BUILD_NUMBER ? env.BUILD_NUMBER as int : 0
        baseImageOS   = env.BASE_IMAGE_OS?.trim() ? env.BASE_IMAGE_OS.trim() : 'centos'
        branch            = params.BUILDBRANCH ? params.BUILDBRANCH.trim() : mainBranchName
        commitHash        = params.COMMIT_HASH ? params.COMMIT_HASH.trim() : ""
        deployBranch      = params.DEPLOYBRANCH ? params.DEPLOYBRANCH.trim() : 'master'
        createTicket      = params.CREATE_TICKET == true
        runBuild          = params.RUN_BUILD == true
        runDeploy         = params.RUN_DEPLOY == true
        runPostDeployTest = params.RUN_POST_DEPLOY_TEST == true
        runRollback       = params.RUN_ROLLBACK == true
        runBuildV2        = params.RUN_BUILD_V2 == true
        deployWithHarness = params.DEPLOY_WITH_HARNESS ? params.DEPLOY_WITH_HARNESS == true : false

        //This is a temporary configuration value used to test TC-134 implementation
        //This  block of code and config values will go away once we populate repos
        //from the service registry. See loadServiceRegistryEntryValues method bellow
        if (env.ETP_REPOSITORIES) {
            List<String> repositories = env.ETP_REPOSITORIES.trim().split(',')
            for (int i = 0; i < repositories.size(); i++) {
                List<String> repoParts = repositories[i].split("=")
                repos[repoParts[0]] = repoParts[1]
            }
            multipleRepository = true
        }

        //Config Delta Schema Store integration
        jsonSchemaBuildCommand = env.ETP_CFD_SCHEMA_BUILD_COMMAND?.trim() ? env.ETP_CFD_SCHEMA_BUILD_COMMAND.trim() : ""
        jsonSchemaPathDistHash = env.ETP_CFD_SCHEMA_PATH_DIST_HASH?.trim() ? env.ETP_CFD_SCHEMA_PATH_DIST_HASH.trim() : ""
        schemaStoreIntegrationEnabled = this.jsonSchemaBuildCommand || this.jsonSchemaPathDistHash
    }

    /**
     * The field createTicket only reflects if the job param CREATE_TICKET is set.
     * For a deploy ticket to be created , we need ETP_REPOSITORY created as well
     * @return
     */
    def canCreateTicket() {
        return environment == "staging" && createTicket && (repository && !repository.isEmpty())
    }

    void loadServiceRegistryEntryValues(ServiceRegistryEntry entry) {
        if (entry.service != service) {
            throw new WrongServiceEntryException("Can not use the entry of another service")
        }

        if (entry.getRepository()) {
            this.repository = entry.getRepository()
            GitRepoUrl repo = new GitRepoUrl(repository)
            repos[repo.repoName] = repository
        }

        entry.otherRepositories.each { it ->
            GitRepoUrl repo = new GitRepoUrl(it)
            repos[repo.repoName] = it
        }

        multipleRepository = repos.size() > 1

        serviceType = entry.type

        if (entry.subServices.size() > 0 ) {
            subservices = entry.subServices.join(',')
        }
    }
}
