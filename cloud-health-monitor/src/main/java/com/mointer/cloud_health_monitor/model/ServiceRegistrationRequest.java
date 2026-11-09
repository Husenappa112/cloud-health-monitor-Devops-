package com.mointer.cloud_health_monitor.model;

/**
 * JSON body for POST /services.
 */
public record ServiceRegistrationRequest(String name, String url) {}
