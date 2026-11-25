package com.mointer.cloud_health_monitor.repository;

import com.mointer.cloud_health_monitor.entity.MonitoredService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MonitoredServiceRepository extends JpaRepository<MonitoredService, Long> {

    Optional<MonitoredService> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    List<MonitoredService> findByEnabledTrue();

    List<MonitoredService> findByCurrentStatus(String currentStatus);

    long countByCurrentStatus(String currentStatus);
}
