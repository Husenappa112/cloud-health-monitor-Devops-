package com.mointer.cloud_health_monitor.service;

import com.mointer.cloud_health_monitor.entity.HealthCheckRecord;
import com.mointer.cloud_health_monitor.entity.MonitoredService;
import com.mointer.cloud_health_monitor.repository.MonitoredServiceRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Exports real-time Micrometer gauges and counters for Prometheus scraping.
 */
@Service
public class PrometheusMetricsExportService {

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<Long, ServiceMetricHolders> serviceMeters = new ConcurrentHashMap<>();

    private static class ServiceMetricHolders {
        final AtomicReference<Double> up = new AtomicReference<>(0.0);
        final AtomicReference<Double> responseTimeSeconds = new AtomicReference<>(0.0);
        final AtomicReference<Double> sslExpiryDays = new AtomicReference<>(-1.0);
        final AtomicReference<Double> dnsLookupSeconds = new AtomicReference<>(0.0);
    }

    public PrometheusMetricsExportService(MeterRegistry meterRegistry,
                                          MonitoredServiceRepository serviceRepository) {
        this.meterRegistry = meterRegistry;

        // System-level summary gauges
        Gauge.builder("monitored_services_total", serviceRepository, r -> (double) r.count())
                .description("Total number of configured URL monitors")
                .register(meterRegistry);

        Gauge.builder("monitored_services_up_count", serviceRepository, r -> (double) r.countByCurrentStatus("UP"))
                .description("Number of URL monitors currently in UP status")
                .register(meterRegistry);

        Gauge.builder("monitored_services_down_count", serviceRepository, r -> (double) r.countByCurrentStatus("DOWN"))
                .description("Number of URL monitors currently in DOWN status")
                .register(meterRegistry);

        Gauge.builder("monitored_services_degraded_count", serviceRepository, r -> (double) r.countByCurrentStatus("DEGRADED"))
                .description("Number of URL monitors currently in DEGRADED status")
                .register(meterRegistry);
    }

    public void updateMetrics(MonitoredService service, HealthCheckRecord record) {
        if (service == null || service.getId() == null) return;

        ServiceMetricHolders holders = serviceMeters.computeIfAbsent(service.getId(), id -> {
            ServiceMetricHolders h = new ServiceMetricHolders();
            String name = service.getName();

            Gauge.builder("monitored_service_up", h.up, AtomicReference::get)
                    .tag("service", name)
                    .description("Service health status (1.0 = UP, 0.5 = DEGRADED, 0.0 = DOWN)")
                    .register(meterRegistry);

            Gauge.builder("monitored_service_response_time_seconds", h.responseTimeSeconds, AtomicReference::get)
                    .tag("service", name)
                    .description("Latest response latency in seconds")
                    .register(meterRegistry);

            Gauge.builder("monitored_service_ssl_expiry_days", h.sslExpiryDays, AtomicReference::get)
                    .tag("service", name)
                    .description("Days remaining until SSL certificate expiration (-1 if not HTTPS)")
                    .register(meterRegistry);

            Gauge.builder("monitored_service_dns_lookup_seconds", h.dnsLookupSeconds, AtomicReference::get)
                    .tag("service", name)
                    .description("Latest DNS lookup duration in seconds")
                    .register(meterRegistry);

            return h;
        });

        // Set status gauge
        double statusVal = switch (record.getStatus()) {
            case "UP" -> 1.0;
            case "DEGRADED" -> 0.5;
            default -> 0.0;
        };
        holders.up.set(statusVal);

        // Latency
        if (record.getResponseTimeMs() != null) {
            holders.responseTimeSeconds.set(record.getResponseTimeMs() / 1000.0);
        }

        // SSL Days
        if (record.getSslDaysRemaining() != null) {
            holders.sslExpiryDays.set(record.getSslDaysRemaining().doubleValue());
        } else {
            holders.sslExpiryDays.set(-1.0);
        }

        // DNS
        if (record.getDnsLookupTimeMs() != null) {
            holders.dnsLookupSeconds.set(record.getDnsLookupTimeMs() / 1000.0);
        }

        // Increment check counter
        Counter.builder("monitored_service_checks_total")
                .tag("service", service.getName())
                .tag("status", record.getStatus())
                .description("Total number of health check executions")
                .register(meterRegistry)
                .increment();
    }
}
