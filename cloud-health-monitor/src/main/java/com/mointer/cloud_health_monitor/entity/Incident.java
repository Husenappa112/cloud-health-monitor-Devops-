package com.mointer.cloud_health_monitor.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Represents an outage, degradation, or security incident associated with a monitored service.
 * Supports full lifecycle: OPEN -> ACKNOWLEDGED -> RESOLVED.
 */
@Entity
@Table(name = "incidents", indexes = {
        @Index(name = "idx_incident_service_id", columnList = "serviceId"),
        @Index(name = "idx_incident_status", columnList = "status"),
        @Index(name = "idx_incident_started_at", columnList = "startedAt")
})
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Column(name = "incident_type", nullable = false, length = 50)
    private String incidentType;

    @Column(nullable = false, length = 20)
    private String severity = "CRITICAL"; // CRITICAL, WARNING, INFO

    @Column(nullable = false, length = 20)
    private String status = "OPEN"; // OPEN, ACKNOWLEDGED, RESOLVED

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_occurrence")
    private Instant lastOccurrence;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "root_cause", length = 1000)
    private String rootCause;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    public Incident() {}

    public Incident(Long serviceId, String serviceName, String incidentType, String rootCause) {
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.incidentType = incidentType;
        this.rootCause = rootCause;
        this.status = "OPEN";
        this.severity = incidentType != null && incidentType.contains("DEGRADED") ? "WARNING" : "CRITICAL";
        this.title = serviceName + " — " + incidentType;
        this.startedAt = Instant.now();
        this.lastOccurrence = this.startedAt;
    }

    public Incident(Long serviceId, String serviceName, String incidentType, String rootCause, String severity, String title) {
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.incidentType = incidentType;
        this.rootCause = rootCause;
        this.status = "OPEN";
        this.severity = severity != null ? severity : "CRITICAL";
        this.title = title != null ? title : (serviceName + " — " + incidentType);
        this.startedAt = Instant.now();
        this.lastOccurrence = this.startedAt;
    }

    public void acknowledge(String note) {
        this.status = "ACKNOWLEDGED";
        this.acknowledgedAt = Instant.now();
        if (note != null && !note.isBlank()) {
            this.rootCause = (this.rootCause != null ? this.rootCause + " | " : "") + "ACK: " + note;
        }
    }

    public void resolve(String resolutionDetails) {
        this.status = "RESOLVED";
        this.resolvedAt = Instant.now();
        if (this.startedAt != null) {
            this.durationMs = this.resolvedAt.toEpochMilli() - this.startedAt.toEpochMilli();
        }
        if (resolutionDetails != null && !resolutionDetails.isBlank()) {
            this.resolutionNotes = resolutionDetails;
            this.rootCause = (this.rootCause != null ? this.rootCause + " | " : "") + resolutionDetails;
        }
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

    public String getIncidentType() {
        return incidentType;
    }

    public void setIncidentType(String incidentType) {
        this.incidentType = incidentType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getLastOccurrence() {
        return lastOccurrence;
    }

    public void setLastOccurrence(Instant lastOccurrence) {
        this.lastOccurrence = lastOccurrence;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(Instant acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }
}
