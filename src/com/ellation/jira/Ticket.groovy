package com.ellation.jira

import com.ellation.jira.exception.TicketAlreadyCreatedException
import com.ellation.jira.exception.TicketNotInitializedException

/**
 * This class basically wraps jira steps plugin so we can easily unit test
 */
class Ticket implements Serializable {

    /**
     * The data structure returned by jiraSteps plugin
     */
    public Object issue

    /**
     * The pipeline context which has access to
     * jenkins steps and shared libraries steps.
     * Technically a CpsScript
     */
    public Object steps

    /**
     * @param pipelineSteps The pipeline context (this)
     */
    Ticket(Object pipelineSteps) {
        this.steps = pipelineSteps
    }

    Ticket() {
    }

    /**
     * Returns a new ticket object with data loaded from idOrKey
     * @param idOrKey
     * @return
     */
    Ticket load(String idOrKey) {
        def ticket = new Ticket(steps)
        ticket.issue = steps.jiraGetIssue idOrKey: idOrKey
        return ticket
    }

    /**
     * Creates a ticket in jira.
     * Takes a map as an argument. The map requires summary and description to be set.
     * If component and service are set, they are transformed to the correct field for
     * jiraNewIssue pipeline step. Any other key will be passed to the jiraNewIssue step.
     * @param params A map. summary, description, project, issuetype are mandatory
     * @return
     */
    Ticket create(Map<String, Object> params) {
        if (this.issue != null) {
            throw new TicketAlreadyCreatedException("Please use a new ticket instance to create a ticket")
        }

        def mandatory = ['summary', 'description', 'project', 'issuetype']

        mandatory.each {
            if (!params.containsKey(it)) {
                throw new IllegalArgumentException(
                    'Ticket.create was not called with all the required params.'
                )
            }
        }

        Map fields = params

        if (fields.containsKey('component')) {
            def componentId = fields['component']
            if (componentId != null) {
                fields['components'] = [[id: "${componentId}"]]
            }
            fields.remove('component')
        }

        if (fields.containsKey('project')) {
            fields['project'] = [id: fields['project']]
        }

        if (fields.containsKey('issuetype')) {
            fields['issuetype'] = [id: fields['issuetype']]
        }

        issue = steps.jiraNewIssue(issue: [fields: fields])
        return this
    }

    /**
     * Transition ids can be accessed via the REST api's offered by JIRA
     * @param transitionId
     */
    void transition(String transitionId) {
        def transitionInput = [
            transition: [id: transitionId]
        ]

        this.steps.jiraTransitionIssue idOrKey: issue.data.key, input: transitionInput
    }

    /**
     * Check jiraEditIssue step documentation to see what you can provide in fields
     * Most common we use are description and summary
     * @param fields
     * @return
     */
    void update(Map fields) {
        steps.jiraEditIssue idOrKey: this.issue.data.key, issue: [fields: fields]
    }

    void comment(String theComment) {
        if (!this.issue) {
            throw new TicketNotInitializedException('Ticket is not initialized')
        }
        steps.jiraAddComment idOrKey: this.issue.data.key, comment: theComment
    }

}
