/**
 * Finds the names of all the lambdas in the json object of a cloudformation template
 * @param jsonTemplate - json object representing the cloudformation template of a service
 * @return List<String> - name of all lambdas found
 */
List<String> findLambdas(Map<String, Object> jsonTemplate) {
    List<String> lambdas = []
    List<String> lambdaTypes = ["AWS::Serverless::Function", "AWS::Lambda::Function"]
    jsonTemplate["Resources"].each { key, value ->
        if (lambdaTypes.contains(value["Type"]) ) {
            // Look for the role property and split it by /
            // Ex. arn:aws:iam::{{ACCOUNT}}:role/proto0-lambda-data-compliance-request-processor
            String roleName = value["Properties"]["Role"].split("/")[-1]
            // Split the lambda role name
            // Ex. proto0-lambda-data-compliance-request-processor
            List parts = roleName.split("-")
            // Reconstruct the lambda name without the env and with the dashes '-'
            String lambdaName = parts[1..(parts.size()-1)].join("-")
            lambdas.add(lambdaName)
        }
    }

    return lambdas
}
