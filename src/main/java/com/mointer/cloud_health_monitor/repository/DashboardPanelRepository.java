package com.mointer.cloud_health_monitor.repository;

import com.mointer.cloud_health_monitor.entity.DashboardPanel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DashboardPanelRepository extends JpaRepository<DashboardPanel, Long> {
    List<DashboardPanel> findByDashboardId(Long dashboardId);
}
