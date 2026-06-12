package com.kovanlabs.logservice.model;

public record AlertNotificationRequest(
        String recipientEmail,
        String recipientName,
        String service,
        String severity,
        String message,
<<<<<<< HEAD
        int count,
        String organizationId) {

    public AlertNotificationRequest(
            String recipientEmail,
            String recipientName,
            String service,
            String severity,
            String message,
            int count) {
        this(recipientEmail, recipientName, service, severity, message, count, null);
    }
=======
        int count) {
>>>>>>> 6a01b900be15a6a689e602f89925f0c54101ef47
}
