package com.kovanlabs.notificationservice.dto;

public record AlertNotificationRequest(
        String recipientEmail,
        String recipientName,
        String service,
        String severity,
        String message,
        int count,
        String organizationId) {

    public AlertNotificationRequest(
            String recipientEmail,
            String recipientName,
            String service,
            String severity,
            String message,
            int count) {
        this(recipientEmail, recipientName, service, severity, message, count, "00000000-0000-0000-0000-000000000000");
    }
}