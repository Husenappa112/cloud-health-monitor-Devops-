package com.mointer.cloud_health_monitor.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity representing an individual visualization panel inside a saved dashboard.
 */
@Entity
@Table(name = "dashboard_panels")
public class DashboardPanel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dashboard_id")
    @JsonIgnore
    private SavedDashboard dashboard;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "visualization_type", nullable = false, length = 50)
    private String visualizationType; // TIME_SERIES, AREA, BAR_VERTICAL, BAR_HORIZONTAL, HEATMAP, HISTOGRAM, GAUGE, STAT, DONUT, STATUS_TIMELINE, TABLE

    @Column(name = "monitor_id")
    private Long monitorId;

    @Column(name = "monitor_name", length = 100)
    private String monitorName;

    @Column(nullable = false, length = 50)
    private String metric; // RESPONSE_TIME, UPTIME, HTTP_CODES, DNS_LOOKUP, SSL_DAYS, CPU, MEMORY

    @Column(name = "time_range", length = 20)
    private String timeRange = "24h";

    @Column(name = "options_json", length = 2000)
    private String optionsJson;

    @Column(name = "grid_x")
    private Integer gridX = 0;

    @Column(name = "grid_y")
    private Integer gridY = 0;

    @Column(name = "grid_w")
    private Integer gridW = 6;

    @Column(name = "grid_h")
    private Integer gridH = 4;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public DashboardPanel() {}

    public DashboardPanel(String title, String visualizationType, Long monitorId, String monitorName,
                          String metric, String timeRange, Integer gridW, Integer gridH) {
        this.title = title;
        this.visualizationType = visualizationType;
        this.monitorId = monitorId;
        this.monitorName = monitorName;
        this.metric = metric;
        this.timeRange = timeRange;
        this.gridW = gridW;
        this.gridH = gridH;
        this.createdAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SavedDashboard getDashboard() { return dashboard; }
    public void setDashboard(SavedDashboard dashboard) { this.dashboard = dashboard; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getVisualizationType() { return visualizationType; }
    public void setVisualizationType(String visualizationType) { this.visualizationType = visualizationType; }

    public Long getMonitorId() { return monitorId; }
    public void setMonitorId(Long monitorId) { this.monitorId = monitorId; }

    public String getMonitorName() { return monitorName; }
    public void setMonitorName(String monitorName) { this.monitorName = monitorName; }

    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }

    public String getTimeRange() { return timeRange; }
    public void setTimeRange(String timeRange) { this.timeRange = timeRange; }

    public String getOptionsJson() { return optionsJson; }
    public void setOptionsJson(String optionsJson) { this.optionsJson = optionsJson; }

    public Integer getGridX() { return gridX; }
    public void setGridX(Integer gridX) { this.gridX = gridX; }

    public Integer getGridY() { return gridY; }
    public void setGridY(Integer gridY) { this.gridY = gridY; }

    public Integer getGridW() { return gridW; }
    public void setGridW(Integer gridW) { this.gridW = gridW; }

    public Integer getGridH() { return gridH; }
    public void setGridH(Integer gridH) { this.gridH = gridH; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
