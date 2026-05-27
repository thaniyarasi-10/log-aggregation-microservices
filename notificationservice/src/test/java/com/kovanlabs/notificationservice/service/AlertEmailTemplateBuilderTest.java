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
}