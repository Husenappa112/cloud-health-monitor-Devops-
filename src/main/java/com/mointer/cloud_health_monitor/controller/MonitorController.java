package com.mointer.cloud_health_monitor.controller;

import com.mointer.cloud_health_monitor.entity.HealthCheckRecord;
import com.mointer.cloud_health_monitor.entity.Incident;
import com.mointer.cloud_health_monitor.entity.MonitoredService;
import com.mointer.cloud_health_monitor.exception.ServiceAlreadyExistsException;
import com.mointer.cloud_health_monitor.exception.ServiceNotFoundException;
import com.mointer.cloud_health_monitor.exception.ValidationException;
import com.mointer.cloud_health_monitor.model.CreateMonitorRequest;
import com.mointer.cloud_health_monitor.model.MonitorResponse;
import com.mointer.cloud_health_monitor.model.UpdateMonitorRequest;
import com.mointer.cloud_health_monitor.repository.HealthCheckRecordRepository;
import com.mointer.cloud_health_monitor.repository.IncidentRepository;
import com.mointer.cloud_health_monitor.repository.MonitoredServiceRepository;
import com.mointer.cloud_health_monitor.service.ScheduledMonitoringService;
import com.mointer.cloud_health_monitor.service.SsrfValidator;
import com.mointer.cloud_health_monitor.service.UptimeAnalyticsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for URL monitor registration, management, and health status reporting.
 */
@RestController
@RequestMapping("/monitors")
public class MonitorController {

    private final MonitoredServiceRepository serviceRepository;
    private final HealthCheckRecordRepository recordRepository;
    private final IncidentRepository incidentRepository;
    private final ScheduledMonitoringService monitoringService;
    private final UptimeAnalyticsService analyticsService;
    private final SsrfValidator ssrfValidator;

    public MonitorController(MonitoredServiceRepository serviceRepository,
                             HealthCheckRecordRepository recordRepository,
                             IncidentRepository incidentRepository,
                             ScheduledMonitoringService monitoringService,
                             UptimeAnalyticsService analyticsService,
                             SsrfValidator ssrfValidator) {
        this.serviceRepository = serviceRepository;
        this.recordRepository = recordRepository;
        this.incidentRepository = incidentRepository;
        this.monitoringService = monitoringService;
        this.analyticsService = analyticsService;
        this.ssrfValidator = ssrfValidator;
    }

