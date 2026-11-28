package com.mointer.cloud_health_monitor.controller;

import com.mointer.cloud_health_monitor.entity.HealthCheckRecord;
import com.mointer.cloud_health_monitor.entity.Incident;
import com.mointer.cloud_health_monitor.entity.MonitoredService;
import com.mointer.cloud_health_monitor.repository.HealthCheckRecordRepository;
import com.mointer.cloud_health_monitor.repository.IncidentRepository;
import com.mointer.cloud_health_monitor.repository.MonitoredServiceRepository;
import com.mointer.cloud_health_monitor.service.UptimeAnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * REST controller for aggregated system overview, statistics, and platform summaries.
 * Accessible under both /dashboard and /api/dashboard, plus /api/events and /api/alerts.
 */
@RestController
@RequestMapping({"/dashboard", "/api/dashboard"})
public class DashboardController {

    private final MonitoredServiceRepository serviceRepository;
    private final HealthCheckRecordRepository recordRepository;
    private final IncidentRepository incidentRepository;
    private final UptimeAnalyticsService analyticsService;

    public DashboardController(MonitoredServiceRepository serviceRepository,
                               HealthCheckRecordRepository recordRepository,
                               IncidentRepository incidentRepository,
                               UptimeAnalyticsService analyticsService) {
        this.serviceRepository = serviceRepository;
        this.recordRepository = recordRepository;
        this.incidentRepository = incidentRepository;
        this.analyticsService = analyticsService;
    }

    @GetMapping({"", "/summary"})
    public Map<String, Object> getDashboardSummary() {
        Map<String, Object> summary = new HashMap<>(analyticsService.calculateSystemOverview());
        long totalMonitors = serviceRepository.count();
        long upCount = serviceRepository.countByCurrentStatus("UP");
        long downCount = serviceRepository.countByCurrentStatus("DOWN");
        long degradedCount = serviceRepository.countByCurrentStatus("DEGRADED");
        long unknownCount = serviceRepository.countByCurrentStatus("UNKNOWN");
        long enabledCount = serviceRepository.countByEnabledTrue();
        long pausedCount = serviceRepository.countByEnabledFalse();

        summary.put("totalMonitors", totalMonitors);
        summary.put("upCount", upCount);
        summary.put("downCount", downCount);
        summary.put("degradedCount", degradedCount);
        summary.put("unknownCount", unknownCount);
        summary.put("enabledCount", enabledCount);
        summary.put("pausedCount", pausedCount);

        double overallUptime = 100.0;
        if (totalMonitors > 0) {
            overallUptime = Math.round(((double) (upCount + degradedCount) / totalMonitors) * 10000.0) / 100.0;
        }
        summary.put("overallUptimePercentage", overallUptime);

        // Calculate average response time across active monitors
        List<MonitoredService> allServices = serviceRepository.findAll();
        double avgLatency = allServices.stream()
                .filter(s -> s.getLastResponseTimeMs() != null)
                .mapToLong(MonitoredService::getLastResponseTimeMs)
                .average()
                .orElse(0.0);
        summary.put("avgResponseTimeMs", Math.round(avgLatency * 10.0) / 10.0);

        long openIncidents = incidentRepository.countByStatus("OPEN");
        long ackIncidents = incidentRepository.countByStatus("ACKNOWLEDGED");
        long resolvedIncidents = incidentRepository.countByStatus("RESOLVED");
        summary.put("openIncidents", openIncidents);
        summary.put("acknowledgedIncidents", ackIncidents);
        summary.put("resolvedIncidents", resolvedIncidents);
        summary.put("activeIncidents", openIncidents + ackIncidents);

        return summary;
    }

