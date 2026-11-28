package com.mointer.cloud_health_monitor.model;

/**
 * JSON response for GET /metrics.
 */
public record MetricsResponse(
        int    totalServices,
        long   upServices,
        long   downServices,
        long   unknownServices,
        long   totalChecksPerformed,
        Double averageResponseTimeMs,
        Long   applicationUptimeSeconds
) {}
