package com.mointer.cloud_health_monitor.repository;

import com.mointer.cloud_health_monitor.entity.SavedDashboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavedDashboardRepository extends JpaRepository<SavedDashboard, Long> {
    List<SavedDashboard> findAllByOrderByCreatedAtAsc();
}
