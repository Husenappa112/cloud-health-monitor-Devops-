package com.mointer.cloud_health_monitor.repository;

import com.mointer.cloud_health_monitor.entity.AlertHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertHistoryRepository extends JpaRepository<AlertHistory, Long> {
    List<AlertHistory> findAllByOrderByTriggeredAtDesc();
    List<AlertHistory> findByStatusOrderByTriggeredAtDesc(String status);
    List<AlertHistory> findByMonitorIdOrderByTriggeredAtDesc(Long monitorId);
    long countByStatus(String status);
}
