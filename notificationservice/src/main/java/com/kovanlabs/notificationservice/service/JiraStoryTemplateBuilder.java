package com.kovanlabs.notificationservice.service;

import org.springframework.stereotype.Component;
import com.kovanlabs.notificationservice.dto.AlertRequest;

@Component
public class JiraStoryTemplateBuilder {

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

        String service = (alert.serviceName() != null && !alert.serviceName().isBlank()) ? alert.serviceName().trim() : "unknown-service";
        String priority = (alert.priority() != null && !alert.priority().isBlank()) ? alert.priority().trim().toUpperCase() : "UNKNOWN";
        String rawAlertName = alert.alertName();
        String alertNameFormatted = formatAlertName(rawAlertName);

        // Sentence 1: The service reported a priority severity alertName.
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("The %s reported a %s severity %s.", service, priority, alertNameFormatted));

        // Sentence 2: Error details (if available and different from alertName)
        String topErrors = alert.topErrors();
        boolean hasTopErrors = topErrors != null && !topErrors.isBlank() && !"N/A".equalsIgnoreCase(topErrors.trim());
        boolean topErrorsDiffers = hasTopErrors && (rawAlertName == null || !topErrors.trim().equalsIgnoreCase(rawAlertName.trim()));
        if (topErrorsDiffers) {
            sb.append(String.format(" The detected error details indicate: %s.", topErrors.trim()));
        }

        // Sentence 3: Impact statement based on severity
        String impactStatement;
        if ("CRITICAL".equals(priority) || "HIGH".equals(priority)) {
            impactStatement = "This issue may impact application availability and request processing.";
        } else if ("MEDIUM".equals(priority) || "WARNING".equals(priority)) {
            impactStatement = "This issue may affect service performance or user experience if left unresolved.";
        } else {
            impactStatement = "This issue is currently classified as low severity but should be monitored.";
        }
        sb.append(" ").append(impactStatement);

        // Sentence 4: AI Analysis/Suggested Fix (if available) or Rule-based recommendations
        String aiSuggestion = findAiSuggestion(alert);
        if (aiSuggestion != null) {
            // Include AI suggestions
            String formattedAi = formatAiSuggestion(aiSuggestion);
            sb.append(" ").append(formattedAi);
        } else {
            // Fallback recommendation
            String fallback = getFallbackRecommendation(rawAlertName, topErrors, alert.alertRule());
            sb.append(" ").append(fallback);
        }

        // Ensure description sentences are bounded between 2-5 sentences.
        return limitSentences(sb.toString().trim(), 5);
    }

    private String formatAlertName(String alertName) {
        if (alertName == null || alertName.isBlank()) {
            return "incident";
        }
        String trimmed = alertName.trim();
        String formatted = trimmed;
        if (trimmed.toLowerCase().endsWith("failed")) {
            formatted = trimmed.substring(0, trimmed.length() - 6).trim() + " failure";
        }

        // Split by whitespace and lowercase words unless they are acronyms
        String[] words = formatted.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (i > 0) {
                sb.append(" ");
            }
            if (isAcronym(word)) {
                sb.append(word);
            } else {
                sb.append(word.toLowerCase());
            }
        }
        return sb.toString();
    }

    private boolean isAcronym(String word) {
        if (word.length() < 2) {
            return false;
        }
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (Character.isLetter(c) && !Character.isUpperCase(c)) {
                return false;
            }
        }
        return true;
    }

    private String findAiSuggestion(AlertRequest alert) {
        java.util.List<String> candidates = java.util.Arrays.asList(
                alert.topErrors(),
                alert.alertRule(),
                alert.observedValue(),
                alert.threshold()
        );
        for (String val : candidates) {
            if (val == null || val.isBlank()) {
                continue;
            }
            String lower = val.toLowerCase();
            if (lower.contains("ai suggestion") ||
                lower.contains("troubleshooting suggestion") ||
                lower.contains("root cause:") ||
                lower.contains("suggested fix:") ||
                lower.contains("remediation:") ||
                lower.contains("gemini analysis") ||
                lower.contains("gemini suggestion")) {
                return val;
            }
        }
        return null;
    }

    private String formatAiSuggestion(String aiSuggestion) {
        String trimmed = aiSuggestion.trim();
        // Clean prefix if present
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("ai suggestion:") || lower.startsWith("suggested fix:") || lower.startsWith("remediation:")) {
            int colonIndex = trimmed.indexOf(":");
            if (colonIndex != -1 && colonIndex < trimmed.length() - 1) {
                trimmed = trimmed.substring(colonIndex + 1).trim();
            }
        }

        // Uppercase first letter of suggestion
        if (trimmed.length() > 0 && Character.isLowerCase(trimmed.charAt(0))) {
            trimmed = Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
        }

        if (!trimmed.endsWith(".") && !trimmed.endsWith("!") && !trimmed.endsWith("?")) {
            trimmed += ".";
        }
        return trimmed;
    }

    private String getFallbackRecommendation(String alertName, String topErrors, String alertRule) {
        String combinedText = ((alertName != null ? alertName : "") + " " +
                                (topErrors != null ? topErrors : "") + " " +
                                (alertRule != null ? alertRule : "")).toLowerCase();

        if (combinedText.contains("database") || combinedText.contains("datasource") || combinedText.contains("sql") || combinedText.contains("hikari")) {
            return "Immediate investigation of database availability, connection pool configuration, and network connectivity is recommended.";
        } else if (combinedText.contains("nullpointer") || combinedText.contains("null pointer")) {
            return "A NullPointerException was detected. It is recommended to check the stack trace, verify null checks in the codebase, and inspect recent code changes around the service.";
        } else if (combinedText.contains("outofmemory") || combinedText.contains("memory") || combinedText.contains("heap") || combinedText.contains("garbage collection")) {
            return "An OutOfMemoryError or memory pressure was detected. It is recommended to monitor the JVM heap usage, analyze garbage collection logs, and inspect recent memory configuration changes.";
        } else if (combinedText.contains("timeout")) {
            return "A request or connection timeout occurred. It is recommended to verify downstream service response times, check network latency, and review client timeout settings.";
        } else if (combinedText.contains("kafka") || combinedText.contains("broker") || combinedText.contains("consumer")) {
            return "A message broker issue was detected. It is recommended to verify Kafka broker status, inspect consumer lag, and check network connectivity to the Kafka cluster.";
        } else if (combinedText.contains("refused") || combinedText.contains("connection refused")) {
            return "A connection refused error was detected. It is recommended to check if the target service is running, verify port configuration, and inspect firewall/security group settings.";
        } else {
            return "Immediate investigation of service logs, recent deployment history, and system resource metrics is recommended.";
        }
    }

    private String limitSentences(String text, int maxSentences) {
        if (text == null || text.isBlank()) {
            return "";
        }
        // Split by sentence boundaries: period, exclamation, or question mark followed by space
        String[] sentences = text.split("(?<=[.!?])\\s+");
        if (sentences.length <= maxSentences) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxSentences; i++) {
            if (i > 0) {
                sb.append(" ");
            }
            sb.append(sentences[i]);
        }
        return sb.toString();
    }
}
