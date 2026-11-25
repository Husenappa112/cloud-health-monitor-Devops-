package com.mointer.cloud_health_monitor.model;

import java.time.Instant;

/**
 * JSON representation of a registered service (GET /services, GET /services/{name}).
 */
public record ServiceResponse(
        String  name,
        String  url,
        String  status,
        Integer httpStatus,
        Long    responseTimeMs,
        Instant checkedAt
) {
    public static ServiceResponse from(ServiceEntry e) {
        return new ServiceResponse(
                e.getName(),
                e.getUrl(),
                e.getStatus(),
                e.getHttpStatus(),
                e.getResponseTimeMs(),
                e.getCheckedAt()
        );
    }
}
