import java.util.regex.Matcher
import java.util.regex.Pattern
import static groovy.json.JsonOutput.*
import groovy.json.JsonSlurperClassic
import groovy.transform.Field
import hudson.AbortException

import com.ellation.git.GitHelper

@Field Map<String, String[]> changedFiles = null

def setEfChangedFiles() {
    def git_helper = new GitHelper(this)
    changedFiles = git_helper.getChangedFilesGrouped()

    // Create sets of templates to be tested under the keys configs-templates and cloudformation-templates
    ['configs', 'cloudformation'].each { type ->
        templatesKey = type + '-templates'
        changedFiles[templatesKey] = (changedFiles.containsKey(type)) ?
                changedFiles[type].collect { getTemplate(it) } : []
        changedFiles[templatesKey] = changedFiles[templatesKey].toSet()
    }
}

def printChangedFiles() {
    println "The following files have been identified as changed and will be tested:"
    println prettyPrint(toJson(changedFiles))
}

def testSiteConfigUniformity() {
    def chefConfigPath = 'chef/library/cookbooks/base-tools-cx/files/ef_site_config.yml'
    if (changedFiles['root'].contains('ef_site_config.yml') ||
            (changedFiles.containsKey('chef') && changedFiles['chef'].contains(chefConfigPath))) {
        def rootSiteConfig = readFile('ef_site_config.yml')
        def chefSiteConfig = readFile(chefConfigPath)
        assert rootSiteConfig == chefSiteConfig
    }
}


def testJsonSyntax() {
    changedFiles.each { group, files ->
        files.each { fileName ->
            // Skip linting unrendered templates
            if (fileName.toLowerCase().endsWith('.json') && !(fileName.contains("templates"))) {
                sh(script: "jq '.' ${fileName} > /dev/null")
            }
        }
    }
}

def testYamlSyntax() {
    changedFiles.each { group, files ->
        files.each { fileName ->
            if ((fileName.toLowerCase().endsWith('.yml') || fileName.toLowerCase().endsWith('.yaml')) &&
                    !(fileName.contains("templates"))) {  // Skip linting unrendered templates
                sh(script: "yamllint -d relaxed ${fileName} > /dev/null")
            }
        }
    }
}

def testConfigsResolve() {
    changedFiles['configs-templates'].each { template ->
        def service = getServiceFromConfigTemplate(template)
        def testEnvironments
        if (service == "all") {
            testEnvironments = ["proto0", "staging", "prod"]
        } else if (service == "ssh") {
            testEnvironments = ["proto0", "staging", "prod"]
        } else {
            testEnvironments = getServiceEnvironments(service)
        }
        testEnvironments.each { env ->
            sh(script: "ef-resolve-config ${env} ${template} --lint --silent")
        }
    }
}

def lintCfTemplates() {
    // Limit rendering cloudformation templates to the below environments. This is because
    // lookups will fail for environments where an application hasn't been deployed yet.
    def cfTestEnvironments = [ "proto0", "global.ellationeng", "mgmt.ellationeng" ]
    changedFiles['cloudformation-templates'].each { template ->
        def service = getServiceFromCfTemplate(template)
        def serviceEnvironments = getServiceEnvironments(service)
        def testEnvironments = serviceEnvironments.intersect(cfTestEnvironments)
        testEnvironments.each { env ->
            sh(script: "ef-cf ${template} ${env} --lint")
        }
    }
}

/**
 * CI check for ellation_formation on if there are any unused cloudformation parameters found in the template
 * or parameter files based on the changed files in this branch/PR
 * @return
 */
def checkForUnusedCloudformationParameters() {
    if (!changedFiles.containsKey("cloudformation")) {
        return
    }
    boolean unusedParametersInChangedFiles = false
    for(String cloudformationFile in changedFiles["cloudformation"]) {
        List<String> parameters = []
        if (isCloudformationTemplateFile(cloudformationFile)) {
            parameters = getParametersFromTemplateFile(cloudformationFile)
        }
        if (isCloudformationParameterFile(cloudformationFile)) {
            parameters = getParametersFromParametersFile(cloudformationFile)
        }
        String templateFile = getTemplate(cloudformationFile)
        String fileContent = readFile(file: templateFile)
        for (String parameter in parameters) {
            // In the file content find the parameter surrounded by " " or { } without giving a false positive
            // in case parameter is stated in a comment surrounded by spaces
            Matcher unusedParameterMatcher = (fileContent =~ /["{]${parameter}["}]/)
            if (unusedParameterMatcher.count <= 1) {
                println("Unused parameter ${parameter} found in ${cloudformationFile}")
                unusedParametersInChangedFiles = true
            }
        }
    }
    if (unusedParametersInChangedFiles) {
        error("Unused Cloudformation parameters found.")
    }
}

