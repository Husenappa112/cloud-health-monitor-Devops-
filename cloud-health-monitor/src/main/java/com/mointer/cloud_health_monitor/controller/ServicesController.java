package com.mointer.cloud_health_monitor.controller;

import com.mointer.cloud_health_monitor.exception.ValidationException;
import com.mointer.cloud_health_monitor.model.HealthCheckResult;
import com.mointer.cloud_health_monitor.model.ServiceRegistrationRequest;
import com.mointer.cloud_health_monitor.model.ServiceResponse;
import com.mointer.cloud_health_monitor.service.HealthCheckService;
import com.mointer.cloud_health_monitor.service.ServiceRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for service lifecycle and per-service health checks.
 *
 * <ul>
 *   <li>GET  /services                – list all registered services
 *   <li>POST /services                – register a new service
 *   <li>GET  /services/{name}         – get a single service's details
 *   <li>DELETE /services/{name}       – remove a service
 *   <li>GET  /services/{name}/health  – perform (and cache) a real health check
 *   <li>POST /services/{name}/health  – force re-check
 * </ul>
 */
@RestController
@RequestMapping("/services")
public class ServicesController {

    private final ServiceRegistry    serviceRegistry;
    private final HealthCheckService healthCheckService;

    public ServicesController(ServiceRegistry serviceRegistry,
                              HealthCheckService healthCheckService) {
        this.serviceRegistry    = serviceRegistry;
        this.healthCheckService = healthCheckService;
    }

    // ── GET /services ─────────────────────────────────────────────────────────

    @GetMapping
    public List<ServiceResponse> listServices() {
        return serviceRegistry.all().stream()
                .map(ServiceResponse::from)
                .toList();
    }

    // ── POST /services ────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ServiceResponse> registerService(
            @RequestBody ServiceRegistrationRequest request) {

        validateRequest(request);

        var entry = serviceRegistry.register(request.name(), request.url());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ServiceResponse.from(entry));
    }

    // ── GET /services/{name} ──────────────────────────────────────────────────

    @GetMapping("/{name}")
    public ServiceResponse getService(@PathVariable String name) {
        return ServiceResponse.from(serviceRegistry.getByName(name));
    }

    // ── DELETE /services/{name} ───────────────────────────────────────────────

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteService(@PathVariable String name) {
        serviceRegistry.delete(name);
    }

    // ── GET /services/{name}/health ───────────────────────────────────────────

    @GetMapping("/{name}/health")
    public HealthCheckResult checkHealth(@PathVariable String name) {
        return healthCheckService.check(name);
    }

    // ── POST /services/{name}/health (force re-check) ─────────────────────────

    @PostMapping("/{name}/health")
    public HealthCheckResult recheckHealth(@PathVariable String name) {
        return healthCheckService.check(name);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void validateRequest(ServiceRegistrationRequest req) {
        if (req == null) {
            throw new ValidationException("Request body is required");
        }
        if (req.name() == null || req.name().isBlank()) {
            throw new ValidationException("Service name is required");
        }
        if (req.url() == null || req.url().isBlank()) {
            throw new ValidationException("Service URL is required");
        }
        String url = req.url().trim().toLowerCase();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new ValidationException(
                    "Service URL must start with http:// or https://");
        }
    }
}
