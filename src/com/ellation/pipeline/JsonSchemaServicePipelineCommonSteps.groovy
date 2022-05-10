package com.ellation.pipeline

import com.ellation.configdelta.JsonSchemaUploader
import com.ellation.git.GitRepoUrl
import com.ellation.history.ServiceHistoryEntry
import com.ellation.web.Config

/**
 * Holds common steps between service pipelines.
 */
class JsonSchemaServicePipelineCommonSteps implements Serializable {
    private String globalDistBucket = 'ellation-cx-global-dist'
    private Config config
    private String githubCredentials
    private Script script
    private JsonSchemaUploader jsonSchemaUploader

    JsonSchemaServicePipelineCommonSteps(Config config, Script script) {
        this.config = config
        this.script = script
        githubCredentials = script.env.ETP_SSH_KEY_CREDENTIALS_ID
        this.jsonSchemaUploader = new JsonSchemaUploader(script, config.service)
    }

    /**
     * uploads the JSON schema to S3 schema store based on the service type
     *
     * @param commitHash
     * @param serviceType
     */
    void upload(ServiceHistoryEntry historyEntry) {
        GitRepoUrl repo = new GitRepoUrl(config.repository)
        String commitHash = historyEntry.getRepositoryCommitHash()[repo.repoName]

        switch (config.serviceType.toLowerCase()) {
            case 'http_service':
                uploadJsonSchemaAMI(commitHash)
                break
            case 'dist_hash':
                uploadJsonSchemaDistHash(commitHash)
                break
        }
    }

    /**
     * Builds configuration json schema and deploys it to S3 schema store
     *
     * @param commitHash to be checked out before building schema also used as schema version
     */
    void uploadJsonSchemaAMI(String commitHash) {
        checkout(commitHash)
        String schema = buildConfigDeltaSchema()
        jsonSchemaUploader.upload(schema, config.environment, commitHash)
        jsonSchemaUploader.upload(schema, config.environment, 'latest')
    }

    /**
     * Copies the service's JSON Schema from the global dist bucket to S3 Schema store
     *
     * @param commitHash used as configuration json schema version
     */
    void uploadJsonSchemaDistHash(String commitHash) {
        String schemaSourcePath = "s3://${globalDistBucket}/${config.service}/${commitHash}/${config.environment}/${config.jsonSchemaPathDistHash}"
        jsonSchemaUploader.uploadFromFile(schemaSourcePath, config.environment, commitHash)
        jsonSchemaUploader.uploadFromFile(schemaSourcePath, config.environment, 'latest')
    }

    /**
     * Checks if schema store integration is enabled.
     * We can not upload configuration json schema in If both schemaBuildCommand amd
     * jsonSchemaPathDistHash are null.
     *
     * @return true if schema store integration is enabled
     */
    boolean isSchemaStoreIntegrationEnabled() {
        return config.jsonSchemaBuildCommand || config.jsonSchemaPathDistHash
    }

    private String buildConfigDeltaSchema() {
        return script.sh(script: config.jsonSchemaBuildCommand, returnStdout: true)
    }

    private void checkout(String commitHash) {
        script.checkout(
                $class: 'GitSCM',
                branches: [[name: commitHash]],
                userRemoteConfigs: [[credentialsId: githubCredentials, url: config.repository]]
        )
    }
}
