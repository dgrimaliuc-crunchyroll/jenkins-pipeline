import com.cloudbees.groovy.cps.NonCPS
import com.ellation.git.GitHelper
import groovy.json.JsonSlurper

/**
 * Simple jsonLint step that examines all modified JSON files on current branch. If any of the files examined have
 * invalid JSON syntax, their names will be outputted with the error and the pipeline will fail.
 *
 * A limitation of this step is that it must be called at the root directory of where
 * the git repo was checked out to, otherwise it won't be able to find the files.
 *
 * To call in JenkinsFile just call it as jsonLint() where the git repo was checked out at.
 *
 * @Exception Error if invalid JSON is found
 * @return nothing
 */
def call() {
    GitHelper gitHelper = new GitHelper(this)
    List<String> changedFiles = gitHelper.getChangedFiles()
    boolean jsonError = false
    for(String fileName in changedFiles) {
        if (!fileName.endsWith(".json")) {
            continue
        }
        String content = readFile(fileName)
        if (!isValidJsonContent(content)) {
            println("File ${fileName} contains invalid JSON.")
            jsonError = true
        }
    }
    if (jsonError) {
        error("JSON files with invalid syntax found.")
    }
}

/**
 * Checks the String content to see if it's a valid JSON or not.
 *
 * NOTE: The nature of NonCPS function within the Jenkins world introduces unintended side effects that
 * you would not expect or find documented anywhere. This function was created as NonCPS due to
 * java.io.NotSerializableException: groovy.json.JsonSlurper exception occurring in a regular function. However,
 * originally the readFile step was in this function too, but it would not work as intended due to the NonCPS
 * annotation. Once that was moved out, this entire function works as intended along with its caller. So try to avoid
 * calling Jenkins steps inside a NonCPS function as you get weird side effects that make no sense with no error
 * messages.
 *
 * Link to why NonCPS need to exist due to Jenkins Paradigm
 * https://stackoverflow.com/questions/37864542/jenkins-pipeline-notserializableexception-groovy-json-internal-lazymap
 *
 * @param content String representation of JSON
 * @return true if valid JSON, false if not
 */
@NonCPS
private boolean isValidJsonContent(String content) {
    JsonSlurper slurper = new JsonSlurper()
    try {
        slurper.parseText(content)
        return true
    } catch (groovy.json.JsonException exception) {
        println(exception.message)
        return false
    }
}
