package com.ellation.github.exception

class InvalidUrlException extends GithubRuntimeException {
    InvalidUrlException(String message) {
        super(message)
    }
}