    /**
     * Dedicated HTTP Observability metrics aggregating synthetic health check results.
     */
    @GetMapping("/http-insights")
    public Map<String, Object> getHttpInsights() {
        Map<String, Object> map = new HashMap<>();
        List<HealthCheckRecord> recentRecords = recordRepository.findAll(); // or top 1000
        long total = recentRecords.size();

        long count2xx = recentRecords.stream().filter(r -> r.getHttpStatusCode() != null && r.getHttpStatusCode() >= 200 && r.getHttpStatusCode() < 300).count();
        long count3xx = recentRecords.stream().filter(r -> r.getHttpStatusCode() != null && r.getHttpStatusCode() >= 300 && r.getHttpStatusCode() < 400).count();
        long count4xx = recentRecords.stream().filter(r -> r.getHttpStatusCode() != null && r.getHttpStatusCode() >= 400 && r.getHttpStatusCode() < 500).count();
        long count5xx = recentRecords.stream().filter(r -> r.getHttpStatusCode() != null && r.getHttpStatusCode() >= 500).count();
        long successCount = count2xx;
        long failureCount = total - successCount;

        List<Long> latencies = recentRecords.stream()
                .map(HealthCheckRecord::getResponseTimeMs)
                .filter(Objects::nonNull)
                .sorted()
                .toList();

        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long minLatency = latencies.isEmpty() ? 0L : latencies.get(0);
        long maxLatency = latencies.isEmpty() ? 0L : latencies.get(latencies.size() - 1);
        long p50 = getPercentile(latencies, 50);
        long p90 = getPercentile(latencies, 90);
        long p95 = getPercentile(latencies, 95);
        long p99 = getPercentile(latencies, 99);

        long timeouts = recentRecords.stream().filter(r -> r.getConnectionStatus() != null && r.getConnectionStatus().contains("TIMED_OUT")).count();
        long dnsFailures = recentRecords.stream().filter(r -> "FAILED".equalsIgnoreCase(r.getDnsStatus())).count();
        long connFailures = recentRecords.stream().filter(r -> r.getConnectionStatus() != null && r.getConnectionStatus().contains("FAILED")).count();
        long sslFailures = recentRecords.stream().filter(r -> Boolean.FALSE.equals(r.getSslValid())).count();

        map.put("totalChecks", total);
        map.put("successfulChecks", successCount);
        map.put("failedChecks", failureCount);
        map.put("http2xxCount", count2xx);
        map.put("http3xxCount", count3xx);
        map.put("http4xxCount", count4xx);
        map.put("http5xxCount", count5xx);
        map.put("successRatePercent", total > 0 ? Math.round(((double) successCount / total) * 10000.0) / 100.0 : 100.0);
        map.put("failureRatePercent", total > 0 ? Math.round(((double) failureCount / total) * 10000.0) / 100.0 : 0.0);
        map.put("avgResponseTimeMs", Math.round(avgLatency * 10.0) / 10.0);
        map.put("minResponseTimeMs", minLatency);
        map.put("maxResponseTimeMs", maxLatency);
        map.put("p50LatencyMs", p50);
        map.put("p90LatencyMs", p90);
        map.put("p95LatencyMs", p95);
        map.put("p99LatencyMs", p99);
        map.put("timeoutCount", timeouts);
        map.put("dnsFailureCount", dnsFailures);
        map.put("connectionFailureCount", connFailures);
        map.put("sslFailureCount", sslFailures);
        map.put("metricType", "SYNTHETIC_PROBES");
        map.put("telemetryNotice", "These are synthetic HTTP health-check measurements generated by the Cloud Health Monitor engine, not end-user visitor traffic.");

        return map;
    }

    /**
     * Infrastructure telemetry: Returns live metrics for this application's JVM runtime.
     * External targets without authenticated agent are clearly marked as NOT_CONNECTED.
     */
    @GetMapping("/infrastructure")
    public Map<String, Object> getInfrastructureMetrics() {
        Map<String, Object> map = new HashMap<>();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();

        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        double heapUtil = heapMax > 0 ? ((double) heapUsed / heapMax) * 100.0 : 0.0;

        map.put("source", "CLOUD_HEALTH_MONITOR_JVM");
        map.put("status", "CONNECTED");
        map.put("timestamp", Instant.now().toString());
        map.put("uptimeSeconds", uptimeMs / 1000);
        map.put("jvmHeapUsedBytes", heapUsed);
        map.put("jvmHeapMaxBytes", heapMax);
        map.put("jvmHeapUsedMb", Math.round((heapUsed / (1024.0 * 1024.0)) * 10.0) / 10.0);
        map.put("jvmHeapMaxMb", Math.round((heapMax / (1024.0 * 1024.0)) * 10.0) / 10.0);
        map.put("jvmHeapUtilizationPercent", Math.round(heapUtil * 10.0) / 10.0);
        map.put("jvmNonHeapUsedBytes", memoryBean.getNonHeapMemoryUsage().getUsed());
        map.put("availableProcessors", osBean.getAvailableProcessors());
        map.put("systemLoadAverage", osBean.getSystemLoadAverage());
        map.put("activeThreadCount", threadBean.getThreadCount());
        map.put("peakThreadCount", threadBean.getPeakThreadCount());
        map.put("totalStartedThreadCount", threadBean.getTotalStartedThreadCount());
        map.put("externalAgentStatus", "NOT_CONNECTED");
        map.put("architectureNote", "External monitored websites do not expose server-side CPU/RAM/Disk metrics without an authenticated agent. To connect an external host, configure the Prometheus Node Exporter or Cloud Health Agent.");

        return map;
    }

    /**
     * Security insights: Aggregate TLS/SSL and SSRF posture.
     */
    @GetMapping("/security")
    public Map<String, Object> getSecurityInsights() {
        Map<String, Object> map = new HashMap<>();
        List<MonitoredService> services = serviceRepository.findAll();

        long totalMonitors = services.size();
        long httpsCount = services.stream().filter(s -> s.getUrl() != null && s.getUrl().toLowerCase().startsWith("https://")).count();
        long sslValidCount = services.stream().filter(s -> Boolean.TRUE.equals(s.getSslValid())).count();
        long sslExpiringSoon = services.stream().filter(s -> s.getSslDaysRemaining() != null && s.getSslDaysRemaining() > 0 && s.getSslDaysRemaining() <= 14).count();
        long sslExpired = services.stream().filter(s -> Boolean.FALSE.equals(s.getSslValid()) || (s.getSslDaysRemaining() != null && s.getSslDaysRemaining() <= 0)).count();

        map.put("totalMonitors", totalMonitors);
        map.put("httpsMonitors", httpsCount);
        map.put("httpMonitors", totalMonitors - httpsCount);
        map.put("sslValidCount", sslValidCount);
        map.put("sslExpiringSoonCount", sslExpiringSoon);
        map.put("sslExpiredCount", sslExpired);
        map.put("ssrfProtectionStatus", "ACTIVE_ENFORCED");
        map.put("ssrfBlockedRanges", List.of("127.0.0.0/8", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.169.254/16", "::1", "fc00::/7", "fe80::/10"));

        return map;
    }

    /**
     * Chronological stream of recent system events (outages, recoveries, degradations, re-checks, pause/resume).
     */
    @GetMapping("/events")
    public List<Map<String, Object>> getRecentEvents(@RequestParam(defaultValue = "30") int limit) {
        List<Map<String, Object>> events = new ArrayList<>();

        // Add incident events
        List<Incident> incidents = incidentRepository.findAllByOrderByStartedAtDesc();
        for (Incident inc : incidents) {
            Map<String, Object> ev = new HashMap<>();
            ev.put("id", "inc-" + inc.getId());
            ev.put("timestamp", inc.getStartedAt());
            ev.put("serviceName", inc.getServiceName());
            ev.put("type", "INCIDENT_" + inc.getStatus());
            ev.put("severity", inc.getSeverity());
            ev.put("message", inc.getTitle() != null ? inc.getTitle() : (inc.getServiceName() + " " + inc.getIncidentType()));
            ev.put("details", inc.getRootCause());
            events.add(ev);

            if (inc.getResolvedAt() != null) {
                Map<String, Object> rev = new HashMap<>();
                rev.put("id", "res-" + inc.getId());
                rev.put("timestamp", inc.getResolvedAt());
                rev.put("serviceName", inc.getServiceName());
                rev.put("type", "INCIDENT_RESOLVED");
                rev.put("severity", "INFO");
                rev.put("message", inc.getServiceName() + " recovered to operational state");
                rev.put("details", "Resolved after " + (inc.getDurationMs() != null ? (inc.getDurationMs() / 1000) + "s" : "outage"));
                events.add(rev);
            }
        }

        events.sort((a, b) -> ((Instant) b.get("timestamp")).compareTo((Instant) a.get("timestamp")));
        return events.stream().limit(limit).toList();
    }

    /**
     * Active alerts evaluated against live monitoring thresholds.
     */
    @GetMapping("/alerts")
    public List<Map<String, Object>> getActiveAlerts() {
        List<Map<String, Object>> alerts = new ArrayList<>();
        List<MonitoredService> services = serviceRepository.findAll();

        for (MonitoredService svc : services) {
            if ("DOWN".equals(svc.getCurrentStatus())) {
                alerts.add(Map.of(
                        "id", "alert-down-" + svc.getId(),
                        "serviceId", svc.getId(),
                        "serviceName", svc.getName(),
                        "severity", "CRITICAL",
                        "title", "Service Outage Detected",
                        "condition", "Status == DOWN",
                        "activeSince", svc.getLastCheckedAt() != null ? svc.getLastCheckedAt().toString() : Instant.now().toString(),
                        "summary", svc.getErrorMessage() != null ? svc.getErrorMessage() : "Service is unavailable"
                ));
            } else if ("DEGRADED".equals(svc.getCurrentStatus())) {
                alerts.add(Map.of(
                        "id", "alert-degraded-" + svc.getId(),
                        "serviceId", svc.getId(),
                        "serviceName", svc.getName(),
                        "severity", "WARNING",
                        "title", "Service Latency or SSL Warning",
                        "condition", "Latency > 2000ms or SSL Expiring < 7d",
                        "activeSince", svc.getLastCheckedAt() != null ? svc.getLastCheckedAt().toString() : Instant.now().toString(),
                        "summary", svc.getErrorMessage() != null ? svc.getErrorMessage() : "Service is degraded"
                ));
            } else if (svc.getSslDaysRemaining() != null && svc.getSslDaysRemaining() <= 14 && svc.getSslDaysRemaining() > 0) {
                alerts.add(Map.of(
                        "id", "alert-ssl-" + svc.getId(),
                        "serviceId", svc.getId(),
                        "serviceName", svc.getName(),
                        "severity", "WARNING",
                        "title", "SSL Certificate Expiring Soon",
                        "condition", "SSL Days Remaining <= 14",
                        "activeSince", svc.getLastCheckedAt() != null ? svc.getLastCheckedAt().toString() : Instant.now().toString(),
                        "summary", "Certificate expires in " + svc.getSslDaysRemaining() + " days"
                ));
            }
        }
        return alerts;
    }

    private long getPercentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) return 0L;
        int index = (int) Math.ceil((percentile / 100.0) * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }
}
