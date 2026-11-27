package com.mointer.cloud_health_monitor.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Historical record of alert trigger and recovery events.
 */
@Entity
@Table(name = "alert_history", indexes = {
        @Index(name = "idx_alert_hist_rule", columnList = "rule_id"),
        @Index(name = "idx_alert_hist_status", columnList = "status"),
        @Index(name = "idx_alert_hist_time", columnList = "triggered_at")
})
public class AlertHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "rule_name", nullable = false, length = 100)
    private String ruleName;

    @Column(name = "monitor_id")
    private Long monitorId;

    @Column(name = "service_name", length = 100)
    private String serviceName;

    @Column(nullable = false, length = 20)
    private String severity = "CRITICAL";

    @Column(nullable = false, length = 20)
    private String status = "FIRING"; // FIRING, ACKNOWLEDGED, RESOLVED

    @Column(name = "trigger_reason", length = 500)
    private String triggerReason;

    @Column(name = "metric_value")
    private Double metricValue;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt = Instant.now();

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "notification_status", length = 100)
    private String notificationStatus = "IN_APP_SENT";

    public AlertHistory() {}

    public AlertHistory(Long ruleId, String ruleName, Long monitorId, String serviceName,
                        String severity, String status, String triggerReason, Double metricValue) {
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.monitorId = monitorId;
        this.serviceName = serviceName;
        this.severity = severity;
        this.status = status;
        this.triggerReason = triggerReason;
        this.metricValue = metricValue;
        this.triggeredAt = Instant.now();
        this.notificationStatus = "DELIVERED_IN_APP";
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public Long getMonitorId() { return monitorId; }
    public void setMonitorId(Long monitorId) { this.monitorId = monitorId; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTriggerReason() { return triggerReason; }
    public void setTriggerReason(String triggerReason) { this.triggerReason = triggerReason; }

    public Double getMetricValue() { return metricValue; }
    public void setMetricValue(Double metricValue) { this.metricValue = metricValue; }

    public Instant getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(Instant triggeredAt) { this.triggeredAt = triggeredAt; }

    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getNotificationStatus() { return notificationStatus; }
    public void setNotificationStatus(String notificationStatus) { this.notificationStatus = notificationStatus; }
}
