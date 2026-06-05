package com.kovanlabs.notificationservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.kovanlabs.notificationservice.dto.AlertNotificationRequest;
import com.kovanlabs.notificationservice.model.Alert;
import com.kovanlabs.notificationservice.model.JiraStory;
import java.time.format.DateTimeFormatter;

@Service
public class AlertEmailTemplateBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertEmailTemplateBuilder.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl = "http://localhost:8080";

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

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

    public String buildPlainTextEmail(Alert alert, String recipientName, JiraStory jiraStory) {
        StringBuilder builder = new StringBuilder();
        builder.append("Hello ").append(defaultName(recipientName)).append(",\n\n");
        builder.append("An alert was generated for service '")
                .append(defaultText(alert.getService(), "Unknown Service"))
                .append("'.\n");
        builder.append("Severity: ").append(defaultText(alert.getSeverity(), "LOW")).append('\n');
        builder.append("Message: ").append(defaultText(alert.getMessage(), "No message provided")).append('\n');
        builder.append("Count: ").append(alert.getCount()).append('\n');
        builder.append("Timestamp: ").append(alert.getTimestamp() != null ? alert.getTimestamp().format(DATE_TIME_FORMATTER) : "N/A").append('\n');

        if ("LOW".equalsIgnoreCase(alert.getSeverity())) {
            String jiraUrl = getPublicBaseUrl() + "/api/alerts/" + alert.getId() + "/jira";
            LOGGER.info("Generated Jira action URL (plain text): {}", jiraUrl);
            builder.append("\nYou can create a Jira Story for this alert by visiting: ").append(jiraUrl).append("\n");
        } else if ("HIGH".equalsIgnoreCase(alert.getSeverity())) {
            builder.append("\nJira Details:\n");
            if (jiraStory != null) {
                builder.append("- Jira Key: ").append(jiraStory.getJiraIssueKey()).append('\n');
                builder.append("- Jira URL: ").append(jiraStory.getJiraIssueUrl()).append('\n');
                if (jiraStory.getJiraAssigneeAccountId() != null) {
                    builder.append("- Assignee Name: ").append(jiraStory.getJiraAssigneeName()).append('\n');
                } else {
                    builder.append("- Assignee Name: Unassigned\n");
                    builder.append("Warning: Assignee could not be resolved because there is no service owner/Jira user mapping configured.\n");
                }
            } else {
                builder.append("- Jira Key: N/A\n");
                builder.append("- Jira URL: N/A\n");
                builder.append("- Assignee Name: Unassigned\n");
                builder.append("Warning: Assignee could not be resolved because there is no service owner/Jira user mapping configured.\n");
            }
        }
        return builder.toString();
    }

    public String buildHtmlEmail(Alert alert, String recipientName, JiraStory jiraStory) {
        String severity = defaultText(alert.getSeverity(), "LOW");
        String service = defaultText(alert.getService(), "Unknown Service");
        String message = defaultText(alert.getMessage(), "No message provided");
        String formattedTimestamp = alert.getTimestamp() != null ? alert.getTimestamp().format(DATE_TIME_FORMATTER) : "N/A";

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style=\"font-family: Arial, sans-serif; color: #333; line-height: 1.6; background-color: #f9f9f9; padding: 20px;\">");
        sb.append("<div style=\"max-width: 600px; margin: 0 auto; background-color: #ffffff; padding: 30px; border-radius: 8px; box-shadow: 0 4px 10px rgba(0,0,0,0.05); border: 1px solid #eef2f5;\">");
        sb.append("<h2 style=\"margin-top: 0; color: #1e293b;\">Hello ").append(defaultName(recipientName)).append("</h2>");
        sb.append("<p>An alert was generated for service <strong>").append(service).append("</strong>.</p>");
        sb.append("<table style=\"width: 100%; border-collapse: collapse; margin-bottom: 20px;\">");
        sb.append("<tr><td style=\"padding: 8px 0; font-weight: bold; width: 120px;\">Severity:</td><td style=\"padding: 8px 0;\">").append(severity).append("</td></tr>");
        sb.append("<tr><td style=\"padding: 8px 0; font-weight: bold;\">Count:</td><td style=\"padding: 8px 0;\">").append(alert.getCount()).append("</td></tr>");
        sb.append("<tr><td style=\"padding: 8px 0; font-weight: bold;\">Message:</td><td style=\"padding: 8px 0;\">").append(message).append("</td></tr>");
        sb.append("<tr><td style=\"padding: 8px 0; font-weight: bold;\">Timestamp:</td><td style=\"padding: 8px 0;\">").append(formattedTimestamp).append("</td></tr>");
        sb.append("</table>");

        if ("LOW".equalsIgnoreCase(severity)) {
            String jiraUrl = getPublicBaseUrl() + "/api/alerts/" + alert.getId() + "/jira";
            LOGGER.info("Generated Jira action URL (HTML): {}, href rendered: {}", jiraUrl, jiraUrl);
            sb.append("<div style=\"margin-top: 30px; text-align: center;\">");
            sb.append("<a href=\"").append(jiraUrl).append("\" ")
              .append("style=\"background: linear-gradient(135deg, #4f46e5, #6366f1); color: #ffffff; text-decoration: none; padding: 12px 24px; font-weight: bold; border-radius: 6px; box-shadow: 0 4px 6px rgba(99, 102, 241, 0.2); display: inline-block;\">")
              .append("Create Jira Story</a>");
            sb.append("</div>");
        } else if ("HIGH".equalsIgnoreCase(severity)) {
            sb.append("<h3 style=\"color: #1e293b; border-bottom: 1px solid #eef2f5; padding-bottom: 8px; margin-top: 25px;\">Jira Automation</h3>");
            sb.append("<table style=\"width: 100%; border-collapse: collapse;\">");
            
            String jiraKey = "N/A";
            String jiraUrl = "#";
            String assignee = "Unassigned";
            boolean hasMapping = false;

            if (jiraStory != null) {
                jiraKey = jiraStory.getJiraIssueKey() != null ? jiraStory.getJiraIssueKey() : "N/A";
                jiraUrl = jiraStory.getJiraIssueUrl() != null ? jiraStory.getJiraIssueUrl() : "#";
                if (jiraStory.getJiraAssigneeAccountId() != null) {
                    assignee = jiraStory.getJiraAssigneeName();
                    hasMapping = true;
                }
            }

            sb.append("<tr><td style=\"padding: 8px 0; font-weight: bold; width: 120px;\">Jira Key:</td><td style=\"padding: 8px 0;\">").append(jiraKey).append("</td></tr>");
            sb.append("<tr><td style=\"padding: 8px 0; font-weight: bold;\">Jira URL:</td><td style=\"padding: 8px 0;\">");
            if (!"#".equals(jiraUrl)) {
                sb.append("<a href=\"").append(jiraUrl).append("\" style=\"color: #4f46e5; text-decoration: none;\">").append(jiraUrl).append("</a>");
            } else {
                sb.append("N/A");
            }
            sb.append("</td></tr>");
            sb.append("<tr><td style=\"padding: 8px 0; font-weight: bold;\">Assignee Name:</td><td style=\"padding: 8px 0;\">").append(assignee).append("</td></tr>");
            sb.append("</table>");

            if (!hasMapping) {
                sb.append("<div style=\"margin-top: 15px; padding: 12px; background-color: #fef3c7; border-left: 4px solid #d97706; border-radius: 4px; color: #92400e; font-size: 14px;\">");
                sb.append("<strong>Warning:</strong> Assignee could not be resolved because there is no service owner/Jira user mapping configured.");
                sb.append("</div>");
            }
        }
        sb.append("</div></div></body></html>");
        return sb.toString();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String defaultName(String value) {
        return value == null || value.isBlank() ? "team member" : value;
    }
}