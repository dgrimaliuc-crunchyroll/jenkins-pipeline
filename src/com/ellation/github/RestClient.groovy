package com.ellation.github

/**
 * This is an abstraction over the HttpURLConnection
 * So we don't have our code dependent on it
 */
class RestClient implements Serializable {
    String authToken

    String baseUrl

    HttpResponse get(String path) {
        request( "GET", path)
    }

    HttpResponse post(String path, def content = null) {
        request ("POST", path, content)
    }

    HttpResponse put(String path, def content = null) {
        request( "PUT", path, content)
    }

    HttpResponse delete(String path) {
        request("DELETE", path)
    }

    HttpResponse request(String method, String path, def outContent = null) {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + path).openConnection()

        conn.requestMethod = method
        conn.doInput = true
        conn.doOutput = (outContent != null)

        conn.setRequestProperty "Connection", "close"

        if (authToken) {
            conn.setRequestProperty "Authorization", "Bearer " + authToken
        }

        if (outContent != null && !(outContent instanceof Map)) {
            conn.setRequestProperty "Content-Type", "application/json"
        }

        try {
            if (outContent != null) {
                OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream())
                if (outContent instanceof Map) {
                    def queryString = outContent
                        .collect { k, v -> "$k=$v" }
                        .join('&')
                    writer.write(queryString)
                } else {
                    writer.write(outContent)
                }
                writer.flush()
                writer.close()
            }

            int status = conn.responseCode
            String inContent = (status >= 400 ? conn.errorStream : conn.inputStream)?.text

            return new HttpResponse(status:  status, content:  inContent)
        } finally {
            conn.disconnect()
        }
    }
}
