package com.kovanlabs.notificationservice.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.kovanlabs.notificationservice.dto.AlertNotificationRequest;
import com.kovanlabs.notificationservice.dto.NotificationPreferenceRequest;
import com.kovanlabs.notificationservice.dto.NotificationPreferenceView;
import com.kovanlabs.notificationservice.dto.AlertItemView;
import com.kovanlabs.notificationservice.model.Alert;
import com.kovanlabs.notificationservice.model.NotificationPreference;
import com.kovanlabs.notificationservice.repository.AlertRepository;
import com.kovanlabs.notificationservice.service.AlertNotificationService;
import com.kovanlabs.notificationservice.service.NotificationPreferenceService;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationPreferenceService preferenceService;
    private final AlertNotificationService alertNotificationService;
    private final AlertRepository alertRepository;

    public NotificationController(
            NotificationPreferenceService preferenceService,
            AlertNotificationService alertNotificationService,
            AlertRepository alertRepository) {
        this.preferenceService = preferenceService;
        this.alertNotificationService = alertNotificationService;
        this.alertRepository = alertRepository;
    }

    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferenceView> getPreferences(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        NotificationPreference pref = preferenceService.getOrCreatePreference(userId);
        return ResponseEntity.ok(toView(pref));
    }

    @PutMapping("/preferences")
    public ResponseEntity<NotificationPreferenceView> updatePreferences(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody NotificationPreferenceRequest request) {
        boolean emailEnabled = request != null && request.emailEnabled();
        NotificationPreference updated = preferenceService.updatePreference(userId, emailEnabled);
        return ResponseEntity.ok(toView(updated));
    }

    @PostMapping("/alerts")
    public ResponseEntity<Map<String, Object>> sendAlert(@RequestBody AlertNotificationRequest request) {
        boolean sent = alertNotificationService.sendAlert(request);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", sent ? "accepted" : "skipped");
        response.put("recipientEmail", request != null ? request.recipientEmail() : null);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/alerts")
    public ResponseEntity<Map<String, List<AlertItemView>>> getAlerts() {
        List<Alert> alerts = alertRepository.findAll();
        Map<String, List<AlertItemView>> grouped = alerts.stream()
                .map(a -> new AlertItemView(
                        a.getService(),
                        a.getMessage(),
                        a.getCount(),
                        a.getSeverity(),
                        a.getTimestamp()
                ))
                .collect(Collectors.groupingBy(AlertItemView::service));
        return ResponseEntity.ok(grouped);
    }

    private NotificationPreferenceView toView(NotificationPreference pref) {
        return new NotificationPreferenceView(
                pref.isEmailEnabled(),
                pref.getCreatedAt(),
                pref.getUpdatedAt());
    }
}