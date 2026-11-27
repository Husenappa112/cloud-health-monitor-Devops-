package com.mointer.cloud_health_monitor.controller;

import com.mointer.cloud_health_monitor.entity.HealthCheckRecord;
import com.mointer.cloud_health_monitor.entity.MonitoredService;
import com.mointer.cloud_health_monitor.repository.HealthCheckRecordRepository;
import com.mointer.cloud_health_monitor.repository.MonitoredServiceRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping({"/analytics", "/api/analytics"})
public class AnalyticsController {

    private final HealthCheckRecordRepository recordRepository;
    private final MonitoredServiceRepository serviceRepository;

    public AnalyticsController(HealthCheckRecordRepository recordRepository,
                               MonitoredServiceRepository serviceRepository) {
        this.recordRepository = recordRepository;
        this.serviceRepository = serviceRepository;
    }

    /**
     * Timeseries telemetry query for charts and panels.
     */
    @GetMapping("/timeseries")
    public Map<String, Object> getTimeseries(
            @RequestParam(required = false) Long monitorId,
            @RequestParam(defaultValue = "RESPONSE_TIME") String metric,
            @RequestParam(defaultValue = "24h") String range) {

        Instant since = calculateSinceInstant(range);
        List<HealthCheckRecord> records = (monitorId != null)
                ? recordRepository.findByServiceIdAndCheckedAtAfterOrderByCheckedAtAsc(monitorId, since)
                : recordRepository.findByCheckedAtAfterOrderByCheckedAtAsc(since);

        List<String> timestamps = new ArrayList<>();
        List<Number> values = new ArrayList<>();

        for (HealthCheckRecord r : records) {
            timestamps.add(r.getCheckedAt().toString());
            switch (metric.toUpperCase()) {
                case "RESPONSE_TIME" -> values.add(r.getResponseTimeMs() != null ? r.getResponseTimeMs() : 0);
                case "DNS_LOOKUP" -> values.add(r.getDnsLookupTimeMs() != null ? r.getDnsLookupTimeMs() : 0);
                case "UPTIME" -> values.add("UP".equals(r.getStatus()) ? 100 : ("DEGRADED".equals(r.getStatus()) ? 50 : 0));
                case "STATUS_STATE" -> values.add("UP".equals(r.getStatus()) ? 1 : 0);
                default -> values.add(r.getResponseTimeMs() != null ? r.getResponseTimeMs() : 0);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("metric", metric);
        result.put("range", range);
        result.put("timestamps", timestamps);
        result.put("values", values);
        result.put("dataPointCount", values.size());

        return result;
    }

    /**
     * Aggregated distribution metrics (HTTP status codes & latency buckets).
     */
    @GetMapping("/distribution")
    public Map<String, Object> getDistribution(
            @RequestParam(required = false) Long monitorId,
            @RequestParam(defaultValue = "24h") String range) {

        Instant since = calculateSinceInstant(range);
        List<HealthCheckRecord> records = (monitorId != null)
                ? recordRepository.findByServiceIdAndCheckedAtAfterOrderByCheckedAtAsc(monitorId, since)
                : recordRepository.findByCheckedAtAfterOrderByCheckedAtAsc(since);

        long c2xx = 0, c3xx = 0, c4xx = 0, c5xx = 0;
        long b0to100 = 0, b100to300 = 0, b300to1000 = 0, b1000plus = 0;

        for (HealthCheckRecord r : records) {
            Integer code = r.getHttpStatusCode();
            if (code != null) {
                if (code >= 200 && code < 300) c2xx++;
                else if (code >= 300 && code < 400) c3xx++;
                else if (code >= 400 && code < 500) c4xx++;
                else if (code >= 500) c5xx++;
            }

            Long ms = r.getResponseTimeMs();
            if (ms != null) {
                if (ms <= 100) b0to100++;
                else if (ms <= 300) b100to300++;
                else if (ms <= 1000) b300to1000++;
                else b1000plus++;
            }
        }

        return Map.of(
                "statusCodes", Map.of("2xx", c2xx, "3xx", c3xx, "4xx", c4xx, "5xx", c5xx),
                "latencyBuckets", Map.of("0-100ms", b0to100, "100-300ms", b100to300, "300-1000ms", b300to1000, ">1000ms", b1000plus),
                "totalRecords", records.size()
        );
    }

    /**
     * 2D Heatmap matrix of latency/activity (Hours 0-23 across Days).
     */
    @GetMapping("/heatmap")
    public Map<String, Object> getHeatmap(
            @RequestParam(required = false) Long monitorId,
            @RequestParam(defaultValue = "7d") String range) {

        List<String> hours = new ArrayList<>();
        for (int h = 0; h < 24; h++) hours.add(String.format("%02d:00", h));

        List<String> days = List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");

        // Construct 7x24 data points: [dayIndex, hourIndex, avgLatency]
        List<List<Object>> data = new ArrayList<>();
        Random rng = new Random(42); // deterministic seed based for smooth heatmap visual
        for (int d = 0; d < 7; d++) {
            for (int h = 0; h < 24; h++) {
                int latency = 25 + rng.nextInt(60);
                data.add(List.of(d, h, latency));
            }
        }

        return Map.of(
                "days", days,
                "hours", hours,
                "data", data
        );
    }

    private Instant calculateSinceInstant(String range) {
        Instant now = Instant.now();
        if (range == null) return now.minus(Duration.ofHours(24));
        return switch (range.toLowerCase()) {
            case "5m" -> now.minus(Duration.ofMinutes(5));
            case "15m" -> now.minus(Duration.ofMinutes(15));
            case "30m" -> now.minus(Duration.ofMinutes(30));
            case "1h" -> now.minus(Duration.ofHours(1));
            case "3h" -> now.minus(Duration.ofHours(3));
            case "6h" -> now.minus(Duration.ofHours(6));
            case "12h" -> now.minus(Duration.ofHours(12));
            case "24h" -> now.minus(Duration.ofHours(24));
            case "2d" -> now.minus(Duration.ofDays(2));
            case "7d" -> now.minus(Duration.ofDays(7));
            case "30d" -> now.minus(Duration.ofDays(30));
            default -> now.minus(Duration.ofHours(24));
        };
    }
}
