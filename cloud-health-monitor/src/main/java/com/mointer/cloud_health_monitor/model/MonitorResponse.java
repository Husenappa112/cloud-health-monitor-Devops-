package com.mointer.cloud_health_monitor.model;

import com.mointer.cloud_health_monitor.entity.MonitoredService;
import java.time.Instant;

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
        Long sslDaysRemaining,
        String sslIssuer,
        Long dnsLookupTimeMs,
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
                entity.getSslDaysRemaining(),
                entity.getSslIssuer(),
                entity.getDnsLookupTimeMs(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
