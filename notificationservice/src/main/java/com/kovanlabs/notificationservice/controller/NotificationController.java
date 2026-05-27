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
import com.kovanlabs.notificationservice.model.NotificationPreference;
import com.kovanlabs.notificationservice.service.AlertNotificationService;
import com.kovanlabs.notificationservice.service.NotificationPreferenceService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationPreferenceService preferenceService;
    private final AlertNotificationService alertNotificationService;

    public NotificationController(
            NotificationPreferenceService preferenceService,
            AlertNotificationService alertNotificationService) {
        this.preferenceService = preferenceService;
        this.alertNotificationService = alertNotificationService;
    }

    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferenceView> getPreferences(
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        NotificationPreference pref = preferenceService.getOrCreatePreference(userEmail);
        return ResponseEntity.ok(toView(pref));
    }

    @PutMapping("/preferences")
    public ResponseEntity<NotificationPreferenceView> updatePreferences(
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestBody NotificationPreferenceRequest request) {
        boolean emailEnabled = request != null && request.emailEnabled();
        NotificationPreference updated = preferenceService.updatePreference(userEmail, emailEnabled);
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

    private NotificationPreferenceView toView(NotificationPreference pref) {
        return new NotificationPreferenceView(
                pref.isEmailEnabled(),
                pref.getCreatedAt(),
                pref.getUpdatedAt());
    }
}