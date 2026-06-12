package com.kovanlabs.notificationservice.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;

import com.kovanlabs.notificationservice.dto.AlertNotificationRequest;
import com.kovanlabs.notificationservice.dto.NotificationPreferenceRequest;
import com.kovanlabs.notificationservice.dto.NotificationPreferenceView;
import com.kovanlabs.notificationservice.dto.AlertItemView;
import com.kovanlabs.notificationservice.dto.JiraStoryResponse;
import com.kovanlabs.notificationservice.model.Alert;
import com.kovanlabs.notificationservice.model.NotificationPreference;
import com.kovanlabs.notificationservice.repository.AlertRepository;
import com.kovanlabs.notificationservice.service.AlertNotificationService;
import com.kovanlabs.notificationservice.service.NotificationPreferenceService;
import com.kovanlabs.notificationservice.service.JiraStoryService;
import com.kovanlabs.notificationservice.security.TenantSecurityService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationPreferenceService preferenceService;
    private final AlertNotificationService alertNotificationService;
    private final AlertRepository alertRepository;
    private final JiraStoryService jiraStoryService;
    private final TenantSecurityService tenantSecurityService;

    public NotificationController(
            NotificationPreferenceService preferenceService,
            AlertNotificationService alertNotificationService,
            AlertRepository alertRepository,
            JiraStoryService jiraStoryService,
            TenantSecurityService tenantSecurityService) {
        this.preferenceService = preferenceService;
        this.alertNotificationService = alertNotificationService;
        this.alertRepository = alertRepository;
        this.jiraStoryService = jiraStoryService;
        this.tenantSecurityService = tenantSecurityService;
    }

    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferenceView> getPreferences(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User context missing");
        }
        NotificationPreference pref = preferenceService.getOrCreatePreference(userId);
        return ResponseEntity.ok(toView(pref));
    }

    @PutMapping("/preferences")
    public ResponseEntity<NotificationPreferenceView> updatePreferences(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody NotificationPreferenceRequest request) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User context missing");
        }
        boolean emailEnabled = request != null && request.emailEnabled();
        NotificationPreference updated = preferenceService.updatePreference(userId, emailEnabled);
        return ResponseEntity.ok(toView(updated));
    }

    @PostMapping("/alerts")
    public ResponseEntity<Map<String, Object>> sendAlert(@RequestBody AlertNotificationRequest request) {
        // Log ingestion alert triggering
        boolean sent = alertNotificationService.sendAlert(request);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", sent ? "accepted" : "skipped");
        response.put("recipientEmail", request != null ? request.recipientEmail() : null);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/alerts")
    public ResponseEntity<Map<String, List<AlertItemView>>> getAlerts(
            @RequestHeader(value = "X-User-Id") String userId,
            @RequestHeader(value = "X-Organization-Id") String orgIdStr,
            @RequestHeader(value = "X-User-Services", required = false) String userServices) {
        
        UUID orgId = tenantSecurityService.validateMembership(userId, orgIdStr);

        boolean isAdmin = false;
        try {
            tenantSecurityService.validateMembershipAndRole(userId, orgIdStr, "ADMIN");
            isAdmin = true;
        } catch (Exception ignored) {}

        List<Alert> alerts = alertRepository.findByOrganizationId(orgId);
        Map<String, List<AlertItemView>> grouped = alerts.stream()
                .map(a -> new AlertItemView(
                        a.getService(),
                        a.getMessage(),
                        a.getCount(),
                        a.getSeverity(),
                        a.getTimestamp()
                ))
                .collect(Collectors.groupingBy(AlertItemView::service));

        if (!isAdmin) {
            List<String> allowedServices = new ArrayList<>();
            if (userServices != null && !userServices.isBlank()) {
                allowedServices = Arrays.stream(userServices.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(String::toLowerCase)
                        .toList();
            }
            final List<String> services = allowedServices;
            grouped = grouped.entrySet().stream()
                    .filter(entry -> services.stream().anyMatch(s -> s.equalsIgnoreCase(entry.getKey())))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        return ResponseEntity.ok(grouped);
    }

    private NotificationPreferenceView toView(NotificationPreference pref) {
        return new NotificationPreferenceView(
                pref.isEmailEnabled(),
                pref.getCreatedAt(),
                pref.getUpdatedAt());
    }

    @PostMapping("/alerts/{alertId}/jira")
    public ResponseEntity<JiraStoryResponse> createJiraStoryPost(
            @RequestHeader(value = "X-User-Id") String userId,
            @RequestHeader(value = "X-Organization-Id") String orgIdStr,
            @PathVariable("alertId") String alertId) {
        UUID orgId = tenantSecurityService.validateMembership(userId, orgIdStr);
        Alert alert = alertRepository.findById(UUID.fromString(alertId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found"));
        if (!orgId.equals(alert.getOrganizationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Alert does not belong to your organization");
        }
        
        JiraStoryResponse response = jiraStoryService.createJiraStoryForAlert(alertId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/alerts/{alertId}/jira")
    public ResponseEntity<Void> createJiraStoryGetRedirect(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Organization-Id", required = false) String orgIdStr,
            @PathVariable("alertId") String alertId) {
        LOGGER.info("GET endpoint /alerts/{}/jira hit, alertId received: {}", alertId, alertId);
        
        if (userId == null || orgIdStr == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication headers required");
        }
        UUID orgId = tenantSecurityService.validateMembership(userId, orgIdStr);
        Alert alert = alertRepository.findById(UUID.fromString(alertId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found"));
        if (!orgId.equals(alert.getOrganizationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Alert does not belong to your organization");
        }

        try {
            LOGGER.info("Triggering Jira story creation/lookup for alertId: {}", alertId);
            JiraStoryResponse response = jiraStoryService.createJiraStoryForAlert(alertId);
            LOGGER.info("Jira story action result for alertId {}: status={}, message={}", alertId, response.status(), response.message());
            
            if ("SUCCESS".equalsIgnoreCase(response.status()) || "CREATED".equalsIgnoreCase(response.status())) {
                LOGGER.info("Redirect target URL for alertId {}: {}", alertId, response.jiraIssueUrl());
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, response.jiraIssueUrl())
                        .build();
            } else {
                LOGGER.error("Jira story creation failed for alertId {}: {}", alertId, response.message());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create Jira story: " + response.message());
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            LOGGER.error("Jira story creation exception for alertId {}: {}", alertId, ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create Jira story: " + ex.getMessage());
        }
    }
}