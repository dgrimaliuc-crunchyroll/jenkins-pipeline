package com.ellation.registry

import com.ellation.registry.exception.ServiceNotFoundException
import groovy.json.JsonSlurperClassic

class ServiceRegistry implements Serializable {
    /**
     * Service registry
     */
    final Map registry
    /**
     *  SubServices associated with the service
     */

    /**
     * Constructor that reads in the entire service_registry.json and stores it
     * @param serviceRegistryContent service_registry.json content
     * @throws java.lang.IllegalArgumentException on invalid json content
     */
    ServiceRegistry(Map<String, Object> registryContent) {
        this.registry = registryContent
    }

    static ServiceRegistry fromJson(String jsonServiceRegistry) {
        JsonSlurperClassic slurper = new JsonSlurperClassic()
        def content = slurper.parseText(jsonServiceRegistry)
        if (!content.containsKey('application_services')) {
            throw new ServiceNotFoundException("Missing application_services key in service_registry")
        }
        return new ServiceRegistry(content['application_services'])
    }

    ServiceRegistryEntry getService(String serviceName) {
        if (!this.registry.containsKey(serviceName)) {
            throw new ServiceNotFoundException("Service ${serviceName} can not be found")
        }
        Map<String, Object> serviceData = this.registry[serviceName]

        serviceData['subServices'] = this.registry.findAll { key, value ->
            key =~ /${serviceName}\.(.?)/
        }.collect { key, value -> key }

        return new GenericServiceRegistryEntry(serviceName, serviceData)
    }

    ServiceRegistryEntry find(Closure check) {
        for (kv in this.registry) {
            def entry = this.getService(kv.key)
            if (check(entry)) {
                return entry
            }
        }
        return null
    }
}
