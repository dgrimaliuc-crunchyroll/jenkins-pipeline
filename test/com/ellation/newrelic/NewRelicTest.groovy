package com.ellation.newrelic

import groovy.json.JsonSlurper
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

class NewRelicTest extends GroovyTestCase {
    void testGetApplicationId() {
        String applicationPrefix = 'alpha0-playheads'
        int applicationID = 42
        String applicationIDsResponse = """
        {
            "applications": [
                {
                    "id": 90700805,
                    "name": "${applicationPrefix}.int"
                },
                {
                    "id": ${applicationID},
                    "name": "${applicationPrefix}"
                },
                {
                    "id": 59826774,
                    "name": "${applicationPrefix}.internal"
                }

            ]
        }"""

        def server = new MockWebServer()
        server.enqueue(new MockResponse().setBody(applicationIDsResponse))
        def apiKey = "randomApiKey"
        server.start()
        def newRelic = new NewRelic(
            apiKey: apiKey,
            baseURL: server.url("").toString().replaceAll('/$', ''))

        def appID =  newRelic.getApplicationId("alpha0-playheads")

        RecordedRequest request = server.takeRequest()
        HttpUrl url = request.getRequestUrl()
        Headers headers = request.getHeaders()

        assertEquals(appID, applicationID)
        assertEquals("/applications.json", url.encodedPath())
        assertEquals("$applicationPrefix", url.queryParameter("filter[name]"))
        assertEquals(apiKey, headers.get("X-Api-Key"))
    }

    void testGetApplicationIdMissingApp() {
        String applicationPrefix = 'alpha0-playheads'
        String applicationIDsResponse = """
        {
            "applications": [
                {
                    "id": 90700805,
                    "name": "${applicationPrefix}.int"
                },
                {
                    "id": 59826774,
                    "name": "${applicationPrefix}.internal"
                }

            ]
        }"""

        def server = new MockWebServer()
        server.enqueue(new MockResponse().setBody(applicationIDsResponse))
        def apiKey = "randomApiKey"
        server.start()
        def newRelic = new NewRelic(
            apiKey: apiKey,
            baseURL: server.url("").toString().replaceAll('/$', ''))

        def appID =  newRelic.getApplicationId("alpha0-playheads")

        RecordedRequest request = server.takeRequest()
        HttpUrl url = request.getRequestUrl()
        Headers headers = request.getHeaders()

        assertEquals(0, appID)
    }

    void testGetApplicationIdFail() {
        def server = new MockWebServer()
        server.enqueue(new MockResponse().setBody("hello cookie").setResponseCode(401))
        def apiKey = "randomApiKey"
        server.start()
        def newRelic = new NewRelic(
            apiKey: apiKey,
            baseURL: server.url("").toString().replaceAll('/$', ''))

        shouldFail (ScriptException) {
            def appID =  newRelic.getApplicationId("alpha0-playheads")
        }
    }

    void testPublishDeployForAppID() {
        def appID = 42
        def apiKey = "randomApiKey"
        def version = "abcdefg"
        def server = new MockWebServer()
        server.enqueue(new MockResponse())
        server.start()
        def newRelic = new NewRelic(
            apiKey: apiKey,
            baseURL: server.url("").toString().replaceAll('/$', ''))

        newRelic.publishDeployForAppID(appID, version)

        RecordedRequest request = server.takeRequest()
        assertEquals("/applications/${appID}/deployments.json", request.getPath())
        assertEquals("application/json", request.getHeader("Content-Type"))

        String body = request.getBody().readUtf8()
        def jsonSlurper = new JsonSlurper()
        def requestPayload = jsonSlurper.parseText(body)
        assertEquals(version, requestPayload?.deployment?.revision)
    }

    void testPublishDeployForAppIDFail() {
        def appID = 42
        def apiKey = "randomApiKey"
        def version = "abcdefg"
        def server = new MockWebServer()
        def failResponses = [101, 199, 0, 300, 400, 401]
        for (response in failResponses) {
            server.enqueue(new MockResponse().setResponseCode(response))
        }
        server.start()
        def newRelic = new NewRelic(
            apiKey: apiKey,
            baseURL: server.url("").toString().replaceAll('/$', ''))

        for (response in failResponses) {
            // can't add meaningful output with the failed params
            shouldFail(ScriptException) {
                newRelic.publishDeployForAppID(appID, version)
            }
        }
    }

    void testPublishVersion() {
        def applicationID = 42
        def appName = "playheads"
        String applicationIDsResponse = """
        {
            "applications": [
                {
                    "id": 90700805,
                    "name": "hello.int"
                },
                {
                    "id": ${applicationID},
                    "name": "${appName}"
                },
                {
                    "id": 59826774,
                    "name": "random_app.internal"
                }

            ]
        }"""
        def apiKey = "randomApiKey"
        def version = "abcdefg"

        def server = new MockWebServer()
        server.enqueue(new MockResponse().setBody(applicationIDsResponse))
        server.enqueue(new MockResponse())
        server.start()
        def newRelic = new NewRelic(
            apiKey: apiKey,
            baseURL: server.url("").toString().replaceAll('/$', ''))

        newRelic.publishVersion(appName, version)

        RecordedRequest request = server.takeRequest()
        HttpUrl url = request.getRequestUrl()
        assertEquals("/applications.json", url.encodedPath())
        assertEquals("${appName}", url.queryParameter("filter[name]"))

        Headers headers = request.getHeaders()
        assertEquals(apiKey, headers.get("X-Api-Key"))

        RecordedRequest request2 = server.takeRequest()
        String body = request2.getBody().readUtf8()
        assertEquals("/applications/${applicationID}/deployments.json", request2.getPath())
        assertEquals("application/json", request2.getHeader("Content-Type"))

        def jsonSlurper = new JsonSlurper()
        def requestPayload = jsonSlurper.parseText(body)
        assertEquals(version, requestPayload?.deployment?.revision)
    }

    void testPublishVersionEmptyResponse() {
        def appName = "playheads"
        def apiKey = "randomApiKey"
        def version = "abcdefg"

        def server = new MockWebServer()
        server.enqueue(new MockResponse())
        server.start()
        def newRelic = new NewRelic(
            apiKey: apiKey,
            baseURL: server.url("").toString().replaceAll('/$', ''))

        shouldFail(ScriptException) {
            newRelic.publishVersion(appName, version)
        }
    }
}
