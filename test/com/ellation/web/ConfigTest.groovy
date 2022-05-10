package com.ellation.web

import com.ellation.registry.ServiceRegistryEntry
import com.ellation.web.exception.WrongServiceEntryException
import org.junit.Test
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

class ConfigTest {

    class ServiceRegistryEntryDouble implements ServiceRegistryEntry {
        String service
        String type
        List<String> subServices = []
        Map<String, String> repos = [:]
        String chefRole
        String runbookURL
        String repository
        List<String> otherRepositories = []
        String team
        String parentServiceAmi
    }

    /**
     * This is the default value for the service name used in test.
     */
    static final String TEST_SERVICE = 'test-service'
    private env = [:]

    @Test
    void defaultBranchIsNotOverridenWhenParamIsNotSet() {
        initMandatoryEnvOptions()
        def config = new Config(env, [JENKINS_PARAM: 'whatever'])
        assertEquals('master', config.branch )
    }

    @Test
    void defaultBranchIsMaster() {
        initMandatoryEnvOptions()

        def config = new Config(env)
        assertEquals('master', config.branch )
    }

    @Test
    void defaultBranchIsMasterByConfig() {
        initMandatoryEnvOptions()

        def params          = [:]
        env.MAIN_BRANCH_NAME  = 'master'

        def config = new Config(env, params)
        assertEquals('master', config.branch )
    }

    @Test(expected = IllegalArgumentException)
    void exceptionInvalidMainBranchNameConfigParam() {
        initMandatoryEnvOptions()

        def params          = [:]
        env.MAIN_BRANCH_NAME  = 'invalid_main_branch_value'

        def config = new Config(env, params)
    }

    @Test
    void defaultBranchIsMain() {
        initMandatoryEnvOptions()

        def params          = [:]
        env.MAIN_BRANCH_NAME  = 'main'

        def config = new Config(env, params)
        assertEquals('main', config.branch )
    }

    @Test
    void youCanSetTheBranch() {
        initMandatoryEnvOptions()

        def params          = [:]
        params.BUILDBRANCH  = 'dev'

        def config = new Config(env, params)
        assertEquals('dev', config.branch )
    }

    @Test
    void createTicketDefault() {
        initMandatoryEnvOptions()
        def config = new Config(env)
        assertFalse(config.createTicket)
    }

    @Test
    void createTicketField() {
        initMandatoryEnvOptions()
        def params = [:]
        params.CREATE_TICKET = true

        def config = new Config(env, params)
        assertTrue(config.createTicket)
    }

    @Test
    void canCreateTicketNegative() {
        initMandatoryEnvOptions()
        def config = new Config(env)
        assertFalse(config.canCreateTicket())

        def params = [:]
        params.CREATE_TICKET = true

        config = new Config(env, params)

        assertFalse(config.canCreateTicket())
    }

    @Test
    void canCreateTicket() {
        initMandatoryEnvOptions()
        env.ETP_REPOSITORY = 'got@git.com'
        env.ETP_ENVIRONMENT = 'staging'

        def config = new Config(env)
        assertFalse(config.canCreateTicket())

        def params = [:]
        params.CREATE_TICKET = true

        config = new Config(env, params)

        assertTrue(config.canCreateTicket())
    }

    @Test(expected = Exception)
    void itExpectsServiceAndEnvironmentDefined() {
        def env = [:]
        new Config(env)
    }

    @Test(expected = Exception)
    void bothServiceAndEnvironmentMustBeDefined() {
        env.ETP_SERVICE     = TEST_SERVICE
        new Config(env)
    }

    @Test
    void settingServiceAndEnvironmentWorks() {
        initMandatoryEnvOptions()
        def config = new Config(env)
        assertEquals(TEST_SERVICE, config.service)
    }

    @Test
    void defaultServiceTimeIsSet() {
        initMandatoryEnvOptions()

        def config = new Config(this.env)
        assertEquals(0, config.waitTime)
    }

    @Test
    void testTargetIsEitherTheServiceOrDefinedValue() {
        initMandatoryEnvOptions()
        def config = new Config(this.env)

        assertEquals(TEST_SERVICE, config.testTarget)

        env.ETP_TEST = 'anotherService'

        config = new Config(this.env)

        assertEquals('anotherService', config.testTarget)
    }

    @Test
    void defaultComponenetState() {
        initMandatoryEnvOptions()
        def config = new Config(this.env)
        assertEquals("", config.component)
    }

    @Test
    void schemaStoreIntegrationDistHash() {
        env.ETP_SERVICE = 'dist-hash-service'
        env.ETP_ENVIRONMENT = 'proto0'
        env.ETP_CFD_SCHEMA_PATH_DIST_HASH = './schema.json'

        def config = new Config(env)

        assertEquals('./schema.json', config.jsonSchemaPathDistHash)
        assertTrue(config.schemaStoreIntegrationEnabled)
    }

    @Test
    void schemaStoreIntegration() {
        env.ETP_SERVICE = 'ami-service'
        env.ETP_ENVIRONMENT = 'proto0'
        env.ETP_CFD_SCHEMA_BUILD_COMMAND = 'cat schema.json'

        def config = new Config(env)

        assertEquals('cat schema.json', config.jsonSchemaBuildCommand)
        assertTrue(config.schemaStoreIntegrationEnabled)
    }

    @Test
    void componentIsSet() {
        env.ETP_SERVICE     = TEST_SERVICE
        env.ETP_ENVIRONMENT = 'the_environment'
        env.ETP_COMPONENT  = "123"

        def config = new Config(env)

        assertEquals("123", config.component)
    }

