package com.mointer.cloud_health_monitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Cloud Health Monitor — Spring Boot entry point.
 *
 * <p>Component scan covers all sub-packages automatically.
 */
@SpringBootApplication
public class CloudHealthMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudHealthMonitorApplication.class, args);
    }
}
