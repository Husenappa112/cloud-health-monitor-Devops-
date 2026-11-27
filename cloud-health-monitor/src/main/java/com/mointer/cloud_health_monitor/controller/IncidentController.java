package com.mointer.cloud_health_monitor.controller;

import com.mointer.cloud_health_monitor.entity.Incident;
import com.mointer.cloud_health_monitor.exception.ServiceNotFoundException;
import com.mointer.cloud_health_monitor.repository.IncidentRepository;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST controller for monitoring outage and degradation incidents.
 * Supports full lifecycle: OPEN -> ACKNOWLEDGED -> RESOLVED, and REOPEN.
 * Mapped to both /incidents and /api/incidents.
 */
@RestController
@RequestMapping({"/incidents", "/api/incidents"})
public class IncidentController {

    private final IncidentRepository incidentRepository;

    public IncidentController(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    @GetMapping
    public List<Incident> listIncidents(@RequestParam(required = false) String status) {
        if (status != null && !status.isBlank()) {
            return incidentRepository.findByStatusOrderByStartedAtDesc(status.toUpperCase());
        }
        return incidentRepository.findAllByOrderByStartedAtDesc();
    }

    @GetMapping("/{id}")
    public Incident getIncident(@PathVariable Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Incident with ID " + id));
    }

    @PostMapping("/{id}/acknowledge")
    public Incident acknowledgeIncident(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Incident with ID " + id));

        String note = (body != null && body.containsKey("note")) ? body.get("note") : "Acknowledged by operator";
        incident.acknowledge(note);
        return incidentRepository.save(incident);
    }

    @PostMapping("/{id}/resolve")
    public Incident resolveIncident(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Incident with ID " + id));

        String note = (body != null && body.containsKey("note")) ? body.get("note") : "Manually resolved via API";
        incident.resolve(note);
        return incidentRepository.save(incident);
    }

    @PostMapping("/{id}/reopen")
    public Incident reopenIncident(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Incident with ID " + id));

        String note = (body != null && body.containsKey("note")) ? body.get("note") : "Reopened by operator";
        incident.setStatus("OPEN");
        incident.setResolvedAt(null);
        incident.setLastOccurrence(Instant.now());
        if (incident.getRootCause() != null) {
            incident.setRootCause(incident.getRootCause() + " | REOPEN: " + note);
        } else {
            incident.setRootCause("REOPEN: " + note);
        }
        return incidentRepository.save(incident);
    }
}