/**
 * Returns all environments we wish to test against for a given service based
 * on the services listed in the service_registry.json. Proto becomes proto0
 * and we remove any instances of alpha.
 *
 * @param service name of the service in the service_registry to look up
 * @return list of test environments
**/
private def getServiceEnvironments(service) {
    String serviceRegistryContent = readFile(file: "${env.WORKSPACE}/service_registry.json")
    def serviceRegistry = getServiceRegistry(serviceRegistryContent)
    def envs
    serviceRegistry.each{ k,v ->
        if (v.containsKey(service)) {
            envs = v[service]['environments']
        }
    }

    if (!envs) {
        error("Service '${service}' not found in the service registry.")
    }

    // Modify ef ephemeral environments
    envs.remove('alpha')
    if (envs.contains('proto')) {
        envs.remove('proto')
        envs.add('proto0')
    }
    return envs
}

/**
 * Return a JSON object representation of the service registry file.
 */
private def getServiceRegistry(String serviceRegistryContent) {
    return new JsonSlurperClassic().parseText(serviceRegistryContent)
}

/**
 * Return a JSON object representation of the json string
 */
private def parseJsonString(String jsonString) {
    return new JsonSlurperClassic().parseText(jsonString)
}

/**
 * Returns the template for a given ellation_formation cloudformation and config file.
 * Used when a parameter file has changed and the corresponding template should be tested.
 *
 * @param filePath relative path to the template or parameter file
 * @return path to the matching template file
 */
private def getTemplate(filePath) {

    if (filePath.contains("templates")) {
        return filePath
    }

    def filePathArr = filePath.tokenize("/")
    def rootFolder = filePathArr[0]
    def fileName = filePathArr[-1].tokenize(".")

    if (rootFolder == "cloudformation") {
        // ex. cloudformation/services/parameters/account-api.parameters.staging.json
        //  -> cloudformation/services/templates/account-api.json
        filePathArr[-1] = fileName[0] + "." + fileName[-1]
    } else if (rootFolder == "configs") {
        // ex. configs/account-api/parameters/config.yaml.parameters.json
        //  -> configs/account-api/templates/config.yaml
        filePathArr[-1] = fileName[0..-3].join(".")
    } else {
        error("getTemplate: Invalid input - ${filePath}")
    }

    filePathArr[filePathArr.indexOf("parameters")] = "templates"

    return filePathArr.join("/")
}

/**
 * Given a cloudformation parameters file, returns a list of parameters
 * @param filePath to a cloudformation parameters file
 * @return List<String> of parameters in the parameters file
 */
private List<String> getParametersFromParametersFile(String filePath) {
    String fileContent = readFile(file: filePath)
    def jsonObject = parseJsonString(fileContent)
    List<String> parameters = []
    for (Object parameter in jsonObject) {
        parameters.add(parameter["ParameterKey"])
    }
    return parameters
}

/**
 * Given a cloudformation template file, returns a list of parameters
 * @param filePath to a cloudformation template file
 * @return List<String> of parameters in the parameters file
 */
private List<String> getParametersFromTemplateFile(String filePath) {
    String fileContent = readFile(file: filePath)
    def jsonObject = parseJsonString(fileContent)
    List<String> parameters = []
    if (jsonObject.containsKey("Parameters")) {
        parameters = jsonObject["Parameters"].keySet() as List<String>
    }
    return parameters
}

private String getServiceFromCfTemplate(String templatePath) {
    String[] splitPath = templatePath.tokenize("/")
    return splitPath[-1].minus(".json")
}

private String getServiceFromConfigTemplate(String templatePath) {
    String[] splitPath = templatePath.tokenize("/")
    return splitPath[1]
}

/**
 * Is this file path a cloudformation template file
 * @param path to cloudformation file
 * @return True if a cloudformation template file, false otherwise.
 */
private boolean isCloudformationTemplateFile(String path) {
    String[] splitPath = path.tokenize("/")
    return splitPath[0] == "cloudformation" && splitPath[2] == "templates"
}

/**
 * Is this file path a cloudformation parameters file
 * @param path to cloudformation file
 * @return True if a cloudformation template file, false otherwise.
 */
