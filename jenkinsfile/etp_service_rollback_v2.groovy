@Library('ellation') _

import com.ellation.ef.ServiceRevision
import com.ellation.history.ServiceHistoryEntry
import com.ellation.registry.ServiceRegistryEntry


String environment = env.ENVIRONMENT?.trim()
String mainService = env.SERVICE?.trim()


String percent = env.PERCENT?.trim() ?: null

if (!mainService) {
    error("SERVICE parameter not set")
}
if (!environment) {
    error("ENVIRONMENT parameter not set")
}

def getHistoryEntry(service, envrionment, ami, serviceHistory) {
    for (entry in serviceHistory.entries) {
        if (entry.value == ami && entry.status == "stable") {
            return entry
        }
    }
    return false
}

def getPreviousStableEntry(service, envrionment, pipeline_build_number, serviceHistory) {
    for (entry in serviceHistory.entries) {
        if (entry.pipeline_build_number < pipeline_build_number && entry.status == "stable") {
            return entry
        }
    }
    return false
}
def getPreviousStableEntryByAmiId(service, envrionment, ami, serviceHistory) {
    for (entry in serviceHistory.entries) {
        if (entry.value == ami && entry.status == "stable") {
            return entry
        }
    }
    return false
}

def getHistoryEntryByPipelineId(service, envrionment, pipeline_build_number, serviceHistory) {
    for (entry in serviceHistory.entries) {
        if (entry.pipeline_build_number == pipeline_build_number && entry.status == "stable") {
            return entry
        }
    }
    return false
}

def getRollBackEntries(mainService, environment) {
    String mainServiceTargetAMI = env.AMI_ID.trim()
    deployedMainServiceAmi = efVersion.getAmiId(mainService, environment)
    echo("Currently deployed ${mainService} AMI ID: ${efVersion.getAmiId(mainService, environment)}")
    serviceHistory = ellation_formation.history(mainService, environment)
    mainServiceHistoryEntry = getHistoryEntry(mainService, environment, deployedMainServiceAmi, serviceHistory)
    if (mainServiceHistoryEntry) {
        if (mainServiceTargetAMI != "") {
            mainServiceRollbackEntry = getPreviousStableEntryByAmiId(mainService, environment, mainServiceTargetAMI , serviceHistory)
        }
        else {
            mainServiceRollbackEntry = getPreviousStableEntry(mainService, environment, mainServiceHistoryEntry.pipeline_build_number, serviceHistory)
        }
        if (mainServiceRollbackEntry) {
            //Sub Services
            subServices = getSubServices(mainService)
            List<ServiceRegistryEntry> subServiceRollbackEntries = []
            for (subservice in subServices) {
                serviceName = subservice.service
                println(serviceName)
                ami = efVersion.getAmiId(serviceName, environment)
                echo("Currently deployed ${serviceName} AMI ID: ${efVersion.getAmiId(serviceName, environment)}")
                serviceHistory = ellation_formation.history(serviceName, environment)
                historyEntry = getHistoryEntry(serviceName, environment, ami, serviceHistory)
                if (historyEntry) {
                    subServiceRollbackEntry = getHistoryEntryByPipelineId(serviceName, environment, mainServiceRollbackEntry.pipeline_build_number, serviceHistory)
                    if (subServiceRollbackEntry) {
                        subServiceRollbackEntries.add(subServiceRollbackEntry)
                    } else {
                        throw new Exception("Unable to determine sub service rollback entry")
                    }
                }
            }
            return [main: mainServiceRollbackEntry, subservices: subServiceRollbackEntries]
        } else {
            throw new Exception("Unable to determine main service rollback entry")
        }
    }
}

def getSubServices(service) {
    ServiceRegistryEntry serviceEntry
    List<ServiceRegistryEntry> subServiceEntries = []
    dir("service_registry") {
        gitWrapper.checkoutRepository(branch: "master", credentialsId: env.ETP_SSH_KEY_CREDENTIALS_ID,
                url: "git@github.com:crunchyroll/ellation_formation.git")
        serviceEntry = serviceRegistry.parseServiceRegistryJson("./service_registry.json", service)
        for (String subService in serviceEntry.getSubServices()) {
            ServiceRegistryEntry subServiceEntry = serviceRegistry.parseServiceRegistryJson("./service_registry.json", subService)
            subServiceEntries.add(subServiceEntry)
        }
    }
    return subServiceEntries
}

node("universal") {
    // Cleanup the workspace before doing anything in case of past failures
    cleanWs()
    stage("Rollback") {
        rollbackEntries = getRollBackEntries(mainService, environment)
        echo("Set ${mainService} to AMI ID: ${rollbackEntries.main.value}")
        efVersion.setAmiId(mainService, environment, rollbackEntries.main.value )
        if (rollbackEntries.subservices.size() > 0) {
            for (subService in rollbackEntries.subservices) {
                echo("Set ${subService.name} to AMI ID: ${subService.value}")
                efVersion.setAmiId(subService.name, environment, subService.value)
            }
        }
    }

    stage("Deploy") {
        efCf.deployService(mainService, environment, "master", percent)
    }

}

