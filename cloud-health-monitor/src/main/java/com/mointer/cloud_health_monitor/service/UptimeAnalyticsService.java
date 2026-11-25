package com.mointer.cloud_health_monitor.service;

import com.mointer.cloud_health_monitor.repository.HealthCheckRecordRepository;
import com.mointer.cloud_health_monitor.repository.IncidentRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Calculates uptime percentage and latency metrics over 24h, 7d, and 30d windows.
 */
@Service
public class UptimeAnalyticsService {

    private final HealthCheckRecordRepository recordRepository;
    private final IncidentRepository incidentRepository;

    public record ServiceStatistics(
            Long serviceId,
            double uptime24h,
            double uptime7d,
            double uptime30d,
            double avgResponseTime24h,
            long minResponseTime24h,
            long maxResponseTime24h,
            long totalChecks24h,
            long openIncidents,
            long totalIncidents
    ) {}

    public UptimeAnalyticsService(HealthCheckRecordRepository recordRepository,
                                  IncidentRepository incidentRepository) {
        this.recordRepository = recordRepository;
        this.incidentRepository = incidentRepository;
    }

    public ServiceStatistics calculateStatistics(Long serviceId) {
        Instant now = Instant.now();
        Instant t24h = now.minus(Duration.ofHours(24));
        Instant t7d  = now.minus(Duration.ofDays(7));
        Instant t30d = now.minus(Duration.ofDays(30));

        double u24h = calculateUptime(serviceId, t24h);
        double u7d  = calculateUptime(serviceId, t7d);
        double u30d = calculateUptime(serviceId, t30d);

        Double avg24h = recordRepository.findAvgResponseTimeMsAfter(serviceId, t24h);
        Long min24h   = recordRepository.findMinResponseTimeMsAfter(serviceId, t24h);
        Long max24h   = recordRepository.findMaxResponseTimeMsAfter(serviceId, t24h);
        long total24h = recordRepository.countByServiceIdAndCheckedAtAfter(serviceId, t24h);

        long openIncidents = incidentRepository.findByServiceIdOrderByStartedAtDesc(serviceId).stream()
                .filter(i -> "OPEN".equals(i.getStatus())).count();
        long totalIncidents = incidentRepository.findByServiceIdOrderByStartedAtDesc(serviceId).size();

        return new ServiceStatistics(
                serviceId,
                u24h,
                u7d,
                u30d,
                avg24h != null ? Math.round(avg24h * 10.0) / 10.0 : 0.0,
                min24h != null ? min24h : 0L,
                max24h != null ? max24h : 0L,
                total24h,
                openIncidents,
                totalIncidents
        );
    }

    public Map<String, Object> calculateSystemOverview() {
        Instant now = Instant.now();
        Instant t24h = now.minus(Duration.ofHours(24));

        long totalChecks = recordRepository.count();
        long openIncidents = incidentRepository.countByStatus("OPEN");
        long resolvedIncidents = incidentRepository.countByStatus("RESOLVED");

        Map<String, Object> map = new HashMap<>();
        map.put("totalChecks", totalChecks);
        map.put("openIncidents", openIncidents);
        map.put("resolvedIncidents", resolvedIncidents);
        return map;
    }

    private double calculateUptime(Long serviceId, Instant after) {
        long total = recordRepository.countByServiceIdAndCheckedAtAfter(serviceId, after);
        if (total == 0) return 100.0;

        long upCount = recordRepository.countByServiceIdAndStatusAndCheckedAtAfter(serviceId, "UP", after);
        long degradedCount = recordRepository.countByServiceIdAndStatusAndCheckedAtAfter(serviceId, "DEGRADED", after);

        double percentage = ((upCount + degradedCount) * 100.0) / total;
        return Math.round(percentage * 100.0) / 100.0;
    }
}
