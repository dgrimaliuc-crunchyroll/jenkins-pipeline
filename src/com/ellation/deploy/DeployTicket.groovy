package com.ellation.deploy

import com.cloudbees.groovy.cps.NonCPS
import com.ellation.git.CommitInfo
import com.ellation.github.GithubClient
import com.ellation.github.exception.GithubRuntimeException
import com.ellation.jira.Ticket
import com.ellation.ef.ServiceRevision
import groovy.text.SimpleTemplateEngine

class DeployTicket extends Ticket implements Serializable {

    HashMap<String, Object> changelogs = [:]

    /**
     * A list of ServiceRevision objects
     * Used by the ticket template to fill the production details
     */
    public ArrayList productionInfo

    /**
     * A list of ServiceRevision objects
     * Used by the ticket template to fill the staging details
     */
    public ArrayList stagingInfo

    /**
     * Links required in "Relevant links section"
     *  Entry format [name:'', link:'']
     */
    public ArrayList links

    /**
     * Jira Component id. If set, it will be setup in the created ticket as well.
     */
    public String component

    /**
     * The name of the service
     */
    public String service

    /**
     * The ticket template has to be passed from outside
     * As I did not found a way to load shared library resources
     * directly from this class. To check what variables are being passed
     * to the template see the method renderContent
     */
    private String template

    private String releaseNotesTemplate

    /**
     * Jira project id
     */
    private Integer jiraProject = 15600

    /**
     * Jira issue type. 3 stands for task
     * https://jira.tenkasu.net/rest/api/3/issuetype
     */
    private Integer jiraIssueType = 3

    /*
     * The commit identified on production
     * Used for the ami version only
     */
    public String productionCommit

    DeployTicket(Object steps, amiTicket = true) {
        super(steps)
        if (amiTicket) {
            this.template = steps.libraryResource 'com/ellation/deploy/amiTicket.tmpl'
        } else {
            this.template = steps.libraryResource 'com/ellation/deploy/distHashTicket.tmpl'
        }

        this.releaseNotesTemplate = steps.libraryResource 'com/ellation/deploy/releaseNotes.tmpl'
    }

    DeployTicket create() {
        create(
            summary: this.renderSummary(),
            description: this.renderContent(),
            labels: [service],
            component: component,
            project: jiraProject,
            issuetype: jiraIssueType

        )
        return this
    }

    void update() {
        this.update([description: this.renderContent()])
    }

    void transition(Transition transition) {
        this.transition(transition.getValue())
    }

    void fail() {
        // Must update the custom field Failed Reason before transitioning to failed state
        // https://jira.tenkasu.net/browse/OPS-14192
        // You need an admin or use postman to figure out what the field looks like to update it correctly.
        Map fields = [ "customfield_13860" : [
                "self": "https://jira.tenkasu.net/rest/api/2/customFieldOption/12093",
                "value": "Jenkins Failure",
                "id": "12093" ]
        ]
        this.update(fields)
        this.transition(Transition.ANY_TO_FAILED)
    }

    /**
     * Sometimes we need to fail the ticket from any state
     * But we do not have a transition any to closed
     * @return
     */
    void failAndClose() {
        this.fail()
        this.comment('Closed by automation')
        this.transition(Transition.FAILED_TO_CLOSED)
    }

    /**
     * Clears all tickets except current deploy ticket from "Manual QA"
     */
    void clearManualQAColumn() {
        def status = "Manual QA"
        def service = this.service
        def project = this.jiraProject

        try {
            def jql = "project = '$project'  and  labels = '$service' and status ='${status}' and key != '${this.issue.data.key}'"
            def issues = steps.jiraJqlSearch jql: jql

            this.log jql

            issues.data.issues.each { it ->
                def ticketToClose = load(it.key)
                this.log("Should load ${it.key}")
                ticketToClose.transition(Transition.CLOSED.getValue())
            }
        } catch (Exception e) {
            log "Could not close the tickets"
            log e.getMessage()
        }
    }

    protected String renderContent() {
        return this.renderTemplate(this.template, getBindings())
    }

