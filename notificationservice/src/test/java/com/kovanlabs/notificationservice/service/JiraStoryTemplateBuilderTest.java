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
        assertThat(description)
                .contains("Alert Name:\nError Threshold Breached")
                .contains("Service:\npayment-service")
                .contains("Priority:\nCRITICAL")
                .contains("Triggered At:\n2026-05-29T12:00:00Z")
                .contains("Alert Rule:\nHTTP 5xx > 1%")
                .contains("Observed Value:\n1.5%")
                .contains("Threshold:\n1.0%")
                .contains("Time Window:\n5m")
                .contains("Error Count:\n10")
                .contains("Top Errors:\nNullPointerException")
                .contains("Alert Id:\nalert-123")
                .contains("Alert Link:\nhttp://las/alerts/123");
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
        assertThat(description)
                .contains("Alert Name:\nN/A")
                .contains("Priority:\nN/A")
                .contains("Error Count:\nN/A")
                .contains("Alert Link:\nN/A");
    }
}
