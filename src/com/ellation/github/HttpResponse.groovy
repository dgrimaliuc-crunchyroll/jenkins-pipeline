package com.ellation.github

/**
 * This is an abstraction over the HttpURLConnection
 * So we don't have our code dependent on it
 */
class HttpResponse {
    Integer status
    String content
}
