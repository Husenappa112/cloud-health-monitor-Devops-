package com.mointer.cloud_health_monitor.service;

import com.mointer.cloud_health_monitor.model.HealthCheckResult;
import com.mointer.cloud_health_monitor.model.ServiceEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Performs real outbound HTTP health checks against registered service URLs.
 *
 * <p>All requests are bounded by {@code monitor.connect-timeout-ms} and
 * {@code monitor.request-timeout-ms} to prevent runaway threads.
 *
 * <p>Only http:// and https:// schemes are permitted; any other scheme (or a
 * blank URL) is rejected immediately with status DOWN.
 */
@Service
public class HealthCheckService {

    private final ServiceRegistry serviceRegistry;
    private final HttpClient      httpClient;

    /** Total number of health checks executed since application start. */
    private final AtomicLong totalChecks = new AtomicLong(0);

    /** Running sum of response times (ms) for UP checks – used for average calc. */
    private final AtomicLong totalResponseTimeMs = new AtomicLong(0);

    /** Number of UP checks (denominator for average). */
    private final AtomicLong successfulChecks = new AtomicLong(0);

    @Value("${monitor.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${monitor.request-timeout-ms:5000}")
    private int requestTimeoutMs;

    public HealthCheckService(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(3000))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Execute a real HTTP GET against the service's registered URL, update
     * the {@link ServiceEntry}, and return an immutable {@link HealthCheckResult}.
     */
    public HealthCheckResult check(String name) {
        ServiceEntry entry = serviceRegistry.getByName(name);
        return performCheck(entry);
    }

    /**
     * Re-checks every registered service (used by the metrics endpoint to keep
     * state fresh when explicitly requested, and by callers who need aggregate data).
     */
    public void checkAll() {
        serviceRegistry.all().forEach(this::performCheck);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private HealthCheckResult performCheck(ServiceEntry entry) {
        String url = entry.getUrl();

        // Validate scheme – only http / https are allowed
        if (!isPermittedUrl(url)) {
            entry.recordHealthCheck("DOWN", null, 0L);
            totalChecks.incrementAndGet();
            return toResult(entry);
        }

        long start = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(requestTimeoutMs))
                    .GET()
                    .build();

            HttpResponse<Void> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.discarding());

            long elapsed   = System.currentTimeMillis() - start;
            int  httpCode  = response.statusCode();
            String status  = (httpCode >= 200 && httpCode < 400) ? "UP" : "DOWN";

            entry.recordHealthCheck(status, httpCode, elapsed);
            totalChecks.incrementAndGet();
            if ("UP".equals(status)) {
                totalResponseTimeMs.addAndGet(elapsed);
                successfulChecks.incrementAndGet();
            }

        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - start;
            entry.recordHealthCheck("DOWN", null, elapsed);
            totalChecks.incrementAndGet();
        }

        return toResult(entry);
    }

    private static boolean isPermittedUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String lower = url.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static HealthCheckResult toResult(ServiceEntry e) {
        return new HealthCheckResult(
                e.getName(),
                e.getUrl(),
                e.getStatus(),
                e.getHttpStatus(),
                e.getResponseTimeMs(),
                e.getCheckedAt()
        );
    }

    // ── Metrics accessors ─────────────────────────────────────────────────────

    public long getTotalChecks()          { return totalChecks.get();          }
    public long getTotalResponseTimeMs()  { return totalResponseTimeMs.get();  }
    public long getSuccessfulChecks()     { return successfulChecks.get();     }
}
# Health check service implemented
