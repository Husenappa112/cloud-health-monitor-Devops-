package com.mointer.cloud_health_monitor.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Historical record of an individual health check event with all 14 captured metrics.
 */
@Entity
@Table(name = "health_check_records", indexes = {
        @Index(name = "idx_record_service_id", columnList = "serviceId"),
        @Index(name = "idx_record_checked_at", columnList = "checkedAt"),
        @Index(name = "idx_record_svc_time", columnList = "serviceId, checkedAt")
})
public class HealthCheckRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Column(nullable = false, length = 20)
    private String status; // UP, DOWN, DEGRADED

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

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

    @Column(name = "redirect_count")
    private Integer redirectCount;

    @Column(name = "final_url", length = 1024)
    private String finalUrl;

    public HealthCheckRecord() {}

    public HealthCheckRecord(Long serviceId, String serviceName, String status,
                             Integer httpStatusCode, Long responseTimeMs,
                             Instant checkedAt, String errorMessage,
                             Long dnsLookupTimeMs, Long sslDaysRemaining) {
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.status = status;
        this.httpStatusCode = httpStatusCode;
        this.responseTimeMs = responseTimeMs;
        this.checkedAt = checkedAt != null ? checkedAt : Instant.now();
        this.errorMessage = errorMessage;
        this.dnsLookupTimeMs = dnsLookupTimeMs;
        this.sslDaysRemaining = sslDaysRemaining;
    }

    public HealthCheckRecord(Long serviceId, String serviceName, String status,
                             Integer httpStatusCode, Long responseTimeMs,
                             Instant checkedAt, String errorMessage,
                             String dnsStatus, Long dnsLookupTimeMs,
                             String connectionStatus, Boolean sslValid,
                             Long sslDaysRemaining, Instant sslExpiryDate,
                             Integer redirectCount, String finalUrl) {
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.status = status;
        this.httpStatusCode = httpStatusCode;
        this.responseTimeMs = responseTimeMs;
        this.checkedAt = checkedAt != null ? checkedAt : Instant.now();
        this.errorMessage = errorMessage;
        this.dnsStatus = dnsStatus;
        this.dnsLookupTimeMs = dnsLookupTimeMs;
        this.connectionStatus = connectionStatus;
        this.sslValid = sslValid;
        this.sslDaysRemaining = sslDaysRemaining;
        this.sslExpiryDate = sslExpiryDate;
        this.redirectCount = redirectCount;
        this.finalUrl = finalUrl;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getHttpStatusCode() {
        return httpStatusCode;
    }

    public void setHttpStatusCode(Integer httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    public Long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(Long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(Instant checkedAt) {
        this.checkedAt = checkedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
}
