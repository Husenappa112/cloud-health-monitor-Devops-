package com.mointer.cloud_health_monitor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.net.ssl.*;
import java.net.URI;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;

/**
 * Inspects SSL/TLS certificates for HTTPS endpoints: calculates days until expiration,
 * issuer, and identifies expired or invalid certificates.
 */
@Component
public class SslCertificateInspector {

    private static final Logger log = LoggerFactory.getLogger(SslCertificateInspector.class);

    public record SslResult(
            boolean valid,
            Long daysRemaining,
            String issuer,
            Instant expiresAt,
            String errorMessage
    ) {}

    public SslResult inspect(String urlString) {
        if (urlString == null || !urlString.toLowerCase().startsWith("https://")) {
            return new SslResult(true, null, null, null, null);
        }

        try {
            URI uri = URI.create(urlString);
            String host = uri.getHost();
            int port = uri.getPort() != -1 ? uri.getPort() : 443;

            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket socket = (SSLSocket) factory.createSocket(host, port)) {
                socket.setSoTimeout(3000);
                SSLSession session = socket.getSession();
                Certificate[] certs = session.getPeerCertificates();

                if (certs != null && certs.length > 0 && certs[0] instanceof X509Certificate x509) {
                    Instant notAfter = x509.getNotAfter().toInstant();
                    long days = Duration.between(Instant.now(), notAfter).toDays();
                    String issuer = x509.getIssuerX500Principal().getName();

                    // Shorten issuer name if long
                    if (issuer != null && issuer.contains("CN=")) {
                        int idx = issuer.indexOf("CN=");
                        int end = issuer.indexOf(",", idx);
                        issuer = end > idx ? issuer.substring(idx, end) : issuer.substring(idx);
                    }

                    boolean isExpired = days <= 0;
                    String error = isExpired ? "SSL Certificate Expired (" + Math.abs(days) + " days ago)" : null;

                    return new SslResult(!isExpired, days, issuer, notAfter, error);
                }
            }
        } catch (SSLHandshakeException e) {
            log.debug("SSL handshake failed for {}: {}", urlString, e.getMessage());
            return new SslResult(false, 0L, null, null, "SSL Handshake Failed: " + e.getMessage());
        } catch (Exception e) {
            log.debug("SSL check error for {}: {}", urlString, e.getMessage());
            return new SslResult(false, null, null, null, "SSL check error: " + e.getMessage());
        }

        return new SslResult(true, null, null, null, null);
    }
}
