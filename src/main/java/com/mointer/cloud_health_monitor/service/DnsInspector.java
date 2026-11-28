package com.mointer.cloud_health_monitor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Performs DNS lookup timing and verification for monitored endpoints.
 */
@Component
public class DnsInspector {

    private static final Logger log = LoggerFactory.getLogger(DnsInspector.class);

    public record DnsResult(boolean success, long lookupTimeMs, String resolvedIp, String errorMessage) {}

    public DnsResult inspect(String host) {
        if (host == null || host.isBlank()) {
            return new DnsResult(false, 0, null, "Hostname is empty");
        }

        long start = System.nanoTime();
        try {
            InetAddress address = InetAddress.getByName(host);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            return new DnsResult(true, Math.max(1, elapsedMs), address.getHostAddress(), null);
        } catch (UnknownHostException e) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.debug("DNS lookup failed for {}: {}", host, e.getMessage());
            return new DnsResult(false, elapsedMs, null, "DNS resolution failed: unknown host " + host);
        } catch (Exception e) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            return new DnsResult(false, elapsedMs, null, "DNS lookup error: " + e.getMessage());
        }
    }
}
