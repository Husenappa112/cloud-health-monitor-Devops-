package com.mointer.cloud_health_monitor.exception;

/**
 * Thrown when a requested service name does not exist in the registry.
 */
public class ServiceNotFoundException extends RuntimeException {
    public ServiceNotFoundException(String name) {
        super("Service not found: " + name);
    }
}
