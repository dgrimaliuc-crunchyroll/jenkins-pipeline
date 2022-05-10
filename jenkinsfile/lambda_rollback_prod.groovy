@Library('ellation') _
import org.jenkinsci.plugins.workflow.libs.Library

// Define the parameters of the pipeline. If you edit the parameters, it will be changed back to this when you run it.
properties(
    [parameters([
        string(name: "SERVICE", defaultValue: "", description: "Lambda service to rollback", trim: true)
    ])
])

String service = env.SERVICE?.trim() ? env.SERVICE.trim() : ""

node("universal") {

    if (service == "") {
        error("Parameter SERVICE was not defined or an empty string")
    }

    stage("Check if Lambda") {
        // Check out ellation_formation and load the service registry
        ServiceRegistryEntry serviceEntry
        List<ServiceRegistryEntry> subServiceEntries = []
        dir("service_registry") {
            gitWrapper.checkoutRepository(branch: config.deployBranch, credentialsId: env.ETP_SSH_KEY_CREDENTIALS_ID,
                    url: "git@github.com:crunchyroll/ellation_formation.git")
            serviceEntry = serviceRegistry.parseServiceRegistryJson("./service_registry.json", config.service)
        }
    }

    List<String> lambdas = []
    stage("Find lambdas") {
        String templateString = efCf.renderLambdaTemplate(service, "prod")
        Map<String, Object> templateJson = jsonHelper.stringToJson(templateString)
        lambdas = cloudformation.findLambdas(templateJson)

        // In multi lambda stacks, we need to add the parent service even though it's not a real lambda for commit hash
        // history for DPLY tickets
        if (!lambdas.contains(service)) {
            lambdas.add(service)
        }
    }

    stage("Rollback commit hash") {
        lambdas.each { lambdaName ->
            efVersion.rollbackCommitHash(lambdaName, "prod")
        }
    }

    stage("Deploy") {
        efCf.deployLambda(service, "prod")
    }
}
