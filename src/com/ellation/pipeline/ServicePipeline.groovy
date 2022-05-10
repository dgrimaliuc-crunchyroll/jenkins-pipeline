package com.ellation.pipeline

import com.ellation.web.Config

import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

/**
 * Interface all pipeline implementations must adhere to if they want to be used in our Jenkins environment. All
 * the functions are ordered in the recommended way they should be called.
 */
interface ServicePipeline {
    /**
     * Returns a map of each service's build job after build() has been called. If called before build(),
     * should return an empty map.
     * @return a map of each service's build job
     */
    Map<String, RunWrapper> getServiceBuildJobs()

    /**
     * Sets the behavior of the pipeline when a job fails.
     * @param behavior is a closure with the coded failure behavior
     */
    void setJobFailureBehavior(Closure behavior)

    /**
     * Sets the behavior of the pipeline when a test fails.
     * @param behavior is a closure with the coded failure behavior
     */
    void setTestFailureBehavior(Closure behavior)

    /**
     * Runs the unit tests implemented in this service's repo.
     */
    void unitTests()

    /**
     * Runs the build jobs for this service. Running this should should populate the return object
     * for getServiceBuildJobs()
     */
    void build(Config config)

    /**
     * Update's this service history in ef-version. The class implementing this method should know what service
     * type is based on the config class passed in, example ami-id, dist-hash or some other future values.
     * @param type is what kind of key is used in this service's history in ef-version
     * @param stable true if this build should be marked stable, false if not
     * @param noprecheck true if a check of the build was deployed to AWS, false if no check should be done
     */
    void updateServiceVersion(boolean stable, boolean noprecheck)

    /**
     * Generates the api docs of this service if it's setup in its repository.
     */
    void generateApiDocs()

    /**
     * Returns true if ETP_CFD_SCHEMA_BUILD_COMMAND or ETP_CFD_SCHEMA_PATH_DIST_HASH are defined.
     *
     * @return boolean
     */
    boolean schemaStoreIntegrationEnabled()

    /**
     * Schema-store integration. Builds and uploads the service's JSON Schema to the schema store service.
     */
    void uploadJsonSchema()

    /**
     * Deploys the service into AWS.
     */
    void deploy()

    /**
     * Runs any QA/QE tests on the service in AWS.
     */
    void postDeployTests()

    /**
     * Publishes notifications on the status of the pipeline.
     */
    void publishNotifications()

    /**
     * Display any artifact information the pipeline generated into Jenkins.
     */
    void displayArtifactInformation()

}
