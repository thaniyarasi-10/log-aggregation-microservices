package com.kovanlabs.notificationservice.dto;

public record AlertNotificationRequest(
        String recipientEmail,
        String recipientName,
        String service,
        String severity,
        String message,
        int count) {
}