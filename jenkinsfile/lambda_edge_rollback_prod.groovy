@Library('ellation') _
import org.jenkinsci.plugins.workflow.libs.Library

// Define the parameters of the pipeline. If you edit the parameters, it will be changed back to this when you run it.
properties(
    [parameters([
        string(name: "SERVICE", defaultValue: "", description: "Lambda service to rollback", trim: true),
        string(name: "CLOUDFRONT_DISTROS", defaultValue: "", description: "Comma-separated Cloudfront distros to rollback", trim: true)
    ])
])

String service = env.SERVICE?.trim() ? env.SERVICE.trim() : ""

ArrayList<String> cloudfrontDistros = (env.CLOUDFRONT_DISTROS) ? env.CLOUDFRONT_DISTROS?.split(",") : []
for(int i = 0; i < cloudfrontDistros.size(); i++) {
    cloudfrontDistros[i] = cloudfrontDistros[i].trim()
}

node("universal") {

    if (service == "") {
        error("Parameter SERVICE was not defined or an empty string")
    }

    if (cloudfrontDistros.size() < 1) {
        error("No cloudfront distros were specified for a lambda edge")
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
    }

    stage("Rollback commit hash") {
        lambdas.each { lambdaName ->
            efVersion.rollbackCommitHash(lambdaName, "prod")
        }
    }

    stage("Deploy") {
        efCf.deployLambda(service, "prod")
    }

    stage("Rollback version number") {
        lambdas.each { lambdaName ->
            efVersion.rollbackVersionNumber(lambdaName, "prod")
        }
    }

    stage("Deploy Cloudformation") {
        // Update the cloudformation stack of the cloudfront stacks if lambda edge
        cloudfrontDistros.each { cloudfront ->
            efCf.deployFixture(cloudfront, "prod")
        }
    }
}
