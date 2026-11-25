package com.mointer.cloud_health_monitor.service;

import com.mointer.cloud_health_monitor.entity.Incident;
import com.mointer.cloud_health_monitor.entity.MonitoredService;
import com.mointer.cloud_health_monitor.repository.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Manages incident creation, auto-recovery, and lifecycle tracking.
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
            // Check if there is already an OPEN incident
            Optional<Incident> openIncident = incidentRepository
                    .findTopByServiceIdAndStatusOrderByStartedAtDesc(service.getId(), "OPEN");

            if (openIncident.isEmpty()) {
                String type = determineIncidentType(errorMessage);
                Incident incident = new Incident(service.getId(), service.getName(), type, errorMessage);
                incidentRepository.save(incident);
                log.warn("Created OPEN incident #{} for service '{}': [{}] {}",
                        incident.getId(), service.getName(), type, errorMessage);
            }
        } else if ("UP".equals(newStatus)) {
            // Check if there is an active incident to resolve
            Optional<Incident> openIncident = incidentRepository
                    .findTopByServiceIdAndStatusOrderByStartedAtDesc(service.getId(), "OPEN");

            if (openIncident.isPresent()) {
                Incident incident = openIncident.get();
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
        if (lower.contains("http error") || lower.contains("status")) return "HTTP_FAILURE";
        return "CONNECTION_FAILURE";
    }
}
