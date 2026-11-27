package com.mointer.cloud_health_monitor.model;

import com.mointer.cloud_health_monitor.entity.MonitoredService;
import java.time.Instant;

/**
 * Data transfer object representing a monitored URL with all 14 availability and performance metrics.
 */
public record MonitorResponse(
        Long id,
        String name,
        String url,
        int monitoringInterval,
        boolean enabled,
        String currentStatus,
        Instant lastCheckedAt,
        Long lastResponseTimeMs,
        Integer lastStatusCode,
        String dnsStatus,
        Long dnsLookupTimeMs,
        String connectionStatus,
        Boolean sslValid,
        Long sslDaysRemaining,
        Instant sslExpiryDate,
        String sslIssuer,
        Integer redirectCount,
        String finalUrl,
        Instant lastSuccessfulCheckAt,
        Instant lastFailedCheckAt,
        int consecutiveFailures,
        Double uptimePercentage,
        Long downtimeDurationSeconds,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static MonitorResponse from(MonitoredService entity) {
        if (entity == null) return null;
        return new MonitorResponse(
                entity.getId(),
                entity.getName(),
                entity.getUrl(),
                entity.getMonitoringInterval(),
                entity.isEnabled(),
                entity.getCurrentStatus(),
                entity.getLastCheckedAt(),
                entity.getLastResponseTimeMs(),
                entity.getLastStatusCode(),
                entity.getDnsStatus(),
                entity.getDnsLookupTimeMs(),
                entity.getConnectionStatus(),
                entity.getSslValid(),
                entity.getSslDaysRemaining(),
                entity.getSslExpiryDate(),
                entity.getSslIssuer(),
                entity.getRedirectCount(),
                entity.getFinalUrl(),
                entity.getLastSuccessfulCheckAt(),
                entity.getLastFailedCheckAt(),
                entity.getConsecutiveFailures(),
                entity.getUptimePercentage(),
                entity.getDowntimeDurationSeconds(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
