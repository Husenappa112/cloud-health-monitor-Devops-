package com.mointer.cloud_health_monitor.entity;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a Grafana-style customizable observability dashboard.
 */
@Entity
@Table(name = "saved_dashboards")
public class SavedDashboard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(name = "is_default")
    private Boolean isDefault = false;

    @OneToMany(mappedBy = "dashboard", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<DashboardPanel> panels = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public SavedDashboard() {}

    public SavedDashboard(String title, String description, boolean isDefault) {
        this.title = title;
        this.description = description;
        this.isDefault = isDefault;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void addPanel(DashboardPanel panel) {
        panels.add(panel);
        panel.setDashboard(this);
    }

    public void removePanel(DashboardPanel panel) {
        panels.remove(panel);
        panel.setDashboard(null);
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isDefault() { return Boolean.TRUE.equals(isDefault); }
    @JsonSetter("default")
    public void setDefault(Boolean aDefault) { isDefault = aDefault != null ? aDefault : false; }

    public List<DashboardPanel> getPanels() { return panels; }
    public void setPanels(List<DashboardPanel> panels) { this.panels = panels; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
