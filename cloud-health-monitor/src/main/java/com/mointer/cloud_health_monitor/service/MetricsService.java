package com.mointer.cloud_health_monitor.service;

import com.mointer.cloud_health_monitor.model.MetricsResponse;
import com.mointer.cloud_health_monitor.model.ServiceEntry;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.util.Collection;

/**
 * Aggregates real runtime statistics for the GET /metrics endpoint.
 */
@Service
public class MetricsService {

    private final ServiceRegistry    serviceRegistry;
    private final HealthCheckService healthCheckService;

    public MetricsService(ServiceRegistry serviceRegistry,
                          HealthCheckService healthCheckService) {
        this.serviceRegistry    = serviceRegistry;
        this.healthCheckService = healthCheckService;
    }

    public MetricsResponse buildMetrics() {
        Collection<ServiceEntry> all = serviceRegistry.all();

        int  total   = all.size();
        long up      = all.stream().filter(e -> "UP".equals(e.getStatus())).count();
        long down    = all.stream().filter(e -> "DOWN".equals(e.getStatus())).count();
        long unknown = all.stream().filter(e -> "UNKNOWN".equals(e.getStatus())).count();

        long totalChecks     = healthCheckService.getTotalChecks();
        long successChecks   = healthCheckService.getSuccessfulChecks();
        long totalResponseMs = healthCheckService.getTotalResponseTimeMs();

        Double avgResponseMs = (successChecks > 0)
                ? (double) totalResponseMs / successChecks
                : null;

        // JVM uptime from MXBean (milliseconds → seconds)
        long uptimeSec = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

        return new MetricsResponse(
                total, up, down, unknown,
                totalChecks, avgResponseMs,
                uptimeSec
        );
    }
}
