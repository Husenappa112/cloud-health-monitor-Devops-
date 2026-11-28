package com.mointer.cloud_health_monitor.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity representing an active threshold alert rule.
 * Evaluates website availability, response times, SSL validity, and error rates.
 */
@Entity
@Table(name = "alert_rules")
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "monitor_id")
    private Long monitorId; // null indicates applies to all monitors

    @Column(name = "monitor_name", length = 100)
    private String monitorName;

    @Column(name = "metric_type", nullable = false, length = 50)
    private String metricType; // WEBSITE_DOWN, RESPONSE_TIME, UPTIME_PERCENTAGE, SSL_EXPIRY, DNS_FAILURE, HTTP_5XX, CPU_USAGE, MEMORY_USAGE

    @Column(nullable = false, length = 20)
    private String comparator = "GREATER_THAN"; // GREATER_THAN, LESS_THAN, EQUALS

    @Column(name = "threshold_value", nullable = false)
    private Double thresholdValue;

    @Column(name = "evaluation_window_minutes")
    private Integer evaluationWindowMinutes = 5;

    @Column(name = "consecutive_checks")
    private Integer consecutiveChecks = 2;

    @Column(nullable = false, length = 20)
    private String severity = "CRITICAL"; // CRITICAL, HIGH, MEDIUM, LOW, INFO

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "notification_channels", length = 100)
    private String notificationChannels = "IN_APP"; // IN_APP, EMAIL, WEBHOOK

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public AlertRule() {}

    public AlertRule(String name, Long monitorId, String monitorName, String metricType, String comparator,
                     Double thresholdValue, String severity) {
        this.name = name;
        this.monitorId = monitorId;
        this.monitorName = monitorName;
        this.metricType = metricType;
        this.comparator = comparator;
        this.thresholdValue = thresholdValue;
        this.severity = severity;
        this.enabled = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getMonitorId() { return monitorId; }
    public void setMonitorId(Long monitorId) { this.monitorId = monitorId; }

    public String getMonitorName() { return monitorName; }
    public void setMonitorName(String monitorName) { this.monitorName = monitorName; }

    public String getMetricType() { return metricType; }
    public void setMetricType(String metricType) { this.metricType = metricType; }

    public String getComparator() { return comparator; }
    public void setComparator(String comparator) { this.comparator = comparator; }

    public Double getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(Double thresholdValue) { this.thresholdValue = thresholdValue; }

    public Integer getEvaluationWindowMinutes() { return evaluationWindowMinutes; }
    public void setEvaluationWindowMinutes(Integer evaluationWindowMinutes) { this.evaluationWindowMinutes = evaluationWindowMinutes; }

    public Integer getConsecutiveChecks() { return consecutiveChecks; }
    public void setConsecutiveChecks(Integer consecutiveChecks) { this.consecutiveChecks = consecutiveChecks; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public boolean isEnabled() { return Boolean.TRUE.equals(enabled); }
    public void setEnabled(Boolean enabled) { this.enabled = enabled != null ? enabled : true; }

    public String getNotificationChannels() { return notificationChannels; }
    public void setNotificationChannels(String notificationChannels) { this.notificationChannels = notificationChannels; }

    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
