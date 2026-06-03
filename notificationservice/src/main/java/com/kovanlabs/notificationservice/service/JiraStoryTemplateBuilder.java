package com.kovanlabs.notificationservice.service;

import org.springframework.stereotype.Component;
import com.kovanlabs.notificationservice.dto.AlertRequest;

@Component
public class JiraStoryTemplateBuilder {

    private static final String DESCRIPTION_TEMPLATE = """
            # Incident Summary

            Alert Name:
            {{alertName}}

            Service:
            {{serviceName}}

            Priority:
            {{priority}}

            Triggered At:
            {{triggeredAt}}

            # Alert Details

            Alert Rule:
            {{alertRule}}

            Observed Value:
            {{observedValue}}

            Threshold:
            {{threshold}}

            Time Window:
            {{timeWindow}}

            # Error Summary

            Error Count:
            {{errorCount}}

            Top Errors:
            {{topErrors}}

            # Recommended Investigation

            1. Verify service health.
            2. Review recent deployments.
            3. Review logs.
            4. Verify downstream dependencies.
            5. Check infrastructure resources.

            # LAS References

            Alert Id:
            {{alertId}}

            Alert Link:
            {{alertUrl}}
            """;

    public String buildSummary(AlertRequest alert) {
        String priority = alert.priority() != null ? alert.priority().trim().toUpperCase() : "UNKNOWN";
        String service = alert.serviceName() != null ? alert.serviceName().trim() : "Unknown Service";
        String name = alert.alertName() != null ? alert.alertName().trim() : "Alert";
        return "[" + priority + "] " + service + " - " + name;
    }

    public String buildDescription(AlertRequest alert) {
        if (alert == null) {
            return "";
        }
        return DESCRIPTION_TEMPLATE
                .replace("{{alertName}}", safeString(alert.alertName()))
                .replace("{{serviceName}}", safeString(alert.serviceName()))
                .replace("{{priority}}", safeString(alert.priority()))
                .replace("{{triggeredAt}}", safeString(alert.triggeredAt()))
                .replace("{{alertRule}}", safeString(alert.alertRule()))
                .replace("{{observedValue}}", safeString(alert.observedValue()))
                .replace("{{threshold}}", safeString(alert.threshold()))
                .replace("{{timeWindow}}", safeString(alert.timeWindow()))
                .replace("{{errorCount}}", alert.errorCount() != null ? String.valueOf(alert.errorCount()) : "N/A")
                .replace("{{topErrors}}", safeString(alert.topErrors()))
                .replace("{{alertId}}", safeString(alert.alertId()))
                .replace("{{alertUrl}}", safeString(alert.alertUrl()));
    }

    private String safeString(String val) {
        return (val == null || val.isBlank()) ? "N/A" : val.trim();
    }
}
