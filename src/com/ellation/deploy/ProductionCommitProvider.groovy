package com.ellation.deploy

import com.ellation.deploy.exception.NoStagingVersionException
import com.ellation.deploy.exception.NoValidCommitException
import com.ellation.ef.ServiceHistory
import com.ellation.ef.ServiceRevision

/**
 * Encapsulates logic of finding a commit on production
 * If the commit is not found on production, it will look for it on staging.
 * This fallback is added temporarily until all services are setting metadata
 * correctly in prod. Currently secure apps use an older version of
 * template.deploy.groovy so merging this branch without this fallback would break it.
 * The fallback will be removed later as part of TC-126.
 */
class ProductionCommitProvider implements Serializable {

    // Refers to jenkins steps reference from currently executing pipeline
    def steps

    /**
     * @param steps the pipeline context
     */
    ProductionCommitProvider(steps) {
        this.steps = steps
    }

    /**
     * Get the commit hash of the service in prod
     * @param service
     * @param serviceType
     * @return String commit hash of service in prod. In the scenario of a first time deploy, will use the very first
     * commit that can be found for this service in staging.
     */
    String productionCommit(String service, String serviceType) {
        //the default value in config is http_service
        //so by default we will behave as for a http_service
        if (serviceType == 'dist_static') {
            return steps.ellation_formation.getDistHash(service, 'prod')
        }
        if (serviceType == 'aws_lambda') {
            return steps.efVersion.getCommitHash(service, 'prod')
        }
        return getHttpServiceCommit(service)
    }

    /**
     * For an http_service service type, returns the production's currently deployed commit hash.
     * @param service
     * @return String of the commit hash of the service deployed, In the scenario of a first time deploy, will use
     * the very first commit that can be found for this service in staging.
     */
    private String getHttpServiceCommit(String service) {
        ServiceHistory history = steps.ellation_formation.history(service, "prod")
        ServiceRevision productionVersion = history.latest()
        if (productionVersion == null) {
            steps.echo("First time deploy to prod detected for this service. Grabbing the first commit in staging")
            // Must be a first time deploy to prod from staging
            history = steps.ellation_formation.history(service, "staging")
            productionVersion = history.first()
            if (!productionVersion) {
                throw new NoStagingVersionException("No history in staging to obtain a commit hash from since prod" +
                        "service history doesn't exist.")
            }
        }
        String commitHash = productionVersion.commit_hash
        // If the metadata for this service was not set correctly, and no commit hash was set
        if (!commitHash) {
            // We look on staging for the commit associated with the ami id
            ServiceRevision stagingVersion = steps.ellation_formation.history(service, 'staging').find productionVersion.value
            if (!stagingVersion) {
                throw new NoStagingVersionException("Could not fallback to staging")
            }
            commitHash = stagingVersion.commit_hash
        }
        if (!commitHash) {
            throw new NoValidCommitException("Could not detect production commit")
        }
        return commitHash
    }

}
