package com.mointer.cloud_health_monitor.repository;

import com.mointer.cloud_health_monitor.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    List<Incident> findAllByOrderByStartedAtDesc();

    List<Incident> findByStatusOrderByStartedAtDesc(String status);

    List<Incident> findByServiceIdOrderByStartedAtDesc(Long serviceId);

    Optional<Incident> findTopByServiceIdAndStatusOrderByStartedAtDesc(Long serviceId, String status);

    long countByStatus(String status);
}
