package com.mointer.cloud_health_monitor.controller;

import com.mointer.cloud_health_monitor.entity.AlertHistory;
import com.mointer.cloud_health_monitor.entity.AlertRule;
import com.mointer.cloud_health_monitor.entity.MonitoredService;
import com.mointer.cloud_health_monitor.exception.ServiceNotFoundException;
import com.mointer.cloud_health_monitor.repository.AlertHistoryRepository;
import com.mointer.cloud_health_monitor.repository.AlertRuleRepository;
import com.mointer.cloud_health_monitor.repository.MonitoredServiceRepository;
import com.mointer.cloud_health_monitor.service.AlertManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/alerts", "/api/alerts"})
public class AlertController {

    private final AlertRuleRepository ruleRepository;
    private final AlertHistoryRepository historyRepository;
    private final MonitoredServiceRepository serviceRepository;
    private final AlertManagementService alertService;

    public AlertController(AlertRuleRepository ruleRepository,
                           AlertHistoryRepository historyRepository,
                           MonitoredServiceRepository serviceRepository,
                           AlertManagementService alertService) {
        this.ruleRepository = ruleRepository;
        this.historyRepository = historyRepository;
        this.serviceRepository = serviceRepository;
        this.alertService = alertService;
    }

    // ── Active Alerts ─────────────────────────────────────────────────────────
    @GetMapping({"", "/active"})
    public List<AlertHistory> getActiveAlerts() {
        return historyRepository.findByStatusOrderByTriggeredAtDesc("FIRING");
    }

    // ── Alert History ─────────────────────────────────────────────────────────
    @GetMapping("/history")
    public List<AlertHistory> getAlertHistory(@RequestParam(required = false) String status) {
        if (status != null && !status.isBlank()) {
            return historyRepository.findByStatusOrderByTriggeredAtDesc(status.toUpperCase());
        }
        return historyRepository.findAllByOrderByTriggeredAtDesc();
    }

    // ── Alert Rules CRUD ──────────────────────────────────────────────────────
    @GetMapping("/rules")
    public List<AlertRule> listRules() {
        return ruleRepository.findAll();
    }

    @GetMapping("/rules/{id}")
    public AlertRule getRule(@PathVariable Long id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Alert Rule with ID " + id));
    }

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    public AlertRule createRule(@RequestBody AlertRule rule) {
        if (rule.getName() == null || rule.getName().isBlank()) {
            rule.setName("Alert Rule — " + rule.getMetricType());
        }
        if (rule.getMonitorId() != null) {
            MonitoredService svc = serviceRepository.findById(rule.getMonitorId()).orElse(null);
            if (svc != null) {
                rule.setMonitorName(svc.getName());
            }
        } else {
            rule.setMonitorName("All Services");
        }
        rule.setCreatedAt(Instant.now());
        rule.setUpdatedAt(Instant.now());
        return ruleRepository.save(rule);
    }

    @PutMapping("/rules/{id}")
    public AlertRule updateRule(@PathVariable Long id, @RequestBody AlertRule updated) {
        AlertRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Alert Rule with ID " + id));

        if (updated.getName() != null) rule.setName(updated.getName());
        if (updated.getMetricType() != null) rule.setMetricType(updated.getMetricType());
        if (updated.getComparator() != null) rule.setComparator(updated.getComparator());
        if (updated.getThresholdValue() != null) rule.setThresholdValue(updated.getThresholdValue());
        if (updated.getSeverity() != null) rule.setSeverity(updated.getSeverity());
        if (updated.getEvaluationWindowMinutes() != null) rule.setEvaluationWindowMinutes(updated.getEvaluationWindowMinutes());
        if (updated.getConsecutiveChecks() != null) rule.setConsecutiveChecks(updated.getConsecutiveChecks());
        rule.setEnabled(updated.isEnabled());
        if (updated.getNotificationChannels() != null) rule.setNotificationChannels(updated.getNotificationChannels());
        if (updated.getWebhookUrl() != null) rule.setWebhookUrl(updated.getWebhookUrl());
        rule.setUpdatedAt(Instant.now());

        return ruleRepository.save(rule);
    }

    @DeleteMapping("/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@PathVariable Long id) {
        if (!ruleRepository.existsById(id)) {
            throw new ServiceNotFoundException("Alert Rule with ID " + id);
        }
        ruleRepository.deleteById(id);
    }

    // ── Alert Acknowledge & Resolve ───────────────────────────────────────────
    @PostMapping("/{id}/acknowledge")
    public AlertHistory acknowledgeAlert(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        AlertHistory alert = historyRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Alert with ID " + id));
        alert.setStatus("ACKNOWLEDGED");
        alert.setAcknowledgedAt(Instant.now());
        return historyRepository.save(alert);
    }

    @PostMapping("/{id}/resolve")
    public AlertHistory resolveAlert(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        AlertHistory alert = historyRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException("Alert with ID " + id));
        alert.setStatus("RESOLVED");
        alert.setResolvedAt(Instant.now());
        return historyRepository.save(alert);
    }
}
