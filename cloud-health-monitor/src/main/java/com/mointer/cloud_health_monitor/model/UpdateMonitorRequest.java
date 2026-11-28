package com.mointer.cloud_health_monitor.model;

import jakarta.validation.constraints.Size;

public record UpdateMonitorRequest(
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @Size(max = 1024, message = "URL must not exceed 1024 characters")
        String url,

        Integer monitoringInterval,

        Boolean enabled
) {}
