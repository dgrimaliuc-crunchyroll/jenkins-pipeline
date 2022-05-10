package com.ellation.jira

import org.junit.Test
import static org.junit.Assert.*

class TicketTest {

    @Test
    void load() {
        def ticketLoader = new Ticket( [jiraGetIssue: { return 'foo' } as Object])

        def ticket = ticketLoader.load("JIRA-123")

        assertTrue(ticket instanceof Ticket)
        assertEquals('foo', ticket.issue)
    }

    @Test
    void update() {
        def editParams = null
        def steps = [jiraEditIssue: { editParams = it } as Object]
        def ticket = new Ticket(steps)
        ticket.issue = [data: [key: 'JIRA-123']]

        ticket.update(summary: 'batman was here')

        assertEquals('batman was here', editParams.issue['fields']['summary'])
    }

    @Test
    void createWillReturnTicket() {
        def calledWith = null

        def stepsMock = [
                jiraNewIssue: { calledWith = it; return 'foo' }
        ]

        def ticket = new Ticket(stepsMock as Object)
        def summary = 'I am the summary'
        def desc = 'I am the description'

        def resp = ticket.create(summary: summary, description: desc , project: 2, issuetype: 3)

        assertCreateIssueFieldsAreSet(calledWith, summary, desc)

        assertTrue(resp instanceof Ticket)
        assertEquals('foo', resp.issue)
    }

    @Test
    void createWithComponent() {
        def calledWith = null

        def stepsMock = [
                jiraNewIssue: { calledWith = it; return 'foo' }
        ]

        def ticket = new Ticket (stepsMock as Object)

        def summary = 'I am the summary'
        def desc = 'I am the description'

        def fields = [
            summary: summary,
            description: desc,
            component: 123,
            project: 1,
            issuetype: 3
        ]
        ticket.create(fields)
        assertCreateIssueFieldsAreSet(calledWith, summary, desc)
        assertNotNull(calledWith.issue.fields.components)
        assertEquals("123", calledWith.issue.fields.components[0].id.toString())
    }

    @Test
    void transitionMakesTheRightCall() {
        def callKey
        def wantedTransition = '123'
        def stepsMock = [
                jiraTransitionIssue: { it -> callKey = it; return 'foo' }

        ]
        def ticket = new Ticket (stepsMock as Object)
        ticket.issue = ['data': ['key': 'JIRA-123']]

        ticket.transition(wantedTransition)

        assertEquals('JIRA-123', callKey.idOrKey)
        assertEquals(wantedTransition, callKey.input.transition.id)
    }

    private void assertCreateIssueFieldsAreSet(calledWith, String summary, String desc) {
        assertNotNull(calledWith.issue.fields)
        assertEquals(summary, calledWith.issue['fields']['summary'])
        assertEquals(desc, calledWith.issue['fields']['description'])
        assertNotNull(calledWith.issue.fields.project)
        assertNotNull(calledWith.issue.fields.issuetype)
    }

}
