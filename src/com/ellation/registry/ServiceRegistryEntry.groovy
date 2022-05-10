package com.ellation.registry

/**
 * Interface to represent all implementations of a ServiceRegistryEntry
 */
interface ServiceRegistryEntry {

    /**
     * Returns service name
     */
    String getService()

    /**
     * Returns a list of subServices associated with each service. Can be empty.
     */
    List<String> getSubServices()

    /**
     * Returns the type of the service
     */
    String getType()

    /**
     * Returns the github URL of the main repository of the service
     */
    String getRepository()

    /**
     * Returns a map of other repositories that this service uses.
     * The key will identify the repo name and is usually the repo name, the value is the github URL of the repository.
     * NOTE: May be deprecated as we go with having services use one repo. The current code logic doesn't work with
     * multiple repos anyway.
     * @return List of other repository URLs, does not include the main repository. Can be empty.
     */
    List<String> getOtherRepositories()

    /**
     * Must return the chef Role associated with the service
     * @return
     */
    String getChefRole()

    /**
     * Must return the runbook associated with service
     * @return
     */
    String getRunbookURL()

    /**
     * Returns the name of the team that owns this service. Not all implementations will have a valid value for this.
     * Default value "" is fine but not for ETPv2 environment.
     * @return
     */
    String getTeam()

    /**
     * Returns the name of the parent service that owns the AMI that this service will build off of
     * @return
     */
    String getParentServiceAmi()
}
