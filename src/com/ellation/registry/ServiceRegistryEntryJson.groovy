package com.ellation.registry

import com.ellation.registry.exception.ServiceNotFoundException
import groovy.json.JsonSlurperClassic

/**
 * Class that stores one service's information in the service_registry.json
 */
class ServiceRegistryEntryJson implements ServiceRegistryEntry, Serializable {
    /**
     * Service name
     */
    final String service
    /**
     *  SubServices associated with the service
     */
    final List<String> subServices = []
    /**
     * type of the service
     */
    final String type
    /**
     * Github URL of main repository of service
     */
    final String repository
    /**
     * Map of Github URLs of other repositories that the service uses.
     * Key is the shortname of the Github URL, value is the URL.
     */
    final List<String> otherRepositories
    /**
     * Chef Role associated with the service
     */
    final String chefRole
    /**
     * Runbook Url
     */
    final String runbookURL
    /**
     * Team name that owns this service
     */
    final String team
    /**
     * Name of the parent service whose ami will be used to build this service's ami
     */
    final String parentServiceAmi

    /**
     * Constructor that reads in the entire service_registry.json and parses the one service specified and stores
     * the information
     * @param serviceRegistryContent service_registry.json content
     * @param service name of the service you wish to store
     * @throws java.lang.IllegalArgumentException on invalid json content
     */
    ServiceRegistryEntryJson (String serviceRegistryContent, String service ) {
        JsonSlurperClassic slurper = new JsonSlurperClassic()
        def content = slurper.parseText(serviceRegistryContent)
        this.service = service

        if (!content.containsKey('application_services') || !content['application_services'].containsKey(service)) {
            throw new ServiceNotFoundException("Service ${service} can not be found")
        }

        Map<String, Object> serviceData = content['application_services'][service]

        this.type              = serviceData['type']
        this.repository        = serviceData['repository']
        this.otherRepositories = serviceData['other_repositories'] ?: []
        this.runbookURL        = serviceData['runbook_url']
        this.chefRole          = serviceData['chef_role']
        this.team              = serviceData['team'] ?: ""
        this.parentServiceAmi  = serviceData['parent_service_ami'] ?: ""

        this.subServices = content['application_services'].findAll { key, value ->
            key =~ /${service}\.(.*)/
        }.collect { key, value ->
            return key
        }
    }
}
