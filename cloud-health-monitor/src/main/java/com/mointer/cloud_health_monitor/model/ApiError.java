package com.mointer.cloud_health_monitor.model;

import java.time.Instant;

/**
 * Uniform error envelope returned by the global exception handler.
 */
public record ApiError(
        int     status,
        String  error,
        String  message,
        Instant timestamp
) {
    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, Instant.now());
    }
}
