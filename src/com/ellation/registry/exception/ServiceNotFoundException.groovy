package com.ellation.registry.exception

/**
 * Represents a custom exception if a service is not found in the service registry.
 */
class ServiceNotFoundException extends RuntimeException {
    ServiceNotFoundException(String message) {
        super(message)
    }
}
