import com.ellation.web.Config

/**
 * Checks if this service is an api gateway rest api by checking for environment variables for api gateway rest api
 * resource ids and stage names.
 *
 * @return true if it is and the resources have the same size number or false otherwise
 */
boolean checkApiGatewayDeploymentEnvVars() {
    ArrayList<String> logicalResourceIds = parameterHelper.convertCommaStringToArray(env.API_GATEWAY_REST_API_RESOURCE_IDS)
    ArrayList<String> stageNames = parameterHelper.convertCommaStringToArray(env.API_GATEWAY_STAGE_NAMES)
    if (logicalResourceIds.size() == 0 || stageNames.size() == 0 || logicalResourceIds.size() != stageNames.size()) {
        echo("No logical resource IDs or stage names specified, or the numbers between the two don't match.")
        echo("Do not perform a create deployment for an api gateway rest api.")
        return false
    } else {
        echo("Logical resource IDs and stage names found. Assuming this is an api gateway rest api, " +
                "can perform a deployment of the api.")
        return true
    }
}

/**
 * Create a deployment of the rest api. Assumes both env vars are the same size of comma delimited entries
 * @param config
 * @param logicalResourceId
 * @param stageName
 * @param region
 * @return
 */
String createDeployment(Config config, String region="us-west-2") {
    ArrayList<String> logicalResourceIds = parameterHelper.convertCommaStringToArray(env.API_GATEWAY_REST_API_RESOURCE_IDS)
    ArrayList<String> stageNames = parameterHelper.convertCommaStringToArray(env.API_GATEWAY_STAGE_NAMES)
    for (int i = 0; i < logicalResourceIds.size(); i++) {
        String restApiId = getRestApiId(config, logicalResourceIds[i], region)
        String script = "aws --region ${region} apigateway create-deployment --rest-api-id ${restApiId} " +
                "--stage-name ${stageNames[i]}"
        sh(script: script)
    }
}

/**
 * Get the ID of the rest api in the service's stack.
 * @param config
 * @param logicalResourceId
 * @param region
 * @return String of the rest api ID
 */
private String getRestApiId(Config config, String logicalResourceId, String region="us-west-2") {
    String script = "aws --region ${region} cloudformation describe-stack-resource " +
            "--stack-name ${config.environment}-${config.service} --logical-resource-id ${logicalResourceId} " +
            "--query StackResourceDetail.PhysicalResourceId --output text"
    return sh(script: script, returnStdout: true).trim()
}
