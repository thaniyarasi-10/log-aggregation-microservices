package com.kovanlabs.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kovanlabs.notificationservice.dto.AlertNotificationRequest;
import org.junit.jupiter.api.Test;

class AlertEmailTemplateBuilderTest {

    private final AlertEmailTemplateBuilder builder = new AlertEmailTemplateBuilder();

    @Test
    void buildPlainTextEmail_includesDefaultsWhenValuesMissing() {
        String body = builder.buildPlainTextEmail(
                new AlertNotificationRequest("dev@test.com", null, null, null, null, 5),
                null);

        assertThat(body).contains("Hello team member");
        assertThat(body).contains("Unknown Service");
        assertThat(body).contains("Severity: ALERT");
        assertThat(body).contains("Count: 5");
    }

    @Test
    void buildHtmlEmail_includesProvidedValues() {
        String body = builder.buildHtmlEmail(
                new AlertNotificationRequest("dev@test.com", "Dev User", "payment-service", "CRITICAL", "DB timeout", 3),
                "Dev User");

        assertThat(body).contains("Hello Dev User");
        assertThat(body).contains("payment-service");
        assertThat(body).contains("CRITICAL");
        assertThat(body).contains("DB timeout");
    }

    @Test
    void buildPlainTextEmail_withAlert_usesPublicBaseUrl() {
        com.kovanlabs.notificationservice.model.Alert alert = new com.kovanlabs.notificationservice.model.Alert();
        alert.setId(java.util.UUID.fromString("12345678-1234-1234-1234-123456789abc"));
        alert.setSeverity("LOW");
        alert.setService("payment-service");
        alert.setMessage("timeout");
        alert.setTimestamp(java.time.LocalDateTime.of(2026, 6, 2, 22, 11, 29));

        builder.setPublicBaseUrl("http://localhost:8080");

        String body = builder.buildPlainTextEmail(alert, "Dev User", null);

        assertThat(body).contains("http://localhost:8080/api/alerts/12345678-1234-1234-1234-123456789abc/jira");
    }

    @Test
    void buildHtmlEmail_withAlert_usesPublicBaseUrl() {
        com.kovanlabs.notificationservice.model.Alert alert = new com.kovanlabs.notificationservice.model.Alert();
        alert.setId(java.util.UUID.fromString("12345678-1234-1234-1234-123456789abc"));
        alert.setSeverity("LOW");
        alert.setService("payment-service");
        alert.setMessage("timeout");
        alert.setTimestamp(java.time.LocalDateTime.of(2026, 6, 2, 22, 11, 29));

        builder.setPublicBaseUrl("http://localhost:8080");

        String body = builder.buildHtmlEmail(alert, "Dev User", null);

        assertThat(body).contains("href=\"http://localhost:8080/api/alerts/12345678-1234-1234-1234-123456789abc/jira\"");
    }
}