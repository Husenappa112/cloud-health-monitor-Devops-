package com.mointer.cloud_health_monitor.exception;

/**
 * Thrown when request input fails validation (blank fields, bad URLs, …).
 */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
