package com.mointer.cloud_health_monitor.controller;

import com.mointer.cloud_health_monitor.entity.DashboardPanel;
import com.mointer.cloud_health_monitor.entity.MonitoredService;
import com.mointer.cloud_health_monitor.exception.ServiceNotFoundException;
import com.mointer.cloud_health_monitor.repository.DashboardPanelRepository;
import com.mointer.cloud_health_monitor.repository.MonitoredServiceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/visualizations", "/api/visualizations"})
public class VisualizationController {

    private final DashboardPanelRepository panelRepository;
    private final MonitoredServiceRepository serviceRepository;

    public VisualizationController(DashboardPanelRepository panelRepository,
                                   MonitoredServiceRepository serviceRepository) {
        this.panelRepository = panelRepository;
        this.serviceRepository = serviceRepository;
    }

    /**
     * Supported visualization types matching Grafana panels.
     */
    @GetMapping("/types")
    public List<Map<String, Object>> getVisualizationTypes() {
        return List.of(
                // Time Series
                Map.of("id", "TIME_SERIES", "name", "Time Series (Line)", "category", "Time Series", "icon", "line-chart", "desc", "Line visualization showing metric trends over time"),
                Map.of("id", "AREA", "name", "Area Chart", "category", "Time Series", "icon", "area-chart", "desc", "Filled area chart showing volume or cumulative values"),
                Map.of("id", "MULTI_SERIES", "name", "Multi-Series Line", "category", "Time Series", "icon", "multi-line", "desc", "Compare latency across multiple endpoints"),

                // Bar
                Map.of("id", "BAR_VERTICAL", "name", "Vertical Bar Chart", "category", "Bar", "icon", "bar-chart", "desc", "Compare response times or checks across targets"),
                Map.of("id", "BAR_HORIZONTAL", "name", "Horizontal Bar Chart", "category", "Bar", "icon", "bar-h", "desc", "Rank services by response time or failure count"),
                Map.of("id", "BAR_STACKED", "name", "Stacked Bar Chart", "category", "Bar", "icon", "bar-stack", "desc", "Status codes (2xx, 3xx, 4xx, 5xx) distribution"),

                // Distribution
                Map.of("id", "HEATMAP", "name", "Heatmap", "category", "Distribution", "icon", "heatmap", "desc", "Hourly activity and latency density matrix"),
                Map.of("id", "HISTOGRAM", "name", "Histogram", "category", "Distribution", "icon", "histogram", "desc", "Response latency frequency distribution"),

                // Statistical
                Map.of("id", "GAUGE", "name", "Gauge", "category", "Statistical", "icon", "gauge", "desc", "Dial meter displaying SLA percentage or current latency"),
                Map.of("id", "STAT", "name", "Stat Panel", "category", "Statistical", "icon", "stat", "desc", "Prominent single stat display with trend sparkline"),
                Map.of("id", "DONUT", "name", "Donut / Pie Chart", "category", "Statistical", "icon", "donut", "desc", "Proportion of HTTP status codes or status split"),

                // Special
                Map.of("id", "STATUS_TIMELINE", "name", "Status Timeline", "category", "Special", "icon", "timeline", "desc", "Color-coded state bands (UP, DEGRADED, DOWN)"),
                Map.of("id", "TABLE", "name", "Data Table", "category", "Special", "icon", "table", "desc", "Tabular view of check records and metrics")
        );
    }

