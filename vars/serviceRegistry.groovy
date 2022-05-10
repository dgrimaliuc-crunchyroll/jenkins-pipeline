import com.ellation.registry.ServiceRegistry
import com.ellation.registry.ServiceRegistryEntry
import com.ellation.registry.ServiceRegistryEntryJson
import com.ellation.registry.ServiceRegistryEntryYaml

/**
 * Parse the registry file and grab the section that belongs to the specified service, YAML version
 * @param serviceRegistryFile service_registry.yaml file location
 * @param service name of the service to be parsed
 * @return
 */
ServiceRegistryEntry parseServiceRegistryYaml(String serviceRegistryFile, String service) {
    String content = readFile(serviceRegistryFile)
    ServiceRegistryEntry entry = new ServiceRegistryEntryYaml(content, service)
    return entry
}

/**
 * Parse the registry file and grab the section that belongs to the specified service, JSON version
 * @param serviceRegistryFile service_registry.json file location
 * @param service name of the service to be parsed
 * @return ServiceRegistryEntry of the main service
 */
ServiceRegistryEntry parseServiceRegistryJson(String serviceRegistryFile, String mainService) {
    String content = readFile(serviceRegistryFile)
    ServiceRegistryEntry entry = new ServiceRegistryEntryJson(content, mainService)
    return entry
}

ServiceRegistryEntry getMainServiceByRepoJson(String serviceRegistryFile, String repo) {
    String content = readFile(serviceRegistryFile)
    ServiceRegistry registry = ServiceRegistry.fromJson(content)
    ServiceRegistryEntry mainEntry = registry.find{
        entry -> entry.repository ==~ ".*${repo}" &&
        !entry.service.contains(".")
    }
    return mainEntry
}