    // ── POST /monitors ────────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<MonitorResponse> createMonitor(@Valid @RequestBody CreateMonitorRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new ValidationException("Monitor name is required");
        }
        if (req.url() == null || req.url().isBlank()) {
            throw new ValidationException("Target URL is required");
        }
        if (serviceRepository.existsByNameIgnoreCase(req.name().trim())) {
            throw new ServiceAlreadyExistsException(req.name().trim());
        }

        // Validate URL and SSRF controls
        ssrfValidator.validateUrl(req.url().trim());

        int interval = (req.monitoringInterval() != null && req.monitoringInterval() > 0)
                ? req.monitoringInterval() : 60;

        MonitoredService service = new MonitoredService(req.name().trim(), req.url().trim(), interval);
        service = serviceRepository.save(service);

        // Perform immediate health check so caller gets live status right away
        monitoringService.checkService(service.getId());

        // Reload fresh state
        service = serviceRepository.findById(service.getId()).orElse(service);

        return ResponseEntity.status(HttpStatus.CREATED).body(MonitorResponse.from(service));
    }

    // ── GET /monitors ─────────────────────────────────────────────────────────
    @GetMapping
    public List<MonitorResponse> listMonitors() {
        return serviceRepository.findAll().stream()
                .map(MonitorResponse::from)
                .toList();
    }

    // ── GET /monitors/{id} ────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public MonitorResponse getMonitor(@PathVariable Long id) {
        MonitoredService service = serviceRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Monitor with ID " + id));
        return MonitorResponse.from(service);
    }

    // ── PUT /monitors/{id} ────────────────────────────────────────────────────
    @PutMapping("/{id}")
    public MonitorResponse updateMonitor(@PathVariable Long id, @Valid @RequestBody UpdateMonitorRequest req) {
        MonitoredService service = serviceRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Monitor with ID " + id));

        if (req.name() != null && !req.name().isBlank()) {
            if (!service.getName().equalsIgnoreCase(req.name().trim()) &&
                    serviceRepository.existsByNameIgnoreCase(req.name().trim())) {
                throw new ServiceAlreadyExistsException(req.name().trim());
            }
            service.setName(req.name().trim());
        }

        if (req.url() != null && !req.url().isBlank()) {
            ssrfValidator.validateUrl(req.url().trim());
            service.setUrl(req.url().trim());
        }

        if (req.monitoringInterval() != null && req.monitoringInterval() > 0) {
            service.setMonitoringInterval(req.monitoringInterval());
        }

        if (req.enabled() != null) {
            service.setEnabled(req.enabled());
        }

        service = serviceRepository.save(service);
        return MonitorResponse.from(service);
    }

    // ── DELETE /monitors/{id} ─────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMonitor(@PathVariable Long id) {
        if (!serviceRepository.existsById(id)) {
            throw new ServiceNotFoundException("Monitor with ID " + id);
        }
        serviceRepository.deleteById(id);
    }

    // ── POST /monitors/{id}/check (Force Recheck) ─────────────────────────────
    @PostMapping("/{id}/check")
    public HealthCheckRecord forceCheck(@PathVariable Long id) {
        if (!serviceRepository.existsById(id)) {
            throw new ServiceNotFoundException("Monitor with ID " + id);
        }
        return monitoringService.checkService(id);
    }

    // ── GET /monitors/{id}/history ────────────────────────────────────────────
    @GetMapping("/{id}/history")
    public List<HealthCheckRecord> getHistory(@PathVariable Long id) {
        if (!serviceRepository.existsById(id)) {
            throw new ServiceNotFoundException("Monitor with ID " + id);
        }
        return recordRepository.findTop50ByServiceIdOrderByCheckedAtDesc(id);
    }

    // ── GET /monitors/{id}/statistics ─────────────────────────────────────────
    @GetMapping("/{id}/statistics")
    public UptimeAnalyticsService.ServiceStatistics getStatistics(@PathVariable Long id) {
        if (!serviceRepository.existsById(id)) {
            throw new ServiceNotFoundException("Monitor with ID " + id);
        }
        return analyticsService.calculateStatistics(id);
    }

    // ── GET /monitors/{id}/incidents ──────────────────────────────────────────
    @GetMapping("/{id}/incidents")
    public List<Incident> getIncidents(@PathVariable Long id) {
        if (!serviceRepository.existsById(id)) {
            throw new ServiceNotFoundException("Monitor with ID " + id);
        }
        return incidentRepository.findByServiceIdOrderByStartedAtDesc(id);
    }

    // ── GET /monitors/overview ────────────────────────────────────────────────
    @GetMapping("/overview")
    public Map<String, Object> getOverview() {
        Map<String, Object> overview = new HashMap<>(analyticsService.calculateSystemOverview());
        long totalMonitors = serviceRepository.count();
        long upCount = serviceRepository.countByCurrentStatus("UP");
        long downCount = serviceRepository.countByCurrentStatus("DOWN");
        long degradedCount = serviceRepository.countByCurrentStatus("DEGRADED");
        long unknownCount = serviceRepository.countByCurrentStatus("UNKNOWN");

        overview.put("totalMonitors", totalMonitors);
        overview.put("upCount", upCount);
        overview.put("downCount", downCount);
        overview.put("degradedCount", degradedCount);
        overview.put("unknownCount", unknownCount);

        double overallUptime = 100.0;
        if (totalMonitors > 0) {
            overallUptime = Math.round(((double) (upCount + degradedCount) / totalMonitors) * 10000.0) / 100.0;
        }
        overview.put("overallUptimePercentage", overallUptime);

        return overview;
    }
}
