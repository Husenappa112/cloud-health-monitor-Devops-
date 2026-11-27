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

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for URL monitor registration, management, lifecycle (pause/resume),
 * and availability reporting. Mapped to both /monitors and /api/monitors for dual-API compatibility.
 */
@RestController
@RequestMapping({"/monitors", "/api/monitors"})
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

    // ── Helper: URL Normalization ────────────────────────────────────────────
    public static String normalizeUrl(String inputUrl) {
        if (inputUrl == null || inputUrl.isBlank()) {
            throw new ValidationException("Target URL is required");
        }
        String trimmed = inputUrl.trim();
        if (!trimmed.toLowerCase().startsWith("http://") && !trimmed.toLowerCase().startsWith("https://")) {
            trimmed = "https://" + trimmed;
        }
        try {
            URI uri = URI.create(trimmed);
            String path = uri.getPath();
            // If path is simply "/" and there's no query or fragment, strip trailing slash
            if ("/".equals(path) && uri.getQuery() == null && uri.getFragment() == null) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
        } catch (Exception ignored) {
            // Let downstream SSRF / URI parsing catch malformed URIs
        }
        return trimmed;
    }

    // ── POST /monitors or /api/monitors ───────────────────────────────────────
    @PostMapping
    public ResponseEntity<MonitorResponse> createMonitor(@Valid @RequestBody CreateMonitorRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new ValidationException("Monitor name is required");
        }
        if (req.url() == null || req.url().isBlank()) {
            throw new ValidationException("Target URL is required");
        }

        String normalizedName = req.name().trim();
        String normalizedUrl = normalizeUrl(req.url());

        if (serviceRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new ServiceAlreadyExistsException(normalizedName);
        }
        if (serviceRepository.existsByUrl(normalizedUrl)) {
            throw new ServiceAlreadyExistsException("URL " + normalizedUrl);
        }

        // Validate URL and SSRF controls
        ssrfValidator.validateUrl(normalizedUrl);

        int interval = (req.monitoringInterval() != null && req.monitoringInterval() > 0)
                ? req.monitoringInterval() : 60;

        MonitoredService service = new MonitoredService(normalizedName, normalizedUrl, interval);
        service = serviceRepository.save(service);

        // Perform immediate health check so caller gets live status right away
        monitoringService.checkService(service.getId());

        // Reload fresh state
        service = serviceRepository.findById(service.getId()).orElse(service);

        return ResponseEntity.status(HttpStatus.CREATED).body(MonitorResponse.from(service));
    }

    // ── GET /monitors or /api/monitors ────────────────────────────────────────
    @GetMapping
    public List<MonitorResponse> listMonitors() {
        return serviceRepository.findAll().stream()
                .map(MonitorResponse::from)
                .toList();
    }

    // ── GET /monitors/{id} or /api/monitors/{id} ──────────────────────────────
    @GetMapping("/{id}")
    public MonitorResponse getMonitor(@PathVariable Long id) {
        MonitoredService service = serviceRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Monitor with ID " + id));
        return MonitorResponse.from(service);
    }

    // ── PUT /monitors/{id} or /api/monitors/{id} ──────────────────────────────
    @PutMapping("/{id}")
    public MonitorResponse updateMonitor(@PathVariable Long id, @Valid @RequestBody UpdateMonitorRequest req) {
        MonitoredService service = serviceRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Monitor with ID " + id));

        if (req.name() != null && !req.name().isBlank()) {
            String newName = req.name().trim();
            if (!service.getName().equalsIgnoreCase(newName) &&
                    serviceRepository.existsByNameIgnoreCase(newName)) {
                throw new ServiceAlreadyExistsException(newName);
            }
            service.setName(newName);
        }

        if (req.url() != null && !req.url().isBlank()) {
            String newUrl = normalizeUrl(req.url());
            if (!service.getUrl().equalsIgnoreCase(newUrl) &&
                    serviceRepository.existsByUrl(newUrl)) {
                throw new ServiceAlreadyExistsException("URL " + newUrl);
            }
            ssrfValidator.validateUrl(newUrl);
            service.setUrl(newUrl);
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

    // ── POST /monitors/{id}/pause or /api/monitors/{id}/pause ────────────────
    @PostMapping("/{id}/pause")
    public MonitorResponse pauseMonitor(@PathVariable Long id) {
        MonitoredService service = serviceRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Monitor with ID " + id));
        service.setEnabled(false);
        service = serviceRepository.save(service);
        return MonitorResponse.from(service);
    }

    // ── POST /monitors/{id}/resume or /api/monitors/{id}/resume ──────────────
    @PostMapping("/{id}/resume")
    public MonitorResponse resumeMonitor(@PathVariable Long id) {
        MonitoredService service = serviceRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Monitor with ID " + id));
        service.setEnabled(true);
        service = serviceRepository.save(service);

        // Perform immediate health check on resumption
        monitoringService.checkService(service.getId());
        service = serviceRepository.findById(service.getId()).orElse(service);

        return MonitorResponse.from(service);
    }

    // ── DELETE /monitors/{id} or /api/monitors/{id} ───────────────────────────
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

    // ── GET /monitors/{id}/uptime and /monitors/{id}/statistics ───────────────
    @GetMapping({"/{id}/uptime", "/{id}/statistics"})
    public UptimeAnalyticsService.ServiceStatistics getStatistics(@PathVariable Long id) {
        if (!serviceRepository.existsById(id)) {
            throw new ServiceNotFoundException("Monitor with ID " + id);
        }
        return analyticsService.calculateStatistics(id);
    }

    // ── GET /monitors/{id}/events and /monitors/{id}/incidents ────────────────
    @GetMapping({"/{id}/events", "/{id}/incidents"})
    public List<Incident> getEvents(@PathVariable Long id) {
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
        long enabledCount = serviceRepository.countByEnabledTrue();
        long pausedCount = serviceRepository.countByEnabledFalse();

        overview.put("totalMonitors", totalMonitors);
        overview.put("upCount", upCount);
        overview.put("downCount", downCount);
        overview.put("degradedCount", degradedCount);
        overview.put("unknownCount", unknownCount);
        overview.put("enabledCount", enabledCount);
        overview.put("pausedCount", pausedCount);

        double overallUptime = 100.0;
        if (totalMonitors > 0) {
            overallUptime = Math.round(((double) (upCount + degradedCount) / totalMonitors) * 10000.0) / 100.0;
        }
        overview.put("overallUptimePercentage", overallUptime);

        return overview;
    }
}
