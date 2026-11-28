package com.mointer.cloud_health_monitor.exception;

/**
 * Thrown when a client tries to register a service name that already exists.
 */
public class ServiceAlreadyExistsException extends RuntimeException {
    public ServiceAlreadyExistsException(String name) {
        super("Service already registered: " + name);
    }
}