    /**
     * Supported metrics and their live availability.
     */
    @GetMapping("/metrics")
    public List<Map<String, Object>> getVisualizationMetrics() {
        return List.of(
                // HTTP
                Map.of("id", "RESPONSE_TIME", "name", "Response Latency (ms)", "category", "HTTP", "status", "AVAILABLE", "unit", "ms"),
                Map.of("id", "HTTP_CODES", "name", "HTTP Status Distribution", "category", "HTTP", "status", "AVAILABLE", "unit", "count"),
                Map.of("id", "LATENCY_PERCENTILES", "name", "Latency Percentiles (P50/P90/P95/P99)", "category", "HTTP", "status", "AVAILABLE", "unit", "ms"),
                Map.of("id", "CHECKS_VOLUME", "name", "Health Check Volume", "category", "HTTP", "status", "AVAILABLE", "unit", "checks"),

                // Availability
                Map.of("id", "UPTIME", "name", "Uptime Percentage (%)", "category", "Availability", "status", "AVAILABLE", "unit", "%"),
                Map.of("id", "STATUS_STATE", "name", "Operational Status (UP/DOWN)", "category", "Availability", "status", "AVAILABLE", "unit", "state"),
                Map.of("id", "CONSECUTIVE_FAILURES", "name", "Consecutive Failure Count", "category", "Availability", "status", "AVAILABLE", "unit", "count"),

                // DNS & TLS
                Map.of("id", "DNS_LOOKUP", "name", "DNS Resolution Time (ms)", "category", "DNS", "status", "AVAILABLE", "unit", "ms"),
                Map.of("id", "SSL_DAYS", "name", "SSL Days Until Expiration", "category", "TLS", "status", "AVAILABLE", "unit", "days"),

                // Infrastructure
                Map.of("id", "JVM_HEAP", "name", "Backend JVM Heap Utilization", "category", "Infrastructure", "status", "AVAILABLE", "unit", "%"),
                Map.of("id", "JVM_THREADS", "name", "JVM Active Thread Count", "category", "Infrastructure", "status", "AVAILABLE", "unit", "threads"),
                Map.of("id", "HOST_CPU", "name", "Remote Target Host CPU", "category", "Infrastructure", "status", "NOT_CONNECTED", "unit", "%", "note", "Agent required on target server"),
                Map.of("id", "HOST_RAM", "name", "Remote Target Host RAM", "category", "Infrastructure", "status", "NOT_CONNECTED", "unit", "%", "note", "Agent required on target server")
        );
    }

    @GetMapping
    public List<DashboardPanel> listSavedVisualizations() {
        return panelRepository.findAll();
    }

    @GetMapping("/{id}")
    public DashboardPanel getVisualization(@PathVariable Long id) {
        return panelRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Visualization Panel with ID " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DashboardPanel createVisualization(@RequestBody DashboardPanel panel) {
        if (panel.getTitle() == null || panel.getTitle().isBlank()) {
            panel.setTitle(panel.getVisualizationType() + " — " + panel.getMetric());
        }
        if (panel.getMonitorId() != null) {
            MonitoredService svc = serviceRepository.findById(panel.getMonitorId()).orElse(null);
            if (svc != null) {
                panel.setMonitorName(svc.getName());
            }
        }
        panel.setCreatedAt(Instant.now());
        return panelRepository.save(panel);
    }

    @PutMapping("/{id}")
    public DashboardPanel updateVisualization(@PathVariable Long id, @RequestBody DashboardPanel updated) {
        DashboardPanel panel = panelRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Visualization Panel with ID " + id));

        if (updated.getTitle() != null) panel.setTitle(updated.getTitle());
        if (updated.getVisualizationType() != null) panel.setVisualizationType(updated.getVisualizationType());
        if (updated.getMetric() != null) panel.setMetric(updated.getMetric());
        if (updated.getTimeRange() != null) panel.setTimeRange(updated.getTimeRange());
        if (updated.getOptionsJson() != null) panel.setOptionsJson(updated.getOptionsJson());
        if (updated.getMonitorId() != null) {
            panel.setMonitorId(updated.getMonitorId());
            MonitoredService svc = serviceRepository.findById(updated.getMonitorId()).orElse(null);
            if (svc != null) panel.setMonitorName(svc.getName());
        }
        return panelRepository.save(panel);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVisualization(@PathVariable Long id) {
        if (!panelRepository.existsById(id)) {
            throw new ServiceNotFoundException("Visualization Panel with ID " + id);
        }
        panelRepository.deleteById(id);
    }
}
