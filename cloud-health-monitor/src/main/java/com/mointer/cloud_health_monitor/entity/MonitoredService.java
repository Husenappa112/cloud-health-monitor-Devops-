package com.mointer.cloud_health_monitor.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity representing a registered monitored service/URL.
 */
@Entity
@Table(name = "monitored_services", indexes = {
        @Index(name = "idx_service_name", columnList = "name", unique = true),
        @Index(name = "idx_service_status", columnList = "currentStatus")
})
public class MonitoredService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, length = 1024)
    private String url;

    @Column(name = "monitoring_interval", nullable = false)
    private int monitoringInterval = 60; // in seconds

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "current_status", nullable = false, length = 20)
    private String currentStatus = "UNKNOWN";

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_response_time_ms")
    private Long lastResponseTimeMs;

    @Column(name = "last_status_code")
    private Integer lastStatusCode;

    @Column(name = "ssl_days_remaining")
    private Long sslDaysRemaining;

    @Column(name = "ssl_issuer", length = 255)
    private String sslIssuer;

    @Column(name = "dns_lookup_time_ms")
    private Long dnsLookupTimeMs;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public MonitoredService() {}

    public MonitoredService(String name, String url, int monitoringInterval) {
        this.name = name;
        this.url = url;
        this.monitoringInterval = monitoringInterval > 0 ? monitoringInterval : 60;
        this.enabled = true;
        this.currentStatus = "UNKNOWN";
    }

    @PrePersist
    public void onPrePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onPreUpdate() {
        this.updatedAt = Instant.now();
    }

    // ── Getters and Setters ──────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getMonitoringInterval() {
        return monitoringInterval;
    }

    public void setMonitoringInterval(int monitoringInterval) {
        this.monitoringInterval = monitoringInterval;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(String currentStatus) {
        this.currentStatus = currentStatus;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(Instant lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public Long getLastResponseTimeMs() {
        return lastResponseTimeMs;
    }

    public void setLastResponseTimeMs(Long lastResponseTimeMs) {
        this.lastResponseTimeMs = lastResponseTimeMs;
    }

    public Integer getLastStatusCode() {
        return lastStatusCode;
    }

    public void setLastStatusCode(Integer lastStatusCode) {
        this.lastStatusCode = lastStatusCode;
    }

    public Long getSslDaysRemaining() {
        return sslDaysRemaining;
    }

    public void setSslDaysRemaining(Long sslDaysRemaining) {
        this.sslDaysRemaining = sslDaysRemaining;
    }

    public String getSslIssuer() {
        return sslIssuer;
    }

    public void setSslIssuer(String sslIssuer) {
        this.sslIssuer = sslIssuer;
    }

    public Long getDnsLookupTimeMs() {
        return dnsLookupTimeMs;
    }

    public void setDnsLookupTimeMs(Long dnsLookupTimeMs) {
        this.dnsLookupTimeMs = dnsLookupTimeMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
