package com.mointer.cloud_health_monitor.model;

import java.time.Instant;

/**
 * Immutable response returned by GET /services/{name}/health.
 */
public record HealthCheckResult(
        String  name,
        String  url,
        String  status,
        Integer httpStatus,
        Long    responseTimeMs,
        Instant checkedAt
) {}
