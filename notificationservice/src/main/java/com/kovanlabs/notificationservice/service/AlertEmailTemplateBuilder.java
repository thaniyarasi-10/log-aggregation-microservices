package com.kovanlabs.notificationservice.service;

import org.springframework.stereotype.Service;

import com.kovanlabs.notificationservice.dto.AlertNotificationRequest;

@Service
public class AlertEmailTemplateBuilder {

    public String buildPlainTextEmail(AlertNotificationRequest alert, String recipientName) {
        StringBuilder builder = new StringBuilder();
        builder.append("Hello ").append(defaultName(recipientName)).append(',').append('\n');
        builder.append('\n');
        builder.append("An alert was generated for service '")
                .append(defaultText(alert.service(), "Unknown Service"))
                .append("'.\n");
        builder.append("Severity: ").append(defaultText(alert.severity(), "ALERT")).append('\n');
        builder.append("Count: ").append(alert.count()).append('\n');
        builder.append("Message: ").append(defaultText(alert.message(), "No message provided")).append('\n');
        return builder.toString();
    }

    public String buildHtmlEmail(AlertNotificationRequest alert, String recipientName) {
        return "<html><body><h2>Hello " + defaultName(recipientName) + "</h2>"
                + "<p>An alert was generated for service <strong>" + defaultText(alert.service(), "Unknown Service") + "</strong>.</p>"
                + "<ul>"
                + "<li>Severity: " + defaultText(alert.severity(), "ALERT") + "</li>"
                + "<li>Count: " + alert.count() + "</li>"
                + "<li>Message: " + defaultText(alert.message(), "No message provided") + "</li>"
                + "</ul></body></html>";
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String defaultName(String value) {
        return value == null || value.isBlank() ? "team member" : value;
    }
}