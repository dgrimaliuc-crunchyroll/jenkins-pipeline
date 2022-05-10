import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurper

/**
 * Convert JSON String to JSON object
 * @param jsonString
 * @return Map<String, Object> json object representation of String
 */
@NonCPS
Map<String, Object> stringToJson(String jsonString) {
    JsonSlurper slurper = new JsonSlurper()
    try {
        return slurper.parseText(jsonString)
    } catch (groovy.json.JsonException exception) {
        error(exception.message)
    }
}
