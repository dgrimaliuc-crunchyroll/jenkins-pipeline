package com.ellation.deploy.exception

class NoValidCommitException extends RuntimeException {
    NoValidCommitException(String message) {
        super(message)
    }
}
