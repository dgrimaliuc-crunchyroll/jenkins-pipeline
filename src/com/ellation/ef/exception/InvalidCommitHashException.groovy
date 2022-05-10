package com.ellation.ef.exception

class InvalidCommitHashException extends RuntimeException {
    InvalidCommitHashException(String message) {
        super(message)
    }
}
