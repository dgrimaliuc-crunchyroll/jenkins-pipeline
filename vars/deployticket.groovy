import com.ellation.deploy.DeployTicket
import com.ellation.deploy.ProductionCommitProvider
import com.ellation.deploy.Transition as DeployTicketTransitions
import com.ellation.ef.CommitHashInformation
import com.ellation.ef.ServiceHistory
import com.ellation.ef.ServiceRevision
import com.ellation.git.GitHelper
import com.ellation.git.GitRepoUrl
import com.ellation.web.Config
import groovy.transform.Field
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

@Field states = DeployTicketTransitions
@Field config = null

/**
 * Creates ami or dist hash ticket based on config.serviceType value
 * @param config
 * @return
 */
def call(Config config) {
    this.config = config
    return create(config)
}

/**
 * Loads service and subservices information from ef-version and
 * updates the ticket. This method needs to be called for AMI  based tickets
 * only
 */
def addStagingInformation(DeployTicket ticket) {
    if (!ticket) {
        this.log "ticket not initialized, ignoring addStagingInformation command"
        return
    }
    ticket.stagingInfo = getServiceInformation(config, 'staging')
    ticket.update()
}

/**
 * Adds the link generated from currentBuild and buildJobs
 * @param  Map<String, RunWrapper> buildJobs
 */
def addRelevantLinks(DeployTicket ticket, Map<String, RunWrapper> buildJobs = [:]) {
    if (!ticket) {
        this.log "ticket not initialized, ignoring links command"
        return
    }
    def links = [[name:'Pipeline url', link: currentBuild.getAbsoluteUrl()]]

    buildJobs.each { serviceName, buildJob ->
        links = links + ['name': serviceName, "link": buildJob.getAbsoluteUrl()]
    }

    ticket.links = links
    ticket.update()
}

/**
 * Creates the JIRA ticket
 * @param config
 * @return DeployTicket
 */
DeployTicket create(Config config) {
    String productionCommit = ""
    try {
        if(env.ETP_DEPLOY_STUB_PROD_COMMIT) {
            //this value is for testing purposes only so I am not adding it in config
            productionCommit = env.ETP_DEPLOY_STUB_PROD_COMMIT.trim()
            log("Using stub value $productionCommit")
        } else {
            productionCommit = new ProductionCommitProvider(this).productionCommit(config.service, config.serviceType)
            log("Starting commit hash for prod in deploy ticket is: ${productionCommit}")
        }
    } catch (RuntimeException e) {
        log("Could not detect commit in production:" + e.getMessage())
        return null
    }

    CommitHashInformation commitInfo = new CommitHashInformation(productionCommit)

    HashMap<String, Object> changelogs = [:]

    if(commitInfo.isMultiple() ) {
        this.log "${config.service} detected as multiple app"

        if (!config.multipleRepository) {
            error("We detected a multiple commit, but could not find the necessary configuration")
        }

        commitInfo.commits.each { String repoKey, String commit ->
            if (!config.repos.containsKey(repoKey)) {
                error ("Deploy Ticket: configuration Problem : Could not find the repository ${repoKey}")
            }
            String gitRepoUrl = config.repos[repoKey]
            dir(repoKey) {
                changelogs[repoKey] = getChangelog(commit, gitRepoUrl, config.credentials)
            }
        }
    } else {
        GitRepoUrl gitRepo = new GitRepoUrl(config.repository)
        // Should always use GitHub SSH Url since username/password authentication is considered to be deprecated now
        // For now we're making so that even if a https url of the repository is specified, convert it to the ssh url
        // and use the ssh credentials
        changelogs[gitRepo.repoName] = getChangelog(commitInfo.commitHash, gitRepo.sshUrl, env.ETP_SSH_KEY_CREDENTIALS_ID)
    }

    boolean has_changes = false

    changelogs.each { repo, changelog ->
        if (changelog['has_changes'] == true) {
            has_changes = true
        }
    }

    ServiceRevision productionVersion = getServiceHistory(config.service, "prod", config.serviceType)?.latest()
    boolean firstTimeDeploy = false
    if (productionVersion == null) {
        println("First time deploy to prod detected for this service. Forcing creation of the deploy ticket.")
        firstTimeDeploy = true
    }

    if (firstTimeDeploy == false && has_changes == false) {
        this.log "Same code version found in production. Deploy ticket will not be created"
        return
    }

    DeployTicket ticket = new DeployTicket(this, config.serviceType == 'http_service')
    ticket.productionInfo = getServiceInformation(config, 'prod')
    ticket.service = config.service
    ticket.productionCommit = productionCommit
    ticket.changelogs = changelogs

    ticket.create()
    ticket.transition(states.SELECTED_FOR_DEPLOY)
    addTicketLinkToBuildDescription(ticket)

    if (firstTimeDeploy) {
        ticket.comment("This is a first time deploy to prod, so no production information " +
                "will be found in the ticket.")
    }

    return ticket
}

/**
 * Get meta data of all the changes between the two commits.
 * @param previousCommit
 * @param repository
 * @param credentials
 * @return Map
 */
private Map getChangelog(previousCommit, repository, credentials) {
    GitHelper gitHelper = new GitHelper(this)
    def result = git(url: repository, credentialsId: credentials)
    Map changelog = gitHelper.getChangelog(result.GIT_COMMIT, previousCommit)
    return changelog
}

/**
 * Get the service revisions history for service in environment
 * @param config A pipeline config object
 * @param environment staging or prod (ef-version will throw error if not one of these)
 * @return ArrayList service revision of service in environemnt, or empty list if there is none
 */
private ArrayList getServiceInformation(Config config, environment) {
    List<ServiceRevision> serviceRevisions = []

    def services = [config.service]

    if (config.subservices) {
        services.addAll(config.subservices.split(','))
    }

    services.each {
        //the default value in config is http_service
        //so by default we will behave as for a http_service
        def history = getServiceHistory(it, environment, config.serviceType)
        ServiceRevision revision = history.latest()
        if (revision) {
            serviceRevisions.add(revision)
        }
    }
    return serviceRevisions
}

private ServiceHistory getServiceHistory(String serviceName, String environment, String serviceType) {
    if (config.serviceType == "dist_static") {
        return ellation_formation.distHashHistory(serviceName, environment)
    }
    if (config.serviceType == "aws_lambda") {
        return new com.ellation.ef.ServiceHistory(efVersion.historyText(serviceName, environment, "commit-hash"))
    }
    return ellation_formation.history(serviceName, environment)
}

private void addTicketLinkToBuildDescription(deployTicket) {
    def key = deployTicket.issue.data.key
    def link = "https://jira.tenkasu.net/browse/${key}"
    currentBuild.description = "<a href='${link}'>${key}</a>";
}

/**
 * Specialised console log which appends a prefix
 */
private void log(message) {
    println "Deploy Ticket: ${message}"
}
