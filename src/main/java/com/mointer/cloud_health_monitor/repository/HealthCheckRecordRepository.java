package com.mointer.cloud_health_monitor.repository;

import com.mointer.cloud_health_monitor.entity.HealthCheckRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface HealthCheckRecordRepository extends JpaRepository<HealthCheckRecord, Long> {

    List<HealthCheckRecord> findByServiceIdOrderByCheckedAtDesc(Long serviceId, Pageable pageable);

    List<HealthCheckRecord> findTop50ByServiceIdOrderByCheckedAtDesc(Long serviceId);

    List<HealthCheckRecord> findByServiceIdAndCheckedAtAfterOrderByCheckedAtAsc(Long serviceId, Instant after);

    List<HealthCheckRecord> findByCheckedAtAfterOrderByCheckedAtAsc(Instant after);

    long countByServiceIdAndCheckedAtAfter(Long serviceId, Instant after);

    long countByServiceIdAndStatusAndCheckedAtAfter(Long serviceId, String status, Instant after);

    @Query("SELECT AVG(r.responseTimeMs) FROM HealthCheckRecord r WHERE r.serviceId = :serviceId AND r.status = 'UP' AND r.checkedAt >= :after")
    Double findAvgResponseTimeMsAfter(@Param("serviceId") Long serviceId, @Param("after") Instant after);

    @Query("SELECT MIN(r.responseTimeMs) FROM HealthCheckRecord r WHERE r.serviceId = :serviceId AND r.status = 'UP' AND r.checkedAt >= :after")
    Long findMinResponseTimeMsAfter(@Param("serviceId") Long serviceId, @Param("after") Instant after);

    @Query("SELECT MAX(r.responseTimeMs) FROM HealthCheckRecord r WHERE r.serviceId = :serviceId AND r.status = 'UP' AND r.checkedAt >= :after")
    Long findMaxResponseTimeMsAfter(@Param("serviceId") Long serviceId, @Param("after") Instant after);
}
