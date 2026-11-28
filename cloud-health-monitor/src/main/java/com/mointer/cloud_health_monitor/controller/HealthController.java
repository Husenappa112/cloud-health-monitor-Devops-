package com.mointer.cloud_health_monitor.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Health indicator for Cloud Health Monitor backend.
 * Mapped to both /health and /api/health for frontend ping verification and Kubernetes probes.
 */
@RestController
public class HealthController {

    private final Instant startTime = Instant.now();

    @GetMapping({"/health", "/api/health"})
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "application", "Cloud Health Monitor",
                "service", "backend-engine",
                "version", "1.0.0",
                "startedAt", startTime.toString(),
                "timestamp", Instant.now().toString()
        );
    }
}
