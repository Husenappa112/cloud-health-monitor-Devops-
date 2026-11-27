package com.mointer.cloud_health_monitor.controller;

import com.mointer.cloud_health_monitor.repository.MonitoredServiceRepository;
import com.mointer.cloud_health_monitor.service.UptimeAnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for aggregated system overview, statistics, and platform summaries.
 * Accessible under both /dashboard and /api/dashboard.
 */
@RestController
@RequestMapping({"/dashboard", "/api/dashboard"})
public class DashboardController {

    private final MonitoredServiceRepository serviceRepository;
    private final UptimeAnalyticsService analyticsService;

    public DashboardController(MonitoredServiceRepository serviceRepository,
                               UptimeAnalyticsService analyticsService) {
        this.serviceRepository = serviceRepository;
        this.analyticsService = analyticsService;
    }

    @GetMapping({"", "/summary"})
    public Map<String, Object> getDashboardSummary() {
        Map<String, Object> summary = new HashMap<>(analyticsService.calculateSystemOverview());
        long totalMonitors = serviceRepository.count();
        long upCount = serviceRepository.countByCurrentStatus("UP");
        long downCount = serviceRepository.countByCurrentStatus("DOWN");
        long degradedCount = serviceRepository.countByCurrentStatus("DEGRADED");
        long unknownCount = serviceRepository.countByCurrentStatus("UNKNOWN");
        long enabledCount = serviceRepository.countByEnabledTrue();
        long pausedCount = serviceRepository.countByEnabledFalse();

        summary.put("totalMonitors", totalMonitors);
        summary.put("upCount", upCount);
        summary.put("downCount", downCount);
        summary.put("degradedCount", degradedCount);
        summary.put("unknownCount", unknownCount);
        summary.put("enabledCount", enabledCount);
        summary.put("pausedCount", pausedCount);

        double overallUptime = 100.0;
        if (totalMonitors > 0) {
            overallUptime = Math.round(((double) (upCount + degradedCount) / totalMonitors) * 10000.0) / 100.0;
        }
        summary.put("overallUptimePercentage", overallUptime);

        return summary;
    }
}
