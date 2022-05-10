package com.ellation.newrelic

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class NewRelic implements Serializable {
    String apiKey
    String baseURL = "https://api.newrelic.com/v2"

    /**
    * Grab the application ID from NewRelic by name
    * Return 0 if not found
    * Throws ScriptException on API error
    *
    * @param applicationName appName to search for
    * @return applicationID. 0 if not found
    */
    int getApplicationId(String applicationName) {
        def response = requestAppIds(applicationName)

        for (app in response.applications) {
            if (app.name == applicationName) {
                return app.id
            }
        }
        return 0
    }

    private Map requestAppIds(String applicationName) {
        def applicationEncoded = URLEncoder.encode(applicationName, 'UTF-8')

        def url = "${this.baseURL}/applications.json?exclude_links=true&filter[name]=${applicationEncoded}"
        HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection()
        request.setRequestProperty("Accept", "*/*")
        request.setRequestProperty("X-Api-Key", this.apiKey)
        def responseCode = request.getResponseCode()
        def responseBody = ""
        if (responseCode >= 200  && responseCode < 400) {
            responseBody = request.getInputStream()?.getText()
            if (!responseBody) {
                throw new ScriptException(
                    """Response body empty, expecting JSON
                    | response headers: ${request.getHeaderFields()}""")
            }
        } else {
            throw new ScriptException(
                """Error reading application data from NewRelic
                | response text: ${request.getErrorStream()?.getText()}
                | response headers: ${request.getHeaderFields()}""")
        }

        def jsonSlurper = new JsonSlurper()
        return jsonSlurper.parseText(responseBody)
    }

    /**
    * Post a deployment to NewRelic for an application ID
    * Throws ScriptException on API error
    *
    * @param version version to post for the application
    * @param applicationId the application ID
    */
    def publishDeployForAppID(int applicationId, String version) {
        def message = [
            deployment: [
                revision: "${version}"
            ]
        ]
        def url = "${this.baseURL}/applications/${applicationId}/deployments.json"
        HttpURLConnection request = (HttpURLConnection) new URL(url)
            .openConnection()

        request.setRequestMethod("POST")
        request.setDoOutput(true)
        request.setRequestProperty("Content-type", "application/json")
        request.setRequestProperty("Accept", "*/*")
        request.setRequestProperty("X-Api-Key", this.apiKey)

        def str = JsonOutput.toJson(message).getBytes("UTF-8")
        request.getOutputStream().write(str)

        def responseCode = request.getResponseCode()
        if (responseCode < 200 || responseCode >= 300) {
            throw new ScriptException(
                 "Error publishing deploy to NewRelic |" +
                 "response text: ${request.getErrorStream()?.getText()}," +
                 "response headers: ${request.getHeaderFields()}")
        }
    }

    /**
    * Post a deployment to NewRelic for an application
    * Throws ScriptException if app name not found and on API error
    *
    * @param applicationName the application name
    * @param version version to post for the application
    */
    def publishVersion(String applicationName, String version) {
        def appID = this.getApplicationId(applicationName)
        if (!appID) {
            throw new ScriptException("Could not find application")
        }
        this.publishDeployForAppID(appID, version)
    }
}
