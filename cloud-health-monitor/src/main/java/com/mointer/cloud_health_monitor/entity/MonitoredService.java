package com.mointer.cloud_health_monitor.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity representing a registered monitored service/URL with comprehensive health metrics.
 */
@Entity
@Table(name = "monitored_services", indexes = {
        @Index(name = "idx_service_name", columnList = "name", unique = true),
        @Index(name = "idx_service_status", columnList = "currentStatus"),
        @Index(name = "idx_service_url", columnList = "url")
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

    @Column(name = "dns_status", length = 30)
    private String dnsStatus;

    @Column(name = "dns_lookup_time_ms")
    private Long dnsLookupTimeMs;

    @Column(name = "connection_status", length = 50)
    private String connectionStatus;

    @Column(name = "ssl_valid")
    private Boolean sslValid;

    @Column(name = "ssl_days_remaining")
    private Long sslDaysRemaining;

    @Column(name = "ssl_expiry_date")
    private Instant sslExpiryDate;

    @Column(name = "ssl_issuer", length = 255)
    private String sslIssuer;

    @Column(name = "redirect_count")
    private Integer redirectCount;

    @Column(name = "final_url", length = 1024)
    private String finalUrl;

    @Column(name = "last_successful_check_at")
    private Instant lastSuccessfulCheckAt;

    @Column(name = "last_failed_check_at")
    private Instant lastFailedCheckAt;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures = 0;

    @Column(name = "uptime_percentage")
    private Double uptimePercentage = 100.0;

    @Column(name = "downtime_duration_seconds")
    private Long downtimeDurationSeconds = 0L;

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
        this.uptimePercentage = 100.0;
        this.consecutiveFailures = 0;
        this.downtimeDurationSeconds = 0L;
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

    public String getDnsStatus() {
        return dnsStatus;
    }

    public void setDnsStatus(String dnsStatus) {
        this.dnsStatus = dnsStatus;
    }

    public Long getDnsLookupTimeMs() {
        return dnsLookupTimeMs;
    }

    public void setDnsLookupTimeMs(Long dnsLookupTimeMs) {
        this.dnsLookupTimeMs = dnsLookupTimeMs;
    }

    public String getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(String connectionStatus) {
        this.connectionStatus = connectionStatus;
    }

    public Boolean getSslValid() {
        return sslValid;
    }

    public void setSslValid(Boolean sslValid) {
        this.sslValid = sslValid;
    }

    public Long getSslDaysRemaining() {
        return sslDaysRemaining;
    }

    public void setSslDaysRemaining(Long sslDaysRemaining) {
        this.sslDaysRemaining = sslDaysRemaining;
    }

    public Instant getSslExpiryDate() {
        return sslExpiryDate;
    }

    public void setSslExpiryDate(Instant sslExpiryDate) {
        this.sslExpiryDate = sslExpiryDate;
    }

    public String getSslIssuer() {
        return sslIssuer;
    }

    public void setSslIssuer(String sslIssuer) {
        this.sslIssuer = sslIssuer;
    }

    public Integer getRedirectCount() {
        return redirectCount;
    }

    public void setRedirectCount(Integer redirectCount) {
        this.redirectCount = redirectCount;
    }

    public String getFinalUrl() {
        return finalUrl;
    }

    public void setFinalUrl(String finalUrl) {
        this.finalUrl = finalUrl;
    }

    public Instant getLastSuccessfulCheckAt() {
        return lastSuccessfulCheckAt;
    }

    public void setLastSuccessfulCheckAt(Instant lastSuccessfulCheckAt) {
        this.lastSuccessfulCheckAt = lastSuccessfulCheckAt;
    }

    public Instant getLastFailedCheckAt() {
        return lastFailedCheckAt;
    }

    public void setLastFailedCheckAt(Instant lastFailedCheckAt) {
        this.lastFailedCheckAt = lastFailedCheckAt;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(int consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    public Double getUptimePercentage() {
        return uptimePercentage;
    }

    public void setUptimePercentage(Double uptimePercentage) {
        this.uptimePercentage = uptimePercentage;
    }

    public Long getDowntimeDurationSeconds() {
        return downtimeDurationSeconds;
    }

    public void setDowntimeDurationSeconds(Long downtimeDurationSeconds) {
        this.downtimeDurationSeconds = downtimeDurationSeconds;
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
