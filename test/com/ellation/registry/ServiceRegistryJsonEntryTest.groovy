package com.ellation.registry

import com.ellation.registry.exception.ServiceNotFoundException
import org.junit.Test

import static org.junit.Assert.*

class ServiceRegistryJsonEntryTest {
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
        def entry = new ServiceRegistryEntryJson(getServiceRegistryContent(), SERVICE_UNDER_TEST)

        assertEquals(SERVICE_UNDER_TEST, entry.service)
    }

    @Test(expected = ServiceNotFoundException)
    void serviceNotFoundInRegistry() {
        new ServiceRegistryEntryJson( getServiceRegistryContent(), INVALID_SERVICE)
    }

    @Test
    void typeIsSet() {
        String expectedResult = 'http_service'

        ServiceRegistryEntryJson entry    = new ServiceRegistryEntryJson( getServiceRegistryContent(), SERVICE_UNDER_TEST)

        assertEquals(expectedResult,  entry.type)
    }

    @Test
    void chefRoleIsSet() {
        String expectedResult = 'test-instance'

        ServiceRegistryEntryJson entry    = new ServiceRegistryEntryJson( getServiceRegistryContent(), SERVICE_UNDER_TEST)

        assertEquals(expectedResult, entry.chefRole)
    }

    @Test
    void runbookURLIsSet() {
        String expectedResult = 'TBD'

        ServiceRegistryEntryJson entry    = new ServiceRegistryEntryJson( getServiceRegistryContent(), SERVICE_UNDER_TEST)

        assertEquals(expectedResult,  entry.runbookURL)
    }

    @Test
    void singleRepositoryIsSet() {
        String expectedResult = 'github.com/crunchyroll/ellation_formation'
        String expectedRepoKey = SERVICE_UNDER_TEST

        ServiceRegistryEntryJson entry    = new ServiceRegistryEntryJson( getServiceRegistryContent(), SERVICE_UNDER_TEST)

        assertEquals(expectedResult,  entry.getRepository())
    }

    @Test
    void subServicesAreLoaded() {
        String expectedResult = "test-instance.subservice"

        ServiceRegistryEntryJson entry    = new ServiceRegistryEntryJson( getServiceRegistryContent(), SERVICE_UNDER_TEST)

        assertEquals(1, entry.subServices.size())
        assertEquals(expectedResult, entry.subServices[0])
    }

    @Test
    void singleServicesHaveZeroSubservices() {
        ServiceRegistryEntryJson entry    = new ServiceRegistryEntryJson( getServiceRegistryContent(), SINGLE_SERVICE)

        assertEquals(0, entry.subServices.size())
    }

    @Test
    void otherRepositories() {
        List<String> expectedValues = [
            "github.com/crunchyroll/multiple-app-service-2",
            "github.com/crunchyroll/multiple-app-service-3"
        ]

        ServiceRegistryEntryJson entry    = new ServiceRegistryEntryJson( getServiceRegistryContent(), MULTIPLE_APP_SERVICE)

        assertEquals(2, entry.otherRepositories.size())

        expectedValues.each { String expectedRepo ->
            assertTrue(entry.otherRepositories.contains(expectedRepo))
        }
    }

    /**
     * Returns the content of a test service registry
     * @return
     */
    private String getServiceRegistryContent() {
        this.class.getResource('/com/ellation/registry/service_registry.json').text
    }
}
