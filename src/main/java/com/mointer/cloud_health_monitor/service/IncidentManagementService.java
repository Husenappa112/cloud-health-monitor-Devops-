package com.mointer.cloud_health_monitor.service;

import com.mointer.cloud_health_monitor.entity.Incident;
import com.mointer.cloud_health_monitor.entity.MonitoredService;
import com.mointer.cloud_health_monitor.repository.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Manages incident creation, auto-recovery, and full lifecycle tracking (OPEN -> ACKNOWLEDGED -> RESOLVED).
 */
@Service
public class IncidentManagementService {

    private static final Logger log = LoggerFactory.getLogger(IncidentManagementService.class);

    private final IncidentRepository incidentRepository;

    public IncidentManagementService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    @Transactional
    public void handleStatusChange(MonitoredService service, String previousStatus,
                                   String newStatus, String errorMessage) {
        if ("DOWN".equals(newStatus)) {
            // Check if there is already an active (OPEN or ACKNOWLEDGED) incident
            Optional<Incident> activeIncident = incidentRepository
                    .findTopByServiceIdAndStatusInOrderByStartedAtDesc(service.getId(), List.of("OPEN", "ACKNOWLEDGED"));

            if (activeIncident.isEmpty()) {
                String type = determineIncidentType(errorMessage);
                String severity = "CRITICAL";
                String title = service.getName() + " Outage: " + type;
                Incident incident = new Incident(service.getId(), service.getName(), type, errorMessage, severity, title);
                incidentRepository.save(incident);
                log.warn("Created OPEN incident #{} for service '{}': [{}] {}",
                        incident.getId(), service.getName(), type, errorMessage);
            } else {
                Incident incident = activeIncident.get();
                incident.setLastOccurrence(Instant.now());
                incidentRepository.save(incident);
            }
        } else if ("DEGRADED".equals(newStatus)) {
            // Check if there is already an active incident
            Optional<Incident> activeIncident = incidentRepository
                    .findTopByServiceIdAndStatusInOrderByStartedAtDesc(service.getId(), List.of("OPEN", "ACKNOWLEDGED"));

            if (activeIncident.isEmpty()) {
                String type = determineIncidentType(errorMessage);
                String severity = "WARNING";
                String title = service.getName() + " Degraded Performance: " + type;
                Incident incident = new Incident(service.getId(), service.getName(), type, errorMessage, severity, title);
                incidentRepository.save(incident);
                log.warn("Created DEGRADED incident #{} for service '{}': [{}] {}",
                        incident.getId(), service.getName(), type, errorMessage);
            } else {
                Incident incident = activeIncident.get();
                incident.setLastOccurrence(Instant.now());
                incidentRepository.save(incident);
            }
        } else if ("UP".equals(newStatus)) {
            // Check if there is an active incident to resolve
            Optional<Incident> activeIncident = incidentRepository
                    .findTopByServiceIdAndStatusInOrderByStartedAtDesc(service.getId(), List.of("OPEN", "ACKNOWLEDGED"));

            if (activeIncident.isPresent()) {
                Incident incident = activeIncident.get();
                incident.resolve("Service automatically recovered to UP status.");
                incidentRepository.save(incident);
                log.info("Resolved incident #{} for service '{}'. Duration: {} ms",
                        incident.getId(), service.getName(), incident.getDurationMs());
            }
        }
    }

    private String determineIncidentType(String errorMsg) {
        if (errorMsg == null) return "OUTAGE";
        String lower = errorMsg.toLowerCase();
        if (lower.contains("timeout") || lower.contains("timed out")) return "TIMEOUT";
        if (lower.contains("ssl") || lower.contains("cert")) return "SSL_ERROR";
        if (lower.contains("dns")) return "DNS_FAILURE";
        if (lower.contains("ssrf")) return "SECURITY_BLOCKED";
        if (lower.contains("slow") || lower.contains("latency")) return "HIGH_LATENCY";
        if (lower.contains("http error") || lower.contains("status")) return "HTTP_FAILURE";
        return "CONNECTION_FAILURE";
    }
}
