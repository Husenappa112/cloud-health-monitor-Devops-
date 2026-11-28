package com.mointer.cloud_health_monitor.controller;

import com.mointer.cloud_health_monitor.entity.HealthCheckRecord;
import com.mointer.cloud_health_monitor.entity.MonitoredService;
import com.mointer.cloud_health_monitor.exception.ValidationException;
import com.mointer.cloud_health_monitor.repository.HealthCheckRecordRepository;
import com.mointer.cloud_health_monitor.repository.MonitoredServiceRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
            @RequestParam(defaultValue = "24h") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        Instant[] window = resolveWindow(range, from, to);
        Instant since = window[0];
        Instant until = window[1];

        List<HealthCheckRecord> allInWindow = (monitorId != null)
                ? recordRepository.findByServiceIdAndCheckedAtAfterOrderByCheckedAtAsc(monitorId, since)
                : recordRepository.findByCheckedAtAfterOrderByCheckedAtAsc(since);

        List<HealthCheckRecord> records = allInWindow.stream()
                .filter(r -> !r.getCheckedAt().isAfter(until))
                .toList();

        List<String> timestamps = new ArrayList<>();
        List<Number> values = new ArrayList<>();
        Map<Long, Map<String, Object>> byMonitor = new LinkedHashMap<>();

        for (HealthCheckRecord r : records) {
            Number val = extractMetricValue(r, metric);
            timestamps.add(r.getCheckedAt().toString());
            values.add(val);

            Map<String, Object> mSeries = byMonitor.computeIfAbsent(r.getServiceId(), id -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("monitorId", id);
                m.put("monitorName", r.getServiceName());
                m.put("timestamps", new ArrayList<String>());
                m.put("values", new ArrayList<Number>());
                return m;
            });
            @SuppressWarnings("unchecked")
            List<String> mTs = (List<String>) mSeries.get("timestamps");
            @SuppressWarnings("unchecked")
            List<Number> mVals = (List<Number>) mSeries.get("values");
            mTs.add(r.getCheckedAt().toString());
            mVals.add(val);
        }

        // Handle snapshot/infrastructure metrics when no per-check series is needed
        String upperMetric = metric.toUpperCase();
        if ("JVM_HEAP".equals(upperMetric) || "JVM_THREADS".equals(upperMetric)) {
            MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            long usedMb = memBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
            long maxMb = Math.max(1, memBean.getHeapMemoryUsage().getMax() / (1024 * 1024));
            double val = "JVM_HEAP".equals(upperMetric)
                    ? Math.round((usedMb * 1000.0) / maxMb) / 10.0
                    : threadBean.getThreadCount();
            timestamps = List.of(Instant.now().toString());
            values = List.of(val);
        }

        // Compute latency/value summary statistics
        List<Double> sorted = values.stream().map(Number::doubleValue).sorted().toList();
        Map<String, Object> stats = new LinkedHashMap<>();
        if (!sorted.isEmpty()) {
            double sum = sorted.stream().mapToDouble(Double::doubleValue).sum();
            stats.put("min", Math.round(sorted.get(0) * 10.0) / 10.0);
            stats.put("max", Math.round(sorted.get(sorted.size() - 1) * 10.0) / 10.0);
            stats.put("avg", Math.round((sum / sorted.size()) * 10.0) / 10.0);
            stats.put("p50", percentile(sorted, 50));
            stats.put("p90", percentile(sorted, 90));
            stats.put("p95", percentile(sorted, 95));
            stats.put("p99", percentile(sorted, 99));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metric", metric);
        result.put("range", range);
        result.put("from", since.toString());
        result.put("to", until.toString());
        result.put("timestamps", timestamps);
        result.put("values", values);
        result.put("seriesByMonitor", new ArrayList<>(byMonitor.values()));
        result.put("stats", stats);
        result.put("dataPointCount", values.size());
        return result;
    }

    /**
     * Aggregated distribution metrics (HTTP status codes, failures, latency buckets, percentiles).
     */
    @GetMapping("/distribution")
    public Map<String, Object> getDistribution(
            @RequestParam(required = false) Long monitorId,
            @RequestParam(defaultValue = "24h") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        Instant[] window = resolveWindow(range, from, to);
        Instant since = window[0];
        Instant until = window[1];

        List<HealthCheckRecord> allInWindow = (monitorId != null)
                ? recordRepository.findByServiceIdAndCheckedAtAfterOrderByCheckedAtAsc(monitorId, since)
                : recordRepository.findByCheckedAtAfterOrderByCheckedAtAsc(since);

        List<HealthCheckRecord> records = allInWindow.stream()
                .filter(r -> !r.getCheckedAt().isAfter(until))
                .toList();

        long c2xx = 0, c3xx = 0, c4xx = 0, c5xx = 0, cFailures = 0;
        long b0to100 = 0, b100to300 = 0, b300to1000 = 0, b1000plus = 0;
        List<Double> latencies = new ArrayList<>();

        for (HealthCheckRecord r : records) {
            Integer code = r.getHttpStatusCode();
            if (code == null || code <= 0) {
                cFailures++;
            } else if (code >= 200 && code < 300) {
                c2xx++;
            } else if (code >= 300 && code < 400) {
                c3xx++;
            } else if (code >= 400 && code < 500) {
                c4xx++;
            } else if (code >= 500) {
                c5xx++;
            }

            Long ms = r.getResponseTimeMs();
            if (ms != null) {
                latencies.add(ms.doubleValue());
                if (ms <= 100) b0to100++;
                else if (ms <= 300) b100to300++;
                else if (ms <= 1000) b300to1000++;
                else b1000plus++;
            }
        }

        Collections.sort(latencies);
        Map<String, Object> percentiles = new LinkedHashMap<>();
        percentiles.put("p50", latencies.isEmpty() ? 0 : percentile(latencies, 50));
        percentiles.put("p90", latencies.isEmpty() ? 0 : percentile(latencies, 90));
        percentiles.put("p95", latencies.isEmpty() ? 0 : percentile(latencies, 95));
        percentiles.put("p99", latencies.isEmpty() ? 0 : percentile(latencies, 99));

        Map<String, Long> statusCodes = new LinkedHashMap<>();
        statusCodes.put("2xx", c2xx);
        statusCodes.put("3xx", c3xx);
        statusCodes.put("4xx", c4xx);
        statusCodes.put("5xx", c5xx);
        statusCodes.put("failures", cFailures);

        Map<String, Long> latencyBuckets = new LinkedHashMap<>();
        latencyBuckets.put("0-100ms", b0to100);
        latencyBuckets.put("100-300ms", b100to300);
        latencyBuckets.put("300-1000ms", b300to1000);
        latencyBuckets.put(">1000ms", b1000plus);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("statusCodes", statusCodes);
        result.put("latencyBuckets", latencyBuckets);
        result.put("percentiles", percentiles);
        result.put("totalRecords", records.size());
        return result;
    }

    /**
     * Real 2D Heatmap matrix of average latency by Day-of-Week (Mon-Sun) and Hour-of-Day (00-23 UTC).
     * Never fabricates synthetic latency data.
     */
    @GetMapping("/heatmap")
    public Map<String, Object> getHeatmap(
            @RequestParam(required = false) Long monitorId,
            @RequestParam(defaultValue = "7d") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        Instant[] window = resolveWindow(range, from, to);
        Instant since = window[0];
        Instant until = window[1];

        List<HealthCheckRecord> allInWindow = (monitorId != null)
                ? recordRepository.findByServiceIdAndCheckedAtAfterOrderByCheckedAtAsc(monitorId, since)
                : recordRepository.findByCheckedAtAfterOrderByCheckedAtAsc(since);

        List<HealthCheckRecord> records = allInWindow.stream()
                .filter(r -> !r.getCheckedAt().isAfter(until) && r.getResponseTimeMs() != null)
                .toList();

        List<String> hours = new ArrayList<>();
        for (int h = 0; h < 24; h++) hours.add(String.format("%02d:00", h));
        List<String> days = List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");

        long[][] sums = new long[7][24];
        long[][] counts = new long[7][24];

        for (HealthCheckRecord r : records) {
            ZonedDateTime zdt = r.getCheckedAt().atZone(ZoneOffset.UTC);
            int dayIdx = zdt.getDayOfWeek().getValue() - 1; // 0=Mon .. 6=Sun
            int hourIdx = zdt.getHour();
            sums[dayIdx][hourIdx] += r.getResponseTimeMs();
            counts[dayIdx][hourIdx]++;
        }

        List<List<Object>> data = new ArrayList<>();
        for (int d = 0; d < 7; d++) {
            for (int h = 0; h < 24; h++) {
                if (counts[d][h] > 0) {
                    long avg = Math.round((double) sums[d][h] / counts[d][h]);
                    data.add(List.of(h, d, avg));
                }
            }
        }

        return Map.of(
                "days", days,
                "hours", hours,
                "data", data,
                "totalRecords", records.size()
        );
    }

    /**
     * Historical status timeline per monitor showing real UP / DEGRADED / DOWN checks.
     */
    @GetMapping("/timeline")
    public Map<String, Object> getStatusTimeline(
            @RequestParam(required = false) Long monitorId,
            @RequestParam(defaultValue = "24h") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        Instant[] window = resolveWindow(range, from, to);
        Instant since = window[0];
        Instant until = window[1];

        List<MonitoredService> services = (monitorId != null)
                ? serviceRepository.findById(monitorId).map(List::of).orElse(List.of())
                : serviceRepository.findAll();

        List<HealthCheckRecord> allInWindow = (monitorId != null)
                ? recordRepository.findByServiceIdAndCheckedAtAfterOrderByCheckedAtAsc(monitorId, since)
                : recordRepository.findByCheckedAtAfterOrderByCheckedAtAsc(since);

        Map<Long, List<Map<String, Object>>> grouped = new HashMap<>();
        for (HealthCheckRecord r : allInWindow) {
            if (r.getCheckedAt().isAfter(until)) continue;
            Map<String, Object> pt = new LinkedHashMap<>();
            pt.put("checkedAt", r.getCheckedAt().toString());
            pt.put("status", r.getStatus());
            pt.put("httpStatusCode", r.getHttpStatusCode());
            pt.put("responseTimeMs", r.getResponseTimeMs());
            pt.put("errorMessage", r.getErrorMessage());
            grouped.computeIfAbsent(r.getServiceId(), k -> new ArrayList<>()).add(pt);
        }

        List<Map<String, Object>> timelines = new ArrayList<>();
        for (MonitoredService svc : services) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("monitorId", svc.getId());
            entry.put("monitorName", svc.getName());
            entry.put("currentStatus", svc.getCurrentStatus());
            entry.put("enabled", svc.isEnabled());
            entry.put("checks", grouped.getOrDefault(svc.getId(), List.of()));
            timelines.add(entry);
        }

        return Map.of(
                "from", since.toString(),
                "to", until.toString(),
                "range", range,
                "timelines", timelines
        );
    }

    private Number extractMetricValue(HealthCheckRecord r, String metric) {
        return switch (metric.toUpperCase()) {
            case "RESPONSE_TIME" -> r.getResponseTimeMs() != null ? r.getResponseTimeMs() : 0;
            case "DNS_LOOKUP" -> r.getDnsLookupTimeMs() != null ? r.getDnsLookupTimeMs() : 0;
            case "SSL_DAYS" -> r.getSslDaysRemaining() != null ? r.getSslDaysRemaining() : 0;
            case "UPTIME" -> "UP".equals(r.getStatus()) ? 100 : ("DEGRADED".equals(r.getStatus()) ? 50 : 0);
            case "STATUS_STATE" -> "UP".equals(r.getStatus()) ? 1 : ("DEGRADED".equals(r.getStatus()) ? 0.5 : 0);
            case "CONSECUTIVE_FAILURES" -> "UP".equals(r.getStatus()) ? 0 : 1;
            default -> r.getResponseTimeMs() != null ? r.getResponseTimeMs() : 0;
        };
    }

    private double percentile(List<Double> sorted, int pct) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil((pct / 100.0) * sorted.size()) - 1;
        idx = Math.max(0, Math.min(sorted.size() - 1, idx));
        return Math.round(sorted.get(idx) * 10.0) / 10.0;
    }

    private Instant[] resolveWindow(String range, String from, String to) {
        Instant now = Instant.now();
        if (from != null && !from.isBlank()) {
            try {
                Instant since = Instant.parse(from);
                Instant until = (to != null && !to.isBlank()) ? Instant.parse(to) : now;
                if (since.isAfter(until)) {
                    throw new ValidationException("Invalid date range: 'from' timestamp must be before 'to' timestamp.");
                }
                return new Instant[]{since, until};
            } catch (ValidationException ve) {
                throw ve;
            } catch (Exception e) {
                throw new ValidationException("Invalid ISO-8601 date range format. Expected e.g. 2026-11-28T00:00:00Z.");
            }
        }
        return new Instant[]{calculateSinceInstant(range, now), now};
    }

    private Instant calculateSinceInstant(String range, Instant now) {
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
