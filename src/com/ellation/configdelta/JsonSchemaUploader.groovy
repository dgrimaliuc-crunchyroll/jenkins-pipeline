package com.ellation.configdelta

/**
 * Used to deploy configuration json schema.
 */
class JsonSchemaUploader implements Serializable {
    private static String schemaStoreBucket = 'ellation-cx-{{ENV}}-config-schema-store'
    private Script script
    private String serviceName

    JsonSchemaUploader(Script script, String serviceName) {
        this.script = script
        this.serviceName = serviceName
    }

    /**
     * Uploads configuration json schema from memory to s3 bucket
     *
     * @param schema configuration json schema as string
     * @param environment will be replaced in bucket name 'ellation-cx-{{ENV}}-config-schema-store'
     * @param version will become a part of the deploy destination path
     * @param awsProfile aws credentials to be used for bucked upload action
     */
    void upload(String schema, String environment, String version, String awsProfile = null) {
        def target = getSchemaBucketPath(environment, version)
        def command = "echo '${schema}' | aws s3 cp - ${target} --acl bucket-owner-full-control"
        uploadToAws(command, awsProfile)
    }

    /**
     * Uploads configuration json schema from file to s3 bucket
     *
     * @param source configuration json schema file path
     * @param environment will be replaced in bucket name 'ellation-cx-{{ENV}}-config-schema-store'
     * @param version will become a part of the deploy destination path
     * @param awsProfile aws credentials to be used for bucked upload action
     */
    void uploadFromFile(
            String source,
            String environment,
            String version,
            String awsProfile = null
    ) {
        def target = getSchemaBucketPath(environment, version)
        def command = "aws s3 cp ${source} ${target} --acl bucket-owner-full-control"
        uploadToAws(command, awsProfile)
    }

    /**
     * Generates config schema bucket path.
     *
     * @param environment will be replaced in bucket name 'ellation-cx-{{ENV}}-config-schema-store'
     * @param version will become a part of the deploy destination path
     * @return deployment destination path
     */
    String getSchemaBucketPath(String environment, String version) {
        String targetBucket = schemaStoreBucket.replace('{{ENV}}', environment)
        return "s3://${targetBucket}/${serviceName}/${version}/schema.json"
    }

    private void uploadToAws(String uploadCommand, String awsProfile) {
        if (awsProfile) {
            uploadCommand += " --profile ${awsProfile}"
        }
        script.sh(uploadCommand)
    }
}
