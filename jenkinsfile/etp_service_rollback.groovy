@Library('ellation') _

String environment = env.ENVIRONMENT?.trim()
String mainService = env.SERVICE?.trim()
String subServices = env.SUBSERVICES?.trim() ?: ""
String percent = env.PERCENT?.trim() ?: null

if (!mainService) {
    error("SERVICE parameter not set")
}
if (!environment) {
    error("ENVIRONMENT parameter not set")
}

node("universal") {
    // Cleanup the workspace before doing anything in case of past failures
    cleanWs()

    stage("Rollback service") {
        String mainServiceTargetAmiId = ""
        // If the service name contains a colon :, it means they want to set the service to a specific AMI ID
        if (mainService.contains(':')) {
            String[] result = mainService.split(':')
            mainService = result[0].trim()
            if (result.size() < 2) {
                error("Service ${mainService} had a colon : but no target AMI ID was given.")
            } else {
                mainServiceTargetAmiId = result[1].trim()
            }
        }

        // Check if the subservices have a colon in it with some AMI ID value, otherwise throw an error
        subServices.each { subServiceName ->
            String subServiceTargetAmiId = ""
            if (subServiceName.contains(':')) {
                String[] result = subServiceName.split(':')
                subServiceName = result[0].trim()
                if (result.size() < 2) {
                    error("Sub Service ${subServiceName} had a colon : but no target AMI ID was given.")
                }
            }
        }

        currentBuild.description = "rollback:${mainService}:${environment}"
        echo("Currently deployed ${mainService} AMI ID: ${efVersion.getAmiId(mainService, environment)}")

        if (mainServiceTargetAmiId) {
            // TODO missing pipeline number and repository information, once we finish implementing serviceHistory
            // jenkins step this should be easily obtained
            efVersion.setAmiId(mainService, environment, mainServiceTargetAmiId)
            echo("Set ${mainService} to AMI ID: ${mainServiceTargetAmiId}")
        } else {
            efVersion.rollbackAmiId(mainService, environment)
            echo("Rolling back ${mainService} to AMI ID: ${efVersion.getAmiId(mainService, environment)}")
        }

        if (subServices) {
            List<String> subServicesList = subServices.split(',')
            subServicesList.each { subServiceName ->
                String subServiceTargetAmiId = ""
                // If the sub service name contains a colon :, it means they want to set the service to a specific AMI ID
                if (subServiceName.contains(':')) {
                    String[] result = subServiceName.split(':')
                    subServiceName = result[0].trim()
                    subServiceTargetAmiId = result[1].trim()
                }
                echo("Currently deployed ${subServiceName} AMI ID: ${efVersion.getAmiId(subServiceName, environment)}")
                if (subServiceTargetAmiId) {
                    // TODO missing pipeline number and repository information, once we finish implementing serviceHistory
                    // jenkins step this should be easily obtained
                    efVersion.setAmiId(subServiceName, environment, subServiceTargetAmiId)
                    echo("Set ${subServiceName} to AMI ID: ${subServiceTargetAmiId}")
                } else {
                    efVersion.rollbackAmiId(subServiceName, environment)
                    echo("Rolling back ${subServiceName} to AMI ID: ${efVersion.getAmiId(subServiceName, environment)}")
                }
            }
        }
    }
    stage("Deploy") {
        efCf.deployService(mainService, environment, "master", percent)
    }
}
