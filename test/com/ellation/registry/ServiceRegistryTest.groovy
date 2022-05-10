package com.ellation.registry

import com.ellation.registry.exception.ServiceNotFoundException
import org.junit.Test

import static org.junit.Assert.*

class ServiceRegistryTest {
    /**
     * This is the default valid service we use in our unit tests
     */
    public static  final SERVICE_UNDER_TEST = "test-instance"
    /**
     * This is a non existing service that we use in our tests
     */
    public static  final INVALID_SERVICE = "invalidService"
    /**
     * This is a service without subServices
     */
    public static  final SINGLE_SERVICE = "single-service"
    /**
     * This is a service with multiple repositories
     */
    public static  final MULTIPLE_APP_SERVICE = "multiple-app-service"

    @Test
    void serviceIsInitialized() {
        def registry = ServiceRegistry.fromJson(getServiceRegistryContent())
        assertTrue(registry.registry.containsKey(SERVICE_UNDER_TEST))
    }

    @Test(expected = ServiceNotFoundException)
    void serviceNotFoundInRegistry() {
        ServiceRegistry.fromJson(getServiceRegistryContent()).getService(INVALID_SERVICE)
    }

    @Test
    void typeIsSet() {
        String expectedResult = 'http_service'
        def entry = ServiceRegistry.fromJson(getServiceRegistryContent()).getService(SERVICE_UNDER_TEST)

        assertEquals(expectedResult, entry.type)
    }

    @Test
    void chefRoleIsSet() {
        String expectedResult = 'test-instance'

        def entry = ServiceRegistry.fromJson(getServiceRegistryContent()).getService(SERVICE_UNDER_TEST)

        assertEquals(expectedResult, entry.chefRole)
    }

    @Test
    void runbookURLIsSet() {
        String expectedResult = 'TBD'

        def entry = ServiceRegistry.fromJson(getServiceRegistryContent()).getService(SERVICE_UNDER_TEST)

        assertEquals(expectedResult,  entry.runbookURL)
    }

    @Test
    void singleRepositoryIsSet() {
        String expectedResult = 'github.com/crunchyroll/ellation_formation'

        def entry = ServiceRegistry.fromJson(getServiceRegistryContent()).getService(SERVICE_UNDER_TEST)

        assertEquals(expectedResult,  entry.getRepository())
    }

    @Test
    void subServicesAreLoaded() {
        String expectedResult = "test-instance.subservice"

        def entry = ServiceRegistry.fromJson(getServiceRegistryContent()).getService(SERVICE_UNDER_TEST)

        assertEquals(1, entry.subServices.size())
        assertEquals(expectedResult, entry.subServices[0])
    }

    @Test
    void singleServicesHaveZeroSubservices() {
        def entry = ServiceRegistry.fromJson(getServiceRegistryContent()).getService(SINGLE_SERVICE)

        assertEquals(0, entry.subServices.size())
    }

    @Test
    void otherRepositories() {
        List<String> expectedValues = [
            "github.com/crunchyroll/multiple-app-service-2",
            "github.com/crunchyroll/multiple-app-service-3"
        ]

        def entry = ServiceRegistry.fromJson(getServiceRegistryContent()).getService(MULTIPLE_APP_SERVICE)

        assertEquals(2, entry.otherRepositories.size())

        expectedValues.each { String expectedRepo ->
            assertTrue(entry.otherRepositories.contains(expectedRepo))
        }
    }
    @Test
    void findServiceByClosure() {
        String expectedResult = 'single-service'
        String searchString = '.*single-service'
        def registry = ServiceRegistry.fromJson(getServiceRegistryContent())
        def entry = registry.find { x -> x.repository =~ searchString }

        assertEquals(expectedResult, entry.service)
    }

    /**
     * Returns the content of a test service registry
     * @return
     */
    private String getServiceRegistryContent() {
        this.class.getResource('/com/ellation/registry/service_registry.json').text
    }
}