    /**
     * Builds and returns the bindings passed to the template
     * @return Map<String, Object> a map containing the variables to be passed to the template
     */
    protected Map<String, Object> getBindings() {
        Boolean stagingFallback = false

        if (this.productionCommit && this.productionInfo) {
            ServiceRevision serviceRevision = this.productionInfo.first()
            stagingFallback = serviceRevision.commit_hash != this.productionCommit
        }

        Map<String, Collection<CommitInfo>> deployNotes = [:]

        boolean haveDeployNotes = false
        changelogs.each { String repoKey, Object changelog ->
            deployNotes[repoKey]  = changelog['commits'].findAll { it.preDeploy != null || it.postDeploy != null }
            if (deployNotes[repoKey].size() > 0) {
                haveDeployNotes = true
            }
        }

        Map <String, Object> binding = [
            "production"       : this.productionInfo,
            "staging"          : this.stagingInfo,
            "links"            : this.links,
            "service"          : this.service,
            "deployNotes"      : deployNotes,
            "haveDeployNotes"  : haveDeployNotes,
            "stagingFallback"  : stagingFallback,
            "productionCommit" : productionCommit,
            "releaseNotes"     : getReleaseNotes(),
        ]
        return binding
    }

    /**
     * Will parse the changelogs associated with the deploy ticket
     * and render release notes
     * @return HashMap the key will be the repo name, the value is the rendered changelog
     */
    private Map<String, String> getReleaseNotes() {
        Map<String, String> releaseNotes = [:]

        changelogs.each { repoKey, changelog ->
            releaseNotes[repoKey] = changelog['commits'].size() > 0 ?  renderChangelog(changelog) : 'No Changes'
        }

        return releaseNotes
    }

    /**
     * Renders a  changelog for a single repository, including library release notes
     * @param changelog
     * @return
     */
    private String renderChangelog(Map<String, Object> changelog) {
        Map<String, String> libraryReleaseNotes = [:]
        List<CommitInfo> libraryCommits = changelog['commits'].findAll { it.libraries.size() > 0 }

        libraryCommits.each { commit ->
            commit.libraries.each { it ->
                List<String> parts = getGithubRepoInformation(it)
                String content
                try {
                    content = getLibraryReleaseNotes(it)
                } catch (GithubRuntimeException e) {
                    content = "Could not get release notes for ${it} \\\\ " + e.getMessage()
                }
                libraryReleaseNotes["${parts[0]}/${parts[1]}:${parts[2]}"] = content
            }
        }

        Map<String, Object> bindings =  [
            "commits"     : changelog['commits'],
            "releaseNotes": libraryReleaseNotes,
        ]

        return renderTemplate(releaseNotesTemplate, bindings)
    }

    /**
     * This has to live in own method or else Jenkins freaks out
     * in the calling method
     * @param githubUrl
     * @return List<String> Contains 3 entries: owner, repo, tag, example ['crunchyroll','test-instance','0.1.0']
     */
    @NonCPS
    protected List<String> getGithubRepoInformation(String githubUrl) {
        def match = (githubUrl =~ /https:\/\/github.com\/(.*)\/(.*)\/releases\/tag\/(.*)/)
        return [match[0][1], match[0][2], match[0][3]]
    }

    /**
     * @param gitReleaseUrl release url, example: https://github.com/crunchyroll/test-instance/releases/tag/0.1.0
     * @return String the text of the release notes
     */
    protected String getLibraryReleaseNotes(String gitReleaseUrl) {
        return getGithubClient().getReleaseNotesFromUrl(gitReleaseUrl).body
    }

    /**
     * @return GithubClient
     */
    protected GithubClient getGithubClient() {
        return steps.etp.githubClient()
    }

    private String renderTemplate(template, binding) {
        def engine = new SimpleTemplateEngine()
        return engine.createTemplate(template).make(binding).toString()
    }

    private String renderSummary() {
        def buildDate = new Date().format('MM/dd/yyyy')
        return "Promote  ${service} to Prod [${buildDate}]"
    }

    /**
     *
     * @param message
     */
    private void log(String message) {
        steps.println "Debug Ticket: ${message}"
    }

}