private boolean isCloudformationParameterFile(String path) {
    String[] splitPath = path.tokenize("/")
    return splitPath[0] == "cloudformation" && splitPath[2] == "parameters"
}

/**
 * Given a config parameters file, returns a map of parameters with environments as keys
 * @param filePath to a config parameters file
 * @return Map<String, Object> of parameters in the parameters file
 */
Map<String, Object> getParametersFromConfigParameters(String filePath) {
    String fileContent = readFile(file: filePath)
    Map<String, Object> jsonObject = parseJsonString(fileContent)
    Map<String, Object> params = jsonObject["params"]
    return params
}

/**
 * Given a blob of text, find aws encrypted secrets
 * @param String text to check against
 * @return List<String> encrypted secrets found
 */
List<String> getEncryptedSecretsFromText(String text) {
    Pattern pattern = ~'\\{\\{aws:kms:decrypt,(.*)\\}\\}'
    Matcher matcher = (text =~ pattern)
    List<String> matches = []
    while(matcher.find()) {
        matches << matcher.group(1)
    }
    return matches
}

/**
 * Given an encrypted secret, check if secret was encrypted for the specific
 * service in the specific environment.
 * @param String secret cyphertext to check
 * @param String service to check against
 * @param String env to check against
 * @return boolean: true if secret matches, false if not
 */
boolean canServiceDecryptSecret(String secret, String service, String env) {

    String result = decryptSecret(secret, env)
    // the keys contain underscores instead of dots
    service = service.replaceAll('\\.', '_')
    Pattern expectedKey = ~"Key: ${env}-${service}\$"
    if (result =~ expectedKey) {
        return true
    }

    return false
}

/**
 * Decrypt a secret using ef-password
 * @param String secret cyphertext to check
 * @param String env to check against
 * @return String ef-password output
 */
private String decryptSecret(String secret, String env) {
    String command = "ef-password --decrypt $secret service $env"
    String result = ""
    try {
        result = sh(script: command, returnStdout: true)
    } catch(AbortException) {
        return null
    }
    return result

}

/**
 * If params contain encrypted secrets, check that they can be decrypted by
 * service in env
 * @param Map<String, object> params parameters to check
 * @param String service to check against
 * @param String env to check against
 * @return List<String> secrets that cannot be decrypted
 */
List<String> findBadSecrets(Map<String, Object> params, String service, String env) {
    String json = toJson(params)
    String pretty = prettyPrint(json)
    List<String> secrets = getEncryptedSecretsFromText(pretty)
    List<String> fails = []
    for (secret in secrets) {
        boolean result = canServiceDecryptSecret(secret, service, env)
        if (!result) {
            fails << secret
        }
    }
    return fails
}

/**
 * Given params, check if they contain encrypted secrets
 * service in env
 * @param Map<String, object> params parameters to check
 * @return boolean true if there are encrypted secrets, false otherwise
 */
boolean encryptedSecretsInParams(Map<String, Object> params) {
    String jsonParams = toJson(params)
    String prettyJson = prettyPrint(jsonParams)
    List<String> encrypted = getEncryptedSecretsFromText(prettyJson)
    return (encrypted.size() > 0)
}

/**
 * Check that changed parameter files contain secrets suitable for the services
 * they are intended for
 * Treats proto as proto0
 */
def checkParameterEncryptedSecrets() {
    List<String> parametersChanged = changedFiles['configs'].findAll {
        it.contains("/parameters/")
    }
    List<String> fails = []
    for(def filePath in parametersChanged) {
        println("Testing $filePath")
        String service = getServiceFromConfigTemplate(filePath)
        List<String> envs
        if (service == "all") {
            envs = ["proto0", "staging", "prod"]
        } else if (service == "ssh") {
            envs = ["proto0", "staging", "prod"]
        } else {
            envs = getServiceEnvironments(service)
        }
        Map<String, Object> params = getParametersFromConfigParameters(filePath)

        for (String env in envs + ["proto"]) {
            def envParams = params[env]
            if (env == "proto") {
                env = "proto0"
            }
            List<String> badSecrets = findBadSecrets(envParams, service, env)
            for (badSecret in badSecrets) {
                fails << "ERROR: ${env}-${service}: bad secret <<${badSecret}>>"
            }
        }

        if (encryptedSecretsInParams(params["default"])) {
            fails << "ERROR: default-${service}: Found encrypted secrets"
        }
    }

    for (String failure in fails) {
        currentBuild.result = 'FAILURE'
        println(failure)
    }
}
