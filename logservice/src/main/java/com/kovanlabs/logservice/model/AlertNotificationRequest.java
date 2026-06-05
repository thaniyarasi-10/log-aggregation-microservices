package com.kovanlabs.logservice.model;

public record AlertNotificationRequest(
        String recipientEmail,
        String recipientName,
        String service,
        String severity,
        String message,
        int count) {
}
