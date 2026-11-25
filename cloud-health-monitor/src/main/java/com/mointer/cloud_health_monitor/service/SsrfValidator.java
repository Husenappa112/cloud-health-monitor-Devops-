package com.mointer.cloud_health_monitor.service;

import com.mointer.cloud_health_monitor.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Server-Side Request Forgery (SSRF) validation filter.
 *
 * <p>Restricts monitored URLs to public, routable internet destinations.
 * Blocks:
 * <ul>
 *   <li>Non-HTTP/HTTPS schemes (e.g., file://, ftp://, gopher://)
 *   <li>Loopback addresses (127.0.0.0/8, ::1)
 *   <li>Cloud metadata addresses (169.254.169.254, fe80::/10)
 *   <li>Private RFC 1918 subnets (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16)
 *   <li>Multicast and broadcast addresses
 *   <li>Explicit localhost and internal names
 * </ul>
 */
@Component
public class SsrfValidator {

    private static final Logger log = LoggerFactory.getLogger(SsrfValidator.class);

    @Value("${monitor.allow-private-ips:false}")
    private boolean allowPrivateIps;

    public void validateUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ValidationException("URL cannot be empty");
        }

        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (Exception e) {
            throw new ValidationException("Invalid URL syntax: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new ValidationException("Only http and https protocols are permitted: " + scheme);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ValidationException("URL must specify a valid host name");
        }

        // Check if private IPs are explicitly allowed (e.g., test profiles)
        if (allowPrivateIps) {
            return;
        }

        String lowerHost = host.toLowerCase(Locale.ROOT);
        if (lowerHost.equals("localhost") || lowerHost.endsWith(".localhost") ||
                lowerHost.endsWith(".local") || lowerHost.endsWith(".internal")) {
            throw new ValidationException("Access to local or internal domain names is blocked for security: " + host);
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (isRestrictedAddress(addr)) {
                    throw new ValidationException("URL resolves to a restricted private or internal IP address: " + addr.getHostAddress());
                }
            }
        } catch (UnknownHostException e) {
            // Let the DNS inspector handle resolution during actual check if domain is just unresolvable,
            // but if it cannot be resolved during validation, log debug and allow registration.
            log.debug("Host {} not resolvable at validation time: {}", host, e.getMessage());
        }
    }

    /**
     * Checks if an IP address belongs to loopback, link-local, private, or metadata ranges.
     */
    public boolean isRestrictedAddress(InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress()) {
            return true;
        }

        if (addr.isSiteLocalAddress()) { // RFC 1918 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16
            return true;
        }

        if (addr.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = addr.getAddress();

        // IPv4-specific checks
        if (bytes.length == 4) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;

            // 127.0.0.0/8 (Loopback)
            if (b0 == 127) return true;

            // 10.0.0.0/8 (Private)
            if (b0 == 10) return true;

            // 172.16.0.0/12 (Private 172.16.0.0 - 172.31.255.255)
            if (b0 == 172 && (b1 >= 16 && b1 <= 31)) return true;

            // 192.168.0.0/16 (Private)
            if (b0 == 192 && b1 == 168) return true;

            // 169.254.0.0/16 (Link Local / Cloud Metadata e.g. 169.254.169.254)
            if (b0 == 169 && b1 == 254) return true;

            // 0.0.0.0/8
            if (b0 == 0) return true;
        }

        return false;
    }

    public void setAllowPrivateIps(boolean allowPrivateIps) {
        this.allowPrivateIps = allowPrivateIps;
    }
}
