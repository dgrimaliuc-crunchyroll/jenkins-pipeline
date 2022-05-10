package com.ellation.github.exception

class InvalidGithubResponse extends GithubRuntimeException {
    InvalidGithubResponse(String message) {
        super(message)
    }
}
