package com.mointer.cloud_health_monitor.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMonitorRequest(
        @NotBlank(message = "Monitor name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @NotBlank(message = "Target URL is required")
        @Size(max = 1024, message = "URL must not exceed 1024 characters")
        String url,

        Integer monitoringInterval
) {}
