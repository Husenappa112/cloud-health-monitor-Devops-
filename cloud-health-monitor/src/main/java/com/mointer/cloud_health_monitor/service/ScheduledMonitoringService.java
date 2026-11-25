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

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Coordinates automated periodic health checks for all enabled registered monitors.
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

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final Set<Long> inFlightChecks = ConcurrentHashMap.newKeySet();

    public ScheduledMonitoringService(MonitoredServiceRepository serviceRepository,
                                      HealthCheckRecordRepository recordRepository,
                                      UrlHealthMonitoringEngine monitoringEngine,
                                      IncidentManagementService incidentService,
                                      PrometheusMetricsExportService metricsExportService) {
        this.serviceRepository = serviceRepository;
        this.recordRepository = recordRepository;
        this.monitoringEngine = monitoringEngine;
        this.incidentService = incidentService;
        this.metricsExportService = metricsExportService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Application started: Initializing background health monitoring...");
        triggerAllChecks();
    }

    /**
     * Periodic background tick running every 10 seconds.
     * Evaluates which enabled monitors are due for their next check based on their individual interval.
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

        // Update service state
        service.setCurrentStatus(result.status());
        service.setLastCheckedAt(result.checkedAt());
        service.setLastResponseTimeMs(result.responseTimeMs());
        service.setLastStatusCode(result.httpStatusCode());
        service.setSslDaysRemaining(result.sslDaysRemaining());
        service.setSslIssuer(result.sslIssuer());
        service.setDnsLookupTimeMs(result.dnsLookupTimeMs());
        service.setErrorMessage(result.errorMessage());
        serviceRepository.save(service);

        // Record history
        HealthCheckRecord record = new HealthCheckRecord(
                service.getId(),
                service.getName(),
                result.status(),
                result.httpStatusCode(),
                result.responseTimeMs(),
                result.checkedAt(),
                result.errorMessage(),
                result.dnsLookupTimeMs(),
                result.sslDaysRemaining()
        );
        recordRepository.save(record);

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
