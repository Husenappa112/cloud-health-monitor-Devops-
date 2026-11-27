package com.mointer.cloud_health_monitor.service;

import com.mointer.cloud_health_monitor.entity.AlertHistory;
import com.mointer.cloud_health_monitor.entity.AlertRule;
import com.mointer.cloud_health_monitor.entity.MonitoredService;
import com.mointer.cloud_health_monitor.repository.AlertHistoryRepository;
import com.mointer.cloud_health_monitor.repository.AlertRuleRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class AlertManagementService {

    private static final Logger log = LoggerFactory.getLogger(AlertManagementService.class);

    private final AlertRuleRepository ruleRepository;
    private final AlertHistoryRepository historyRepository;

    public AlertManagementService(AlertRuleRepository ruleRepository, AlertHistoryRepository historyRepository) {
        this.ruleRepository = ruleRepository;
        this.historyRepository = historyRepository;
    }

    @PostConstruct
    @Transactional
    public void seedDefaultRules() {
        if (ruleRepository.count() == 0) {
            log.info("Seeding default observability alert rules...");
            ruleRepository.saveAll(List.of(
                    new AlertRule("Website Outage Alert", null, "All Services", "WEBSITE_DOWN", "EQUALS", 2.0, "CRITICAL"),
                    new AlertRule("High Response Latency Alert", null, "All Services", "RESPONSE_TIME", "GREATER_THAN", 1000.0, "WARNING"),
                    new AlertRule("Low Uptime SLA Breach", null, "All Services", "UPTIME_PERCENTAGE", "LESS_THAN", 99.0, "HIGH"),
                    new AlertRule("SSL Certificate Expiry Warning", null, "All Services", "SSL_EXPIRY", "LESS_THAN", 14.0, "WARNING"),
                    new AlertRule("DNS Lookup Failure", null, "All Services", "DNS_FAILURE", "EQUALS", 1.0, "HIGH"),
                    new AlertRule("HTTP 5xx Server Error Outage", null, "All Services", "HTTP_5XX", "GREATER_THAN", 499.0, "CRITICAL")
            ));
        }
    }

    /**
     * Evaluates all active rules against a monitored service after a health check.
     */
    @Transactional
    public void evaluateRulesForService(MonitoredService service) {
        if (service == null || !service.isEnabled()) return;

        List<AlertRule> rules = ruleRepository.findByEnabledTrue();
        for (AlertRule rule : rules) {
            if (rule.getMonitorId() != null && !rule.getMonitorId().equals(service.getId())) {
                continue; // Applies to a different specific monitor
            }

            boolean shouldFire = false;
            String reason = "";
            Double metricVal = null;

            switch (rule.getMetricType()) {
                case "WEBSITE_DOWN" -> {
                    if ("DOWN".equals(service.getCurrentStatus()) &&
                            service.getConsecutiveFailures() >= (rule.getConsecutiveChecks() != null ? rule.getConsecutiveChecks() : 2)) {
                        shouldFire = true;
                        metricVal = (double) service.getConsecutiveFailures();
                        reason = "Service has been DOWN for " + service.getConsecutiveFailures() + " consecutive checks.";
                    }
                }
                case "RESPONSE_TIME" -> {
                    if (service.getLastResponseTimeMs() != null &&
                            service.getLastResponseTimeMs() > rule.getThresholdValue()) {
                        shouldFire = true;
                        metricVal = service.getLastResponseTimeMs().doubleValue();
                        reason = "Response latency " + service.getLastResponseTimeMs() + "ms exceeded threshold of " + rule.getThresholdValue() + "ms.";
                    }
                }
                case "UPTIME_PERCENTAGE" -> {
                    if (service.getUptimePercentage() != null &&
                            service.getUptimePercentage() < rule.getThresholdValue()) {
                        shouldFire = true;
                        metricVal = service.getUptimePercentage();
                        reason = "Uptime " + service.getUptimePercentage() + "% dropped below threshold " + rule.getThresholdValue() + "%.";
                    }
                }
                case "SSL_EXPIRY" -> {
                    if (service.getSslDaysRemaining() != null &&
                            service.getSslDaysRemaining() <= rule.getThresholdValue()) {
                        shouldFire = true;
                        metricVal = service.getSslDaysRemaining().doubleValue();
                        reason = "SSL certificate expires in " + service.getSslDaysRemaining() + " days (threshold <= " + rule.getThresholdValue() + "d).";
                    }
                }
                case "DNS_FAILURE" -> {
                    if ("FAILED".equalsIgnoreCase(service.getDnsStatus())) {
                        shouldFire = true;
                        metricVal = 1.0;
                        reason = "DNS resolution failed for hostname.";
                    }
                }
                case "HTTP_5XX" -> {
                    if (service.getLastStatusCode() != null && service.getLastStatusCode() >= 500) {
                        shouldFire = true;
                        metricVal = service.getLastStatusCode().doubleValue();
                        reason = "HTTP " + service.getLastStatusCode() + " server outage received.";
                    }
                }
            }

            if (shouldFire) {
                fireAlert(rule, service, reason, metricVal);
            }
        }

        // If service is UP, auto-resolve any FIRING alerts for this service
        if ("UP".equals(service.getCurrentStatus())) {
            resolveFiringAlertsForService(service.getId());
        }
    }

    private void fireAlert(AlertRule rule, MonitoredService service, String reason, Double metricVal) {
        // Check if there is already an active FIRING alert for this rule & service
        List<AlertHistory> existing = historyRepository.findByStatusOrderByTriggeredAtDesc("FIRING");
        boolean alreadyFiring = existing.stream().anyMatch(a ->
                rule.getId().equals(a.getRuleId()) &&
                (service.getId().equals(a.getMonitorId()) || a.getMonitorId() == null));

        if (!alreadyFiring) {
            AlertHistory alert = new AlertHistory(
                    rule.getId(),
                    rule.getName(),
                    service.getId(),
                    service.getName(),
                    rule.getSeverity(),
                    "FIRING",
                    reason,
                    metricVal
            );
            historyRepository.save(alert);
            log.warn("ALERT FIRED [{}]: {} for service {}", rule.getSeverity(), rule.getName(), service.getName());
        }
    }

    private void resolveFiringAlertsForService(Long serviceId) {
        List<AlertHistory> firing = historyRepository.findByStatusOrderByTriggeredAtDesc("FIRING");
        for (AlertHistory a : firing) {
            if (serviceId.equals(a.getMonitorId())) {
                a.setStatus("RESOLVED");
                a.setResolvedAt(Instant.now());
                historyRepository.save(a);
                log.info("ALERT RESOLVED: {} for service ID {}", a.getRuleName(), serviceId);
            }
        }
    }
}
