package com.mointer.cloud_health_monitor.controller;

import com.mointer.cloud_health_monitor.model.MetricsResponse;
import com.mointer.cloud_health_monitor.service.MetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /metrics — real runtime monitoring statistics.
 */
@RestController
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/metrics")
    public MetricsResponse metrics() {
        return metricsService.buildMetrics();
    }
}
