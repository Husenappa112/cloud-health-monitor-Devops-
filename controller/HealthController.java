
package com.mointer.cloudhealth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> checkHealth() {

        return Map.of(
            "status", "UP",
            "application", "Cloud Health Monitor",
            "message", "Application is running successfully"
        );
    }
}
