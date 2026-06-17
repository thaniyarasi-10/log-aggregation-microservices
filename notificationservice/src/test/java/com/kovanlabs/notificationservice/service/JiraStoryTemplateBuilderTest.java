package com.kovanlabs.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.kovanlabs.notificationservice.dto.AlertRequest;

class JiraStoryTemplateBuilderTest {

    private JiraStoryTemplateBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new JiraStoryTemplateBuilder();
    }

    @Test
    void buildSummary_validAlert_returnsFormattedSummary() {
        AlertRequest alert = new AlertRequest(
                "alert-123",
                "Error Threshold Breached",
                "payment-service",
                "CRITICAL",
                "2026-05-29T12:00:00Z",
                "HTTP 5xx > 1%",
                "1.5%",
                "1.0%",
                "5m",
                10,
                "NullPointerException",
                "http://las/alerts/123"
        );

        String summary = builder.buildSummary(alert);
        assertThat(summary).isEqualTo("[CRITICAL] payment-service - Error Threshold Breached");
    }

    @Test
    void buildDescription_validAlert_replacesAllPlaceholders() {
        AlertRequest alert = new AlertRequest(
                "alert-123",
                "Error Threshold Breached",
                "payment-service",
                "CRITICAL",
                "2026-05-29T12:00:00Z",
                "HTTP 5xx > 1%",
                "1.5%",
                "1.0%",
                "5m",
                10,
                "NullPointerException",
                "http://las/alerts/123"
        );

        String description = builder.buildDescription(alert);
        assertThat(description).isEqualTo(
                "The payment-service reported a CRITICAL severity error threshold breached. " +
                "The detected error details indicate: NullPointerException. " +
                "This issue may impact application availability and request processing. " +
                "A NullPointerException was detected. It is recommended to check the stack trace, " +
                "verify null checks in the codebase, and inspect recent code changes around the service."
        );
    }

    @Test
    void buildDescription_nullValues_replacesWithNA() {
        AlertRequest alert = new AlertRequest(
                "alert-123",
                null,
                "payment-service",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        String description = builder.buildDescription(alert);
        assertThat(description).isEqualTo(
                "The payment-service reported a UNKNOWN severity incident. " +
                "This issue is currently classified as low severity but should be monitored. " +
                "Immediate investigation of service logs, recent deployment history, and system resource metrics is recommended."
        );
    }

    @Test
    void buildDescription_withAiSuggestion_includesAiSuggestion() {
        AlertRequest alert = new AlertRequest(
                "alert-123",
                "Database Connection Failed",
                "gateway-service",
                "HIGH",
                "2026-06-08T13:22:55",
                "AI Suggestion: Increase connection pool size and check db server health.",
                "N/A",
                "N/A",
                "N/A",
                1,
                "Database Connection Failed",
                "N/A"
        );

        String description = builder.buildDescription(alert);
        assertThat(description).isEqualTo(
                "The gateway-service reported a HIGH severity database connection failure. " +
                "This issue may impact application availability and request processing. " +
                "Increase connection pool size and check db server health."
        );
    }
}
