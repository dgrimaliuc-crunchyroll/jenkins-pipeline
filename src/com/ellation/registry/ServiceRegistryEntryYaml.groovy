package com.ellation.registry

// Download this jar of snakeyaml in Jenkins runtime environment, for IDE you still need to add this library
// in the build.gradle file for editor to help you in local development
// https://issues.jenkins-ci.org/browse/JENKINS-19401 says just use @Grab for libraries not provided by Jenkins
@Grab('org.yaml:snakeyaml:1.24')
import org.yaml.snakeyaml.Yaml

/**
 * Class that stores one service's information in the service_registry.yaml
 */
class ServiceRegistryEntryYaml implements ServiceRegistryEntry, Serializable {
    /**
     * Service name
     */
    final String service
    /**
     * Type of the service
     */
    final String type
    /**
     * Team the service belongs to
     */
    final String team
    /**
     * SubServices associated with the service
     */
    final List<String> subServices = []
    /**
     * Main repository of the service
     */
    final String repository
    /**
     * Dummy field for now
     */
    final List<String> otherRepositories
    /**
     * Chef role that the service uses
     */
    final String chefRole
    /**
     * Notifications that should be alerted for this service
     */
    final Map<String, Object> notifications
    /**
     * Runbook URL of service
     */
    final String runbookURL
    /**
     * List of test jobs to run after this service is deployed
     */
    final List<String> integrationTestJobs
    /**
     * Should a JIRA ticket be created for this service
     */
    final boolean createJiraTicket
    /**
     * Days this service should be deployed on
     */
    final List<String> deployDays
    /**
     * Name of the parent service whose ami will be used to build this service's ami
     */
    final String parentServiceAmi

    /**
     * Constructor that reads in the entire service_registry.yaml and parses the one service specified and stores
     * the information
     * @param serviceRegistryContent path to the service_registry.yaml
     * @param service name of the service you wish to store
     */
    ServiceRegistryEntryYaml(String serviceRegistryContent, String service) {
        this.service = service
        Yaml parser = new Yaml()
        Map<String, Object> serviceEntry = parser.load(serviceRegistryContent)[service]
        type = serviceEntry["Type"]?.trim() ?: ""
        team = serviceEntry["Team"]?.trim() ?: ""
        subServices = serviceEntry["SubServices"] ?: []
        repository = serviceEntry["Repository"]?.trim() ?: ""
        chefRole = serviceEntry["ChefRole"]?.trim() ?: ""
        notifications = serviceEntry["Notifications"] ?: [:]
        runbookURL = serviceEntry["RunbookURL"]?.trim() ?: ""
        integrationTestJobs = serviceEntry["IntegrationTestJobs"] ?: []
        createJiraTicket = serviceEntry["CreateJiraTicket"] ?: false
        deployDays = serviceEntry["DeployDays"] ?: []
        parentServiceAmi = serviceEntry["ParentServiceAmi"] ?: ""
    }
}
