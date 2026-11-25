package com.mointer.cloud_health_monitor.service;

import com.mointer.cloud_health_monitor.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;

/**
 * Core health check engine for verifying URL availability, latency, DNS resolution, and SSL certificates.
 */
@Service
public class UrlHealthMonitoringEngine {

    private static final Logger log = LoggerFactory.getLogger(UrlHealthMonitoringEngine.class);

    private final HttpClient httpClient;
    private final SsrfValidator ssrfValidator;
    private final DnsInspector dnsInspector;
    private final SslCertificateInspector sslInspector;

    @Value("${monitor.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${monitor.request-timeout-ms:5000}")
    private int requestTimeoutMs;

    public record CheckExecutionResult(
            String status,             // UP, DOWN, DEGRADED
            Integer httpStatusCode,
            Long responseTimeMs,
            Instant checkedAt,
            String errorMessage,
            Long dnsLookupTimeMs,
            Long sslDaysRemaining,
            String sslIssuer
    ) {}

    public UrlHealthMonitoringEngine(SsrfValidator ssrfValidator,
                                    DnsInspector dnsInspector,
                                    SslCertificateInspector sslInspector) {
        this.ssrfValidator = ssrfValidator;
        this.dnsInspector = dnsInspector;
        this.sslInspector = sslInspector;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(3000))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public CheckExecutionResult executeCheck(String url) {
        Instant checkTime = Instant.now();

        // 1. SSRF Validation
        try {
            ssrfValidator.validateUrl(url);
        } catch (ValidationException e) {
            return new CheckExecutionResult("DOWN", null, 0L, checkTime,
                    "SSRF Violation: " + e.getMessage(), 0L, null, null);
        }

        URI uri = URI.create(url);
        String host = uri.getHost();

        // 2. DNS Inspection
        DnsInspector.DnsResult dnsResult = dnsInspector.inspect(host);
        if (!dnsResult.success()) {
            return new CheckExecutionResult("DOWN", null, 0L, checkTime,
                    dnsResult.errorMessage(), dnsResult.lookupTimeMs(), null, null);
        }

        // 3. SSL Inspection (if HTTPS)
        SslCertificateInspector.SslResult sslResult = sslInspector.inspect(url);
        if (!sslResult.valid() && sslResult.errorMessage() != null && sslResult.errorMessage().contains("Expired")) {
            return new CheckExecutionResult("DOWN", null, 0L, checkTime,
                    sslResult.errorMessage(), dnsResult.lookupTimeMs(),
                    sslResult.daysRemaining(), sslResult.issuer());
        }

        // 4. HTTP Availability & Latency Check with Safe SSRF-Checked Redirects
        long startTime = System.currentTimeMillis();
        try {
            URI currentUri = uri;
            HttpResponse<Void> response = null;
            int redirectCount = 0;

            while (redirectCount <= 3) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(currentUri)
                        .timeout(Duration.ofMillis(requestTimeoutMs))
                        .GET()
                        .build();

                response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                int statusCode = response.statusCode();

                // If redirect, re-validate destination URL against SSRF policy before following
                if (statusCode >= 300 && statusCode < 400 && response.headers().firstValue("Location").isPresent()) {
                    String loc = response.headers().firstValue("Location").get();
                    URI nextUri = currentUri.resolve(loc);

                    try {
                        ssrfValidator.validateUrl(nextUri.toString());
                    } catch (ValidationException e) {
                        long elapsedMs = System.currentTimeMillis() - startTime;
                        return new CheckExecutionResult("DOWN", statusCode, elapsedMs, checkTime,
                                "SSRF Violation on Redirect: " + e.getMessage(),
                                dnsResult.lookupTimeMs(), sslResult.daysRemaining(), sslResult.issuer());
                    }

                    currentUri = nextUri;
                    redirectCount++;
                } else {
                    break;
                }
            }

            long elapsedMs = System.currentTimeMillis() - startTime;
            int statusCode = response != null ? response.statusCode() : 500;

            String status;
            String errorMsg = null;

            if (statusCode >= 200 && statusCode < 300) {
                if (elapsedMs > 2000) {
                    status = "DEGRADED";
                    errorMsg = "Slow response latency: " + elapsedMs + "ms (> 2000ms threshold)";
                } else if (sslResult.daysRemaining() != null && sslResult.daysRemaining() <= 7) {
                    status = "DEGRADED";
                    errorMsg = "SSL certificate expiring soon (" + sslResult.daysRemaining() + " days remaining)";
                } else {
                    status = "UP";
                }
            } else if (statusCode >= 300 && statusCode < 400) {
                status = "DEGRADED";
                errorMsg = "HTTP Redirect status: " + statusCode;
            } else {
                status = "DOWN";
                errorMsg = "HTTP Error status: " + statusCode;
            }

            return new CheckExecutionResult(status, statusCode, elapsedMs, checkTime,
                    errorMsg, dnsResult.lookupTimeMs(), sslResult.daysRemaining(), sslResult.issuer());

        } catch (HttpTimeoutException e) {
            long elapsedMs = System.currentTimeMillis() - startTime;
            return new CheckExecutionResult("DOWN", null, elapsedMs, checkTime,
                    "Connection timed out (> " + requestTimeoutMs + "ms)",
                    dnsResult.lookupTimeMs(), sslResult.daysRemaining(), sslResult.issuer());
        } catch (Exception e) {
            long elapsedMs = System.currentTimeMillis() - startTime;
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new CheckExecutionResult("DOWN", null, elapsedMs, checkTime,
                    "Connection failure: " + msg,
                    dnsResult.lookupTimeMs(), sslResult.daysRemaining(), sslResult.issuer());
        }
    }
}
