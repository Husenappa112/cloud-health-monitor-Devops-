package com.mointer.cloud_health_monitor.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * GET /health — basic application-level health indicator.
 * Returns the same response as the original implementation so existing
 * integrations are not broken.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status",      "UP",
                "application", "Cloud Health Monitor"
        );
    }
}
