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
            return isRestrictedIpv4(bytes[0] & 0xFF, bytes[1] & 0xFF);
        }

        // IPv6-specific checks
        if (bytes.length == 16) {
            // IPv4-mapped IPv6 address (::ffff:w.x.y.z)
            boolean isIpv4Mapped = true;
            for (int i = 0; i < 10; i++) {
                if (bytes[i] != 0) { isIpv4Mapped = false; break; }
            }
            if (isIpv4Mapped && (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF) {
                return isRestrictedIpv4(bytes[12] & 0xFF, bytes[13] & 0xFF);
            }

            // IPv6 Unique Local Address (fc00::/7 -> fc00:: to fdff::)
            int firstByte = bytes[0] & 0xFF;
            if ((firstByte & 0xFE) == 0xFC) {
                return true;
            }

            // IPv6 Link-Local (fe80::/10)
            int secondByte = bytes[1] & 0xFF;
            if (firstByte == 0xFE && (secondByte & 0xC0) == 0x80) {
                return true;
            }
        }

        return false;
    }

    private boolean isRestrictedIpv4(int b0, int b1) {
        // 127.0.0.0/8 (Loopback)
        if (b0 == 127) return true;

        // 10.0.0.0/8 (Private RFC 1918)
        if (b0 == 10) return true;

        // 172.16.0.0/12 (Private RFC 1918 172.16.0.0 - 172.31.255.255)
        if (b0 == 172 && (b1 >= 16 && b1 <= 31)) return true;

        // 192.168.0.0/16 (Private RFC 1918)
        if (b0 == 192 && b1 == 168) return true;

        // 169.254.0.0/16 (Link Local / Cloud Metadata e.g. 169.254.169.254)
        if (b0 == 169 && b1 == 254) return true;

        // 0.0.0.0/8 (Broadcast/Current network)
        if (b0 == 0) return true;

        return false;
    }

    public void setAllowPrivateIps(boolean allowPrivateIps) {
        this.allowPrivateIps = allowPrivateIps;
    }
}