    @Test
    void fullValueTest() {
        env.ETP_SERVICE     = TEST_SERVICE
        env.ETP_ENVIRONMENT = 'the_environment'
        env.ETP_TEST        = 'the_other_service'
        env.ETP_WAIT        = 20
        env.ETP_SLACK       = '@the_slack_user'
        env.ETP_REPOSITORY  = 'git@something.com'
        env.ETP_TECHNOLOGY  = 'the_technology'
        env.ETP_CREDENTIALS = 'the_credentials'
        env.ETP_SUBSERVICES = 'the_subservice,the_other_subservice'
        env.ETP_CFD_SCHEMA_BUILD_COMMAND = 'cat schema.json'
        env.ETP_CFD_SCHEMA_PATH_DIST_HASH = './schema.json'

        def config = new Config(env)

        assertEquals(TEST_SERVICE, config.service)
        assertEquals('the_environment', config.environment)
        assertEquals('the_other_service', config.testTarget)
        assertEquals(20, config.waitTime)
        assertEquals('@the_slack_user', config.notifications)
        assertEquals('git@something.com', config.repository)
        assertEquals('the_technology', config.technology)
        assertEquals('the_credentials', config.credentials)
        assertEquals('the_subservice,the_other_subservice', config.subservices)
        assertEquals('cat schema.json', config.jsonSchemaBuildCommand)
        assertEquals('./schema.json', config.jsonSchemaPathDistHash)
        assertTrue(config.schemaStoreIntegrationEnabled)
    }

    @Test
    void byDefaultTheRepositoryIsNotMultiple() {
        initMandatoryEnvOptions()

        Config config = new Config(env)

        assertFalse(config.multipleRepository)
    }

    @Test
    void multipleRepositoriesCanBeInitializedFromEnv() {
        Map testRepos = [
            "repo-key-1" : "https://github.com/crunchyroll/multiple-app-service-1",
            "repo-key-2" : "https://github.com/crunchyroll/multiple-app-service-2"
        ]
        initMandatoryEnvOptions()
        env.ETP_REPOSITORIES = 'repo-key-1=https://github.com/crunchyroll/multiple-app-service-1,repo-key-2=https://github.com/crunchyroll/multiple-app-service-2'
        Config config = new Config(env)

        assertTrue(config.multipleRepository)
        testRepos.each { String repoKey, String repoValue ->
            assertEquals(repoValue, config.repos[repoKey])
        }
    }

    @Test(expected = WrongServiceEntryException)
    void usingAServiceRegistryEntryIsNotAllowedForADifferentService() {
        initMandatoryEnvOptions()
        Config config = new Config(env)
        ServiceRegistryEntryDouble entry = new ServiceRegistryEntryDouble(service: "another_service")

        config.loadServiceRegistryEntryValues(entry)
    }

    @Test
    void usingAServiceRegistryEntryWillOverrideSingleRepository() {
        initMandatoryEnvOptions()
        Config config = new Config(env)
        ServiceRegistryEntryDouble entry = new ServiceRegistryEntryDouble(service: TEST_SERVICE)
        entry.repository = 'https://github.com/crunchyroll/multiple-app-service-1'

        config.loadServiceRegistryEntryValues(entry)

        assertEquals('https://github.com/crunchyroll/multiple-app-service-1', config.repository)
    }

    @Test
    void serviceEntryWithMultipleRepositories() {
        List otherRepos = [
            "https://github.com/crunchyroll/multiple-app-service-2",
            "https://github.com/crunchyroll/multiple-app-service-3"
        ]
        initMandatoryEnvOptions()
        Config config = new Config(env)
        ServiceRegistryEntryDouble entry = new ServiceRegistryEntryDouble(service: TEST_SERVICE)
        entry.otherRepositories = otherRepos
        entry.repository = "https://github.com/crunchyroll/multiple-app-service-1"

        config.loadServiceRegistryEntryValues(entry)

        assertTrue(config.multipleRepository)

        assertEquals("https://github.com/crunchyroll/multiple-app-service-1", config.repos["multiple-app-service-1"])
        assertEquals("https://github.com/crunchyroll/multiple-app-service-2", config.repos["multiple-app-service-2"])
        assertEquals("https://github.com/crunchyroll/multiple-app-service-3", config.repos["multiple-app-service-3"])
    }

    @Test
    void serviceEntryWillOverrideType() {
        initMandatoryEnvOptions()
        Config config = new Config(env)
        ServiceRegistryEntryDouble entry = new ServiceRegistryEntryDouble(service: TEST_SERVICE)
        entry.type = 'dist_static'

        config.loadServiceRegistryEntryValues(entry)

        assertEquals('dist_static', config.serviceType)
    }

    @Test
    void serviceEntryWillOverrideSubServices() {
        List<String> testSubServices = ['subServ1', 'subServ2', 'subServ3']
        initMandatoryEnvOptions()
        Config config = new Config(env)
        ServiceRegistryEntryDouble entry = new ServiceRegistryEntryDouble(service: TEST_SERVICE)
        entry.subServices = testSubServices

        config.loadServiceRegistryEntryValues(entry)

        assertEquals(testSubServices.join(','), config.subservices)
    }

    private void initMandatoryEnvOptions() {
        this.env.ETP_SERVICE = TEST_SERVICE
        this.env.ETP_ENVIRONMENT = 'proto0'
    }

}
