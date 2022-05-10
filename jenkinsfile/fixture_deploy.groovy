@Library('ellation')
import groovy.json.JsonSlurperClassic
import java.util.regex.Matcher

def weekdaysMap = [
   1: "SUNDAY",
   2: "MONDAY",
   3: "TUESDAY",
   4: "WEDNESDAY",
   5: "THURSDAY",
   6: "FRIDAY",
   7: "SATURDAY"
]

List<String> deployDays = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]
if (env["DEPLOY_DAYS"]) {
    deployDays = env["DEPLOY_DAYS"].split(",")
}

String channel = env["SLACK_CHANNEL"]

if (!env["ENVIRONMENTS"]) {
    error("Missing ENVIRONMENTS to deploy to")
}
List<String> deployEnvs = env["ENVIRONMENTS"].split(",")

String prevGit
if (!env["PREV_GIT"]) {
    println("Missing PREV_GIT number of commits to diff on. Setting to 1.")
    prevGit = "1"
} else {
    prevGit = env["PREV_GIT"]
}

node("universal") {

    Calendar calendar = Calendar.getInstance();
    if (deployDays.indexOf(weekdaysMap[calendar.get(Calendar.DAY_OF_WEEK)]) == -1) {
        println("Pipeline triggered out of deploy days and will be stopped here")
        return
    }

    gitWrapper.checkoutRepository(credentialsId: env.ETP_SSH_KEY_CREDENTIALS_ID, url: env.ELLATION_FORMATION_REPO_URL)
    String gitCommit = sh(script: "git rev-parse HEAD", returnStdout: true).trim()
    String filterDir = 'cloudformation/fixtures/templates'
    // diff only files that changed in the filter directory
    List<String> changedFixtureFiles = sh(
        script: "git diff --name-only --diff-filter=d $gitCommit~$prevGit...$gitCommit -- ${filterDir}",
        returnStdout: true).split()

    String paramDir = 'cloudformation/fixtures/parameters'
    // diff only files that changed in the parameters directory
    List<String> changedFixtureParamFiles = sh(
        script: "git diff --name-only --diff-filter=d $gitCommit~$prevGit...$gitCommit -- ${paramDir}",
        returnStdout: true).split()

    // If parameter file is for a deploy env, we add its fixture file (if not added yet)
    Matcher fixtureName
    String fixtureFile
    for (paramFile in changedFixtureParamFiles) {
        for (environment in deployEnvs) {
            if (paramFile.contains(environment)) {
                // Form the corresponding fixture file full path
                fixtureName = (paramFile =~ /cloudformation\/fixtures\/parameters\/(.+).parameters.(.+).json/)
                try {
                    fixtureFile = "cloudformation/fixtures/templates/" + fixtureName[0][1] + ".json"
                    if (!changedFixtureFiles.contains(fixtureFile)) {
                        changedFixtureFiles << fixtureFile
                    }
                } catch(Exception ex) {
                    // Just print a warning here so the job does not die
                    println("Could not derive fixture name from $paramFile")
                }
            }
        }
    }
    // Need to destroy object or we hit a non-serializable error at next sh call
    // https://stackoverflow.com/questions/40454558/
    fixtureName = null

    println("Changed fixtures: $changedFixtureFiles")

    if (!changedFixtureFiles) {
        println("No fixtures changed, nothing to do")
        return
    }

    String fileRestrictOpts = changedFixtureFiles.collect { "-f $it" }.join(" ")
    // add hanging indent to the fixture list for the message
    String changedFixtureMessageList = changedFixtureFiles.collect{ '      ' + it }.join('\n')
    String deployEnvOpts = deployEnvs.collect{ '-e ' + it }.join(' ')

    def serviceRegistryContent = readFile("${WORKSPACE}/service_registry.json")
    def serviceRegistry = new JsonSlurperClassic().parseText(serviceRegistryContent)
    int errors = 0

    String fileName
    String fixture
    List<String> autoDeployEnvs
    List<String> envsToDeploy
    String deployData

    for (fixtureFilePath in changedFixtureFiles) {
        fileName = fixtureFilePath.substring(fixtureFilePath.lastIndexOf('/')+1)
        fixture = fileName.take(fileName.lastIndexOf('.'))

        autoDeployEnvs = serviceRegistry.get("fixtures").get(fixture).get("auto_deploy_environments")

        if (autoDeployEnvs == null) {
            println("No auto deployment environments specified for $fixture")
            continue
        }

        envsToDeploy = deployEnvs.intersect(autoDeployEnvs)
        for (env in envsToDeploy) {
            println("Started deployment of $fixture fixture on $env environment")
            int fixtureDeployStatus = sh(
                script: "ef-cf ${fixtureFilePath} ${env} --commit --poll",
                returnStatus: true
            )
            deployData = [
                "Commit: $gitCommit",
                "Fixture: $fixture",
                "Environment: $env",
                "$BUILD_URL"
            ]
            if (fixtureDeployStatus != 0) {
                String failMessage = [
                    "*Failed fixture deployment*",
                    "$deployData"
                ].join('\n')
                slackSend(channel: channel, message: failMessage, color: "danger")
                errors += 1
            } else {
                String successMessage = [
                    "*Finished fixture deployment*",
                    "$deployData"
                ].join('\n')
                slackSend(channel: channel, message: successMessage, color:"good")
            }
        }
    }

    if (errors > 0) {
        error("Failed fixture deployment")
    }
}
