package com.ellation.jenkins.exception

class InvalidRepositoryException extends RuntimeException {
    InvalidRepositoryException(String message) {
        super(message)
    }
}
