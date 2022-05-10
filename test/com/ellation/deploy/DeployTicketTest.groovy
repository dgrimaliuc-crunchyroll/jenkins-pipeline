package com.ellation.deploy

import com.ellation.github.GithubClient
import com.ellation.github.RestClient
import com.ellation.jira.Ticket
import org.junit.Test
import static org.junit.Assert.*

class DeployTicketTest {
    static String mockedReleaseNotes = 'mocked_release_notes'

    class GithubClientStub extends GithubClient {
        GithubClientStub(RestClient restClient) {
            super(restClient)
        }

        @Override
        Object getReleaseNotesFromUrl(String url) {
            return [body:mockedReleaseNotes]
        }
    }

    class DeployTicketDouble extends DeployTicket {
        public LinkedHashMap createParams = [:]
        public LinkedHashMap updateParams = [:]

        DeployTicketDouble(Object steps, amiTicket = true) {
            super(steps, amiTicket)
        }

        @Override
        Ticket create(Map params) {
            createParams = params
        }

        @Override
        void update(Map fields) {
            updateParams = fields
        }

        @Override
        protected GithubClient getGithubClient() {
            return new GithubClientStub(new RestClient())
        }
    }

    @Test
    void testCreate() {
        DeployTicket deployTicket = new DeployTicketDouble(getStepsMock())
        deployTicket.service = 'UnitTest'
        def buildDate = new Date().format('MM/dd/yyyy')
        String expectedSubject = "Promote  ${deployTicket.service} to Prod [${buildDate}]"

        deployTicket.create()

        assertEquals(15600, deployTicket.createParams['project'])
        assertEquals(3, deployTicket.createParams['issuetype'])
        assertEquals(expectedSubject, deployTicket.createParams['summary'])
        assertEquals('template', deployTicket.createParams['description'])
    }

    @Test
    void update() {
        DeployTicket deployTicket = new DeployTicketDouble(getStepsMock())

        deployTicket.create()
        deployTicket.update()

        assertEquals('template', deployTicket.updateParams['description'])
    }

    @Test
    void bingindDefaults() {
        DeployTicket deployTicket = new DeployTicketDouble(getStepsMock())

        assertEquals(0, deployTicket.getBindings()['releaseNotes'].size())
    }

    private getStepsMock() {
        [
            libraryResource: { return 'template' },
        ]
    }

}
