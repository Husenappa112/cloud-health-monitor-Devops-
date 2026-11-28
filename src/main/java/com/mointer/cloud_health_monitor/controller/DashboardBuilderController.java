package com.mointer.cloud_health_monitor.controller;

import com.mointer.cloud_health_monitor.entity.DashboardPanel;
import com.mointer.cloud_health_monitor.entity.SavedDashboard;
import com.mointer.cloud_health_monitor.exception.ServiceNotFoundException;
import com.mointer.cloud_health_monitor.repository.SavedDashboardRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping({"/dashboards", "/api/dashboards"})
public class DashboardBuilderController {

    private static final Logger log = LoggerFactory.getLogger(DashboardBuilderController.class);

    private final SavedDashboardRepository dashboardRepository;

    public DashboardBuilderController(SavedDashboardRepository dashboardRepository) {
        this.dashboardRepository = dashboardRepository;
    }

    @PostConstruct
    @Transactional
    public void seedDefaultDashboards() {
        if (dashboardRepository.count() == 0) {
            log.info("Seeding default Grafana-style dashboard templates...");

            // Template 1: URL Health Overview
            SavedDashboard t1 = new SavedDashboard("URL Health Overview", "High-level fleet availability, health states, and response timings.", true);
            t1.addPanel(new DashboardPanel("Fleet Availability Gauge", "GAUGE", null, "All Services", "UPTIME", "24h", 4, 3));
            t1.addPanel(new DashboardPanel("Response Latency Trends", "TIME_SERIES", null, "All Services", "RESPONSE_TIME", "24h", 8, 3));
            t1.addPanel(new DashboardPanel("Status Code Distribution", "DONUT", null, "All Services", "HTTP_CODES", "24h", 4, 3));
            t1.addPanel(new DashboardPanel("Service Status Timeline", "STATUS_TIMELINE", null, "All Services", "STATUS_STATE", "24h", 8, 3));
            dashboardRepository.save(t1);

            // Template 2: HTTP Performance
            SavedDashboard t2 = new SavedDashboard("HTTP Performance", "Detailed synthetic latency percentiles, error distribution, and probe performance.", false);
            t2.addPanel(new DashboardPanel("Latency Percentiles (P50/P90/P95/P99)", "BAR_VERTICAL", null, "All Services", "LATENCY_PERCENTILES", "24h", 6, 3));
            t2.addPanel(new DashboardPanel("Response Time Area Chart", "AREA", null, "All Services", "RESPONSE_TIME", "24h", 6, 3));
            t2.addPanel(new DashboardPanel("HTTP Error Distribution", "BAR_STACKED", null, "All Services", "HTTP_CODES", "24h", 6, 3));
            t2.addPanel(new DashboardPanel("Response Latency Histogram", "HISTOGRAM", null, "All Services", "RESPONSE_TIME", "24h", 6, 3));
            dashboardRepository.save(t2);

            // Template 3: Availability Monitoring
            SavedDashboard t3 = new SavedDashboard("Availability Monitoring", "Historical uptime compliance, consecutive failures, and SLA thresholds.", false);
            t3.addPanel(new DashboardPanel("SLA Availability Gauge", "GAUGE", null, "All Services", "UPTIME", "7d", 4, 3));
            t3.addPanel(new DashboardPanel("Fleet Uptime History", "TIME_SERIES", null, "All Services", "UPTIME", "7d", 8, 3));
            t3.addPanel(new DashboardPanel("Status Timeline Matrix", "STATUS_TIMELINE", null, "All Services", "STATUS_STATE", "24h", 12, 4));
            dashboardRepository.save(t3);

            // Template 4: DNS and TLS
            SavedDashboard t4 = new SavedDashboard("DNS and TLS", "Certificate validity, expiration countdowns, and DNS lookup durations.", false);
            t4.addPanel(new DashboardPanel("SSL Days Remaining Countdown", "BAR_HORIZONTAL", null, "All Services", "SSL_DAYS", "30d", 6, 4));
            t4.addPanel(new DashboardPanel("DNS Lookup Time (ms)", "TIME_SERIES", null, "All Services", "DNS_LOOKUP", "24h", 6, 4));
            dashboardRepository.save(t4);

            // Template 5: Infrastructure Monitoring
            SavedDashboard t5 = new SavedDashboard("Infrastructure Monitoring", "Backend engine JVM heap utilization, active threads, and runtime compute.", false);
            t5.addPanel(new DashboardPanel("JVM Heap Memory Gauge", "GAUGE", null, "Backend Engine", "JVM_HEAP", "1h", 6, 3));
            t5.addPanel(new DashboardPanel("JVM Active Threads", "STAT", null, "Backend Engine", "JVM_THREADS", "1h", 6, 3));
            dashboardRepository.save(t5);

            // Template 6: Security Overview
            SavedDashboard t6 = new SavedDashboard("Security Overview", "TLS certificates, encryption posture, and SSRF defense enforcement.", false);
            t6.addPanel(new DashboardPanel("SSL Certificates Health", "DONUT", null, "All Services", "SSL_DAYS", "30d", 6, 4));
            t6.addPanel(new DashboardPanel("Consecutive Failures", "BAR_VERTICAL", null, "All Services", "CONSECUTIVE_FAILURES", "24h", 6, 4));
            dashboardRepository.save(t6);

            // Template 7: Incident Overview
            SavedDashboard t7 = new SavedDashboard("Incident Overview", "Outage durations, resolution times, and state transitions.", false);
            t7.addPanel(new DashboardPanel("Service Status Timeline", "STATUS_TIMELINE", null, "All Services", "STATUS_STATE", "24h", 12, 4));
            dashboardRepository.save(t7);
        }
    }

    @GetMapping
    public List<SavedDashboard> listDashboards() {
        return dashboardRepository.findAllByOrderByCreatedAtAsc();
    }

    @GetMapping("/{id}")
    public SavedDashboard getDashboard(@PathVariable Long id) {
        return dashboardRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Dashboard with ID " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedDashboard createDashboard(@RequestBody SavedDashboard dashboard) {
        if (dashboard.getTitle() == null || dashboard.getTitle().isBlank()) {
            dashboard.setTitle("New Dashboard");
        }
        dashboard.setCreatedAt(Instant.now());
        dashboard.setUpdatedAt(Instant.now());
        if (dashboard.getPanels() != null) {
            dashboard.getPanels().forEach(p -> p.setDashboard(dashboard));
        }
        return dashboardRepository.save(dashboard);
    }

    @PutMapping("/{id}")
    public SavedDashboard updateDashboard(@PathVariable Long id, @RequestBody SavedDashboard updated) {
        SavedDashboard dashboard = dashboardRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Dashboard with ID " + id));

        if (updated.getTitle() != null) dashboard.setTitle(updated.getTitle());
        if (updated.getDescription() != null) dashboard.setDescription(updated.getDescription());
        dashboard.setDefault(updated.isDefault());
        dashboard.setUpdatedAt(Instant.now());

        if (updated.getPanels() != null) {
            dashboard.getPanels().clear();
            for (DashboardPanel p : updated.getPanels()) {
                p.setId(null);
                p.setDashboard(dashboard);
                dashboard.getPanels().add(p);
            }
        }

        return dashboardRepository.save(dashboard);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDashboard(@PathVariable Long id) {
        if (!dashboardRepository.existsById(id)) {
            throw new ServiceNotFoundException("Dashboard with ID " + id);
        }
        dashboardRepository.deleteById(id);
    }
}
