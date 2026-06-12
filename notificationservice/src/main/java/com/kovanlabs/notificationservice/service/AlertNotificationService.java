package com.kovanlabs.notificationservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.kovanlabs.notificationservice.dto.AlertNotificationRequest;
import com.kovanlabs.notificationservice.model.Alert;
import com.kovanlabs.notificationservice.model.JiraStory;
import com.kovanlabs.notificationservice.repository.AlertRepository;
import com.kovanlabs.notificationservice.repository.UserJiraMappingRepository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class AlertNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertNotificationService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final NotificationPreferenceService preferenceService;
    private final AlertEmailTemplateBuilder templateBuilder;
    private final JiraStoryService jiraStoryService;
    private final UserServiceClient userServiceClient;
    private final AlertRepository alertRepository;
    private final UserJiraMappingRepository userJiraMappingRepository;

    @Value("${alert.thresholds.critical.high:2}")
    private int criticalHighThreshold = 2;

    @Value("${alert.thresholds.normal.high:5}")
    private int normalHighThreshold = 5;

    @Value("${alert.critical-keywords:database connection failed,unable to connect to database,kafka broker unavailable,outofmemoryerror,connection refused,service unavailable}")
    private List<String> criticalKeywords = java.util.Arrays.asList(
            "database connection failed",
            "unable to connect to database",
            "kafka broker unavailable",
            "outofmemoryerror",
            "connection refused",
            "service unavailable"
    );

    public AlertNotificationService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            NotificationPreferenceService preferenceService,
            AlertEmailTemplateBuilder templateBuilder,
            JiraStoryService jiraStoryService,
            UserServiceClient userServiceClient,
            AlertRepository alertRepository,
            UserJiraMappingRepository userJiraMappingRepository) {
        this.mailSenderProvider = mailSenderProvider;
        this.preferenceService = preferenceService;
        this.templateBuilder = templateBuilder;
        this.jiraStoryService = jiraStoryService;
        this.userServiceClient = userServiceClient;
        this.alertRepository = alertRepository;
        this.userJiraMappingRepository = userJiraMappingRepository;
    }

    @PostConstruct
    public void validateMailSenderConfig() {
        boolean configured = mailSenderProvider.getIfAvailable() != null;
        LOGGER.info("Mail sender configured = {}", configured);
    }

    private boolean isCriticalError(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lowerMessage = message.toLowerCase();
        return criticalKeywords.stream()
                .anyMatch(keyword -> lowerMessage.contains(keyword.toLowerCase().trim()));
    }

    public boolean sendAlert(AlertNotificationRequest request) {
        LOGGER.info("sendAlert() entered");
        if (request == null) {
            LOGGER.warn("Skipping alert processing: request is null");
            return false;
        }

        LOGGER.info("Alert event received: {}", request);

        String serviceName = request.service() != null ? request.service().trim() : "Unknown Service";
        String rawMessage = request.message() != null ? request.message().trim() : "Error Triggered";
        int incomingCount = Math.max(1, request.count());

        // Loop prevention: ignore self-referential alerts from notification-service/logservice about notifications or Jira creation
        String rawMessageLower = rawMessage.toLowerCase();
        boolean isSelfService = "notification-service".equalsIgnoreCase(serviceName) || "logservice".equalsIgnoreCase(serviceName);
        if (isSelfService && (
            rawMessageLower.contains("failed to create jira story") ||
            rawMessageLower.contains("jira story creation failed") ||
            rawMessageLower.contains("failed to send alert trigger") ||
            rawMessageLower.contains("failed to persist alert") ||
            rawMessageLower.contains("graceful jira story creation failed") ||
            rawMessageLower.contains("jira connection/operation failure")
        )) {
            LOGGER.warn("Dropping self-referential/loop-prone alert to prevent infinite loop: service={}, message={}", serviceName, rawMessage);
            return true;
        }

        // 1. Perform signature lookup restricted by service within the last 24 hours
        LOGGER.info("Alert aggregation started for service: {}", serviceName);
        LocalDateTime limit = LocalDateTime.now().minusHours(24);
        List<Alert> recentAlerts = alertRepository.findAllByServiceIgnoreCaseAndTimestampAfter(serviceName, limit);
        String incomingSignature = ErrorFingerprinter.getSignature(rawMessage);

        Alert alert = recentAlerts.stream()
                .filter(a -> ErrorFingerprinter.getSignature(a.getMessage()).equals(incomingSignature))
                .findFirst()
                .orElse(null);

        boolean isNew = (alert == null);
        LOGGER.info("Aggregation lookup result: {} alert found", isNew ? "no existing" : "existing");

        if (isNew) {
            alert = new Alert();
            alert.setId(UUID.randomUUID());
            alert.setService(serviceName);
            alert.setMessage(rawMessage);
            alert.setCount(incomingCount);
        } else {
            alert.setCount(alert.getCount() + incomingCount);
        }
        alert.setTimestamp(LocalDateTime.now());
        LOGGER.info("Alert count updated: service={}, message={}, count={}", serviceName, rawMessage, alert.getCount());

        // 2. Classify severity based on count and error type
        boolean isCritical = isCriticalError(rawMessage);
        LOGGER.info("Critical keyword match result for message '{}': {}", rawMessage, isCritical);

        int highThreshold = isCritical ? criticalHighThreshold : normalHighThreshold;
        String resolvedSeverity = alert.getCount() >= highThreshold ? "HIGH" : "LOW";
        alert.setSeverity(resolvedSeverity);
        LOGGER.info("Calculated severity: {} (isCritical = {}, count = {})", resolvedSeverity, isCritical, alert.getCount());

        // 3. Save alert to database
        UUID alertId = alert.getId();
        LOGGER.info("Saving alert with ID: {} to database", alertId);
        try {
            alertRepository.save(alert);
            LOGGER.info("Alert saved: {}", alert);
        } catch (Exception ex) {
            LOGGER.error("Failed to persist alert: {}", ex.getMessage(), ex);
        }

        boolean isLow = "LOW".equalsIgnoreCase(resolvedSeverity);
        boolean isHigh = "HIGH".equalsIgnoreCase(resolvedSeverity);

        JiraStory jiraStory = null;

        // 4. Trigger Jira automation for HIGH severity alerts automatically
        if (isHigh) {
            if (rawMessage.toLowerCase().contains("failed to create jira story") ||
                rawMessage.toLowerCase().contains("jira story creation failed") ||
                rawMessage.toLowerCase().contains("jira connection/operation failure") ||
                (rawMessage.toLowerCase().contains("bad request") && rawMessage.toLowerCase().contains("jira"))) {
                LOGGER.warn("Skipping automatic Jira story creation to prevent infinite loop for Jira-related alert: {}", rawMessage);
            } else {
                try {
                    LOGGER.info("Attempting to auto-create Jira story for alert ID: {}", alertId);
                    jiraStoryService.createJiraStoryForAlert(alertId.toString());
                    jiraStory = jiraStoryService.getJiraStoryByAlertId(alertId.toString());
                    LOGGER.info("Jira story auto-created successfully for alert ID: {}", alertId);
                } catch (Exception ex) {
                    LOGGER.warn("Graceful Jira Story creation failed: {}", ex.getMessage(), ex);
                }
            }
        } else if (!isLow) {
            // Keep original default behavior for other severities (e.g. CRITICAL/MEDIUM in old tests) to prevent breaking backwards compatibility
            if (rawMessage.toLowerCase().contains("failed to create jira story") ||
                rawMessage.toLowerCase().contains("jira story creation failed") ||
                rawMessage.toLowerCase().contains("jira connection/operation failure") ||
                (rawMessage.toLowerCase().contains("bad request") && rawMessage.toLowerCase().contains("jira"))) {
                LOGGER.warn("Skipping legacy Jira story creation to prevent infinite loop for Jira-related alert: {}", rawMessage);
            } else {
                try {
                    LOGGER.info("Triggering legacy Jira story creation for alert ID: {}", alertId);
                    com.kovanlabs.notificationservice.dto.AlertRequest jiraRequest = new com.kovanlabs.notificationservice.dto.AlertRequest(
                            alertId.toString(),
                            rawMessage,
                            serviceName,
                            request.severity() != null ? request.severity() : "MEDIUM",
                            java.time.Instant.now().toString(),
                            rawMessage,
                            "N/A",
                            "N/A",
                            "N/A",
                            alert.getCount(),
                            rawMessage,
                            "N/A"
                    );
                    jiraStoryService.triggerJiraStoryCreation(jiraRequest);
                    LOGGER.info("Legacy Jira story creation completed for alert ID: {}", alertId);
                } catch (Exception ex) {
                    LOGGER.warn("Graceful legacy Jira Story creation failed: {}", ex.getMessage(), ex);
                }
            }
        }

        // Email Notification
        LOGGER.info("Resolving email recipients for service: {}", serviceName);
        List<String> recipients = userJiraMappingRepository.findEmailsByServiceNameIgnoreCase(serviceName);
        if (recipients.isEmpty()) {
            LOGGER.warn("No owners mapped to service '{}' in database service ownership mapping", serviceName);
            if (request.recipientEmail() != null && !request.recipientEmail().isBlank()) {
                LOGGER.info("Falling back to request recipientEmail: {}", request.recipientEmail());
                recipients = Collections.singletonList(request.recipientEmail().trim().toLowerCase());
            }
        } else {
            LOGGER.info("Resolved {} mapped user email(s) for service '{}': {}", recipients.size(), serviceName, recipients);
        }

        if (recipients.isEmpty()) {
            LOGGER.warn("Skipping email notification: no recipients resolved for service '{}' and request recipient email is blank", serviceName);
            return true;
        }

        boolean anySent = false;

        for (String recipientEmail : recipients) {
            String trimmedEmail = recipientEmail.trim().toLowerCase();
            String userId = userServiceClient.getUserIdByEmail(trimmedEmail);
            String lookupKey = userId != null ? userId : "email-" + trimmedEmail;

            if (!preferenceService.getOrCreatePreference(lookupKey).isEmailEnabled()) {
                LOGGER.info("Skipping email for {} because preference is disabled for key {}", trimmedEmail, lookupKey);
                continue;
            }

            JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
            if (mailSender == null) {
                LOGGER.warn("Skipping email for {} because mail sender is unavailable (preview only)", trimmedEmail);
                anySent = true;
                continue;
            }

            try {
                LOGGER.info("Attempting to send alert email to {}...", trimmedEmail);
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setTo(trimmedEmail);
                helper.setSubject(buildSubject(request, resolvedSeverity, alert.getCount()));

                String plainText;
                String htmlContent;

                if (isLow || isHigh) {
                    plainText = templateBuilder.buildPlainTextEmail(alert, request.recipientName(), jiraStory);
                    htmlContent = templateBuilder.buildHtmlEmail(alert, request.recipientName(), jiraStory);
                } else {
                    plainText = templateBuilder.buildPlainTextEmail(request, request.recipientName());
                    htmlContent = templateBuilder.buildHtmlEmail(request, request.recipientName());
                }

                helper.setText(plainText, htmlContent);
                mailSender.send(message);
                LOGGER.info("Email sent successfully to {}", trimmedEmail);
                anySent = true;
            } catch (MailException | MessagingException ex) {
                LOGGER.error("Failed to send email to {}: {}", trimmedEmail, ex.getMessage(), ex);
            }
        }

        return anySent;
    }

    private String buildSubject(AlertNotificationRequest request) {
        String severity = request.severity() == null || request.severity().isBlank()
                ? "ALERT"
                : request.severity().trim().toUpperCase();
        String service = request.service() == null || request.service().isBlank()
                ? "Unknown Service"
                : request.service().trim();
        return "[" + severity + "] Alert: " + service + " - " + request.count() + " error(s) detected";
    }

    private String buildSubject(AlertNotificationRequest request, String severity, int count) {
        String service = request.service() == null || request.service().isBlank()
                ? "Unknown Service"
                : request.service().trim();
        return "[" + severity + "] Alert: " + service + " - " + count + " error(s) detected";
    }
}