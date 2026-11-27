package com.mointer.cloud_health_monitor.service;

import com.mointer.cloud_health_monitor.entity.HealthCheckRecord;
import com.mointer.cloud_health_monitor.entity.MonitoredService;
import com.mointer.cloud_health_monitor.repository.HealthCheckRecordRepository;
import com.mointer.cloud_health_monitor.repository.MonitoredServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Coordinates automated periodic health checks for all enabled registered monitors.
 * Maintains consecutive failures, uptime percentages, downtime duration, and incident lifecycle.
 */
@Service
@EnableScheduling
public class ScheduledMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledMonitoringService.class);

    private final MonitoredServiceRepository serviceRepository;
    private final HealthCheckRecordRepository recordRepository;
    private final UrlHealthMonitoringEngine monitoringEngine;
    private final IncidentManagementService incidentService;
    private final PrometheusMetricsExportService metricsExportService;
    private final UptimeAnalyticsService analyticsService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final Set<Long> inFlightChecks = ConcurrentHashMap.newKeySet();

    public ScheduledMonitoringService(MonitoredServiceRepository serviceRepository,
                                      HealthCheckRecordRepository recordRepository,
                                      UrlHealthMonitoringEngine monitoringEngine,
                                      IncidentManagementService incidentService,
                                      PrometheusMetricsExportService metricsExportService,
                                      UptimeAnalyticsService analyticsService) {
        this.serviceRepository = serviceRepository;
        this.recordRepository = recordRepository;
        this.monitoringEngine = monitoringEngine;
        this.incidentService = incidentService;
        this.metricsExportService = metricsExportService;
        this.analyticsService = analyticsService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Application started: Initializing background health monitoring...");
        triggerAllChecks();
    }

    /**
     * Periodic background tick running every 10 seconds.
     * Evaluates which enabled monitors are due for their next check based on their individual interval.
     * Paused/disabled monitors are strictly excluded.
     */
    @Scheduled(fixedDelay = 10000)
    public void runSchedulerTick() {
        Instant now = Instant.now();
        List<MonitoredService> services = serviceRepository.findByEnabledTrue();

        for (MonitoredService svc : services) {
            if (isDueForCheck(svc, now) && inFlightChecks.add(svc.getId())) {
                executorService.submit(() -> {
                    try {
                        checkService(svc.getId());
                    } finally {
                        inFlightChecks.remove(svc.getId());
                    }
                });
            }
        }
    }

    public void triggerAllChecks() {
        List<MonitoredService> services = serviceRepository.findByEnabledTrue();
        for (MonitoredService svc : services) {
            if (inFlightChecks.add(svc.getId())) {
                executorService.submit(() -> {
                    try {
                        checkService(svc.getId());
                    } finally {
                        inFlightChecks.remove(svc.getId());
                    }
                });
            }
        }
    }

    /**
     * Synchronously execute a health check for a specific service by ID.
     */
    @Transactional
    public HealthCheckRecord checkService(Long serviceId) {
        MonitoredService service = serviceRepository.findById(serviceId).orElse(null);
        if (service == null) return null;

        String prevStatus = service.getCurrentStatus();
        UrlHealthMonitoringEngine.CheckExecutionResult result = monitoringEngine.executeCheck(service.getUrl());

        // Update basic service state and metrics
        service.setCurrentStatus(result.status());
        service.setLastCheckedAt(result.checkedAt());
        service.setLastResponseTimeMs(result.responseTimeMs());
        service.setLastStatusCode(result.httpStatusCode());
        service.setDnsStatus(result.dnsStatus());
        service.setDnsLookupTimeMs(result.dnsLookupTimeMs());
        service.setConnectionStatus(result.connectionStatus());
        service.setSslValid(result.sslValid());
        service.setSslDaysRemaining(result.sslDaysRemaining());
        service.setSslExpiryDate(result.sslExpiryDate());
        service.setSslIssuer(result.sslIssuer());
        service.setRedirectCount(result.redirectCount());
        service.setFinalUrl(result.finalUrl());
        service.setErrorMessage(result.errorMessage());

        // Track consecutive failures, downtime duration, and last success/failure timestamps
        if ("UP".equals(result.status())) {
            service.setConsecutiveFailures(0);
            service.setLastSuccessfulCheckAt(result.checkedAt());
            service.setDowntimeDurationSeconds(0L);
        } else {
            service.setConsecutiveFailures(service.getConsecutiveFailures() + 1);
            service.setLastFailedCheckAt(result.checkedAt());
            if (service.getLastSuccessfulCheckAt() != null) {
                long downtimeSec = Duration.between(service.getLastSuccessfulCheckAt(), result.checkedAt()).getSeconds();
                service.setDowntimeDurationSeconds(Math.max(0L, downtimeSec));
            }
        }

        // Record history in repository
        HealthCheckRecord record = new HealthCheckRecord(
                service.getId(),
                service.getName(),
                result.status(),
                result.httpStatusCode(),
                result.responseTimeMs(),
                result.checkedAt(),
                result.errorMessage(),
                result.dnsStatus(),
                result.dnsLookupTimeMs(),
                result.connectionStatus(),
                result.sslValid(),
                result.sslDaysRemaining(),
                result.sslExpiryDate(),
                result.redirectCount(),
                result.finalUrl()
        );
        record = recordRepository.save(record);

        // Update real-time uptime percentage
        double uptime = analyticsService.calculateStatistics(service.getId()).uptime24h();
        service.setUptimePercentage(uptime);
        serviceRepository.save(service);

        // Trigger incident management lifecycle
        incidentService.handleStatusChange(service, prevStatus, result.status(), result.errorMessage());

        // Update Prometheus gauges and counters
        metricsExportService.updateMetrics(service, record);

        return record;
    }

    private boolean isDueForCheck(MonitoredService service, Instant now) {
        if (service.getLastCheckedAt() == null) return true;
        long elapsedSeconds = now.getEpochSecond() - service.getLastCheckedAt().getEpochSecond();
        return elapsedSeconds >= service.getMonitoringInterval();
    }
}
