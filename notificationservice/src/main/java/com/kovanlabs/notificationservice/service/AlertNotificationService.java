package com.kovanlabs.notificationservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.kovanlabs.notificationservice.dto.AlertNotificationRequest;
import com.kovanlabs.notificationservice.model.Alert;
import com.kovanlabs.notificationservice.repository.AlertRepository;

import java.time.LocalDateTime;
import java.util.UUID;

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

    public AlertNotificationService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            NotificationPreferenceService preferenceService,
            AlertEmailTemplateBuilder templateBuilder,
            JiraStoryService jiraStoryService,
            UserServiceClient userServiceClient,
            AlertRepository alertRepository) {
        this.mailSenderProvider = mailSenderProvider;
        this.preferenceService = preferenceService;
        this.templateBuilder = templateBuilder;
        this.jiraStoryService = jiraStoryService;
        this.userServiceClient = userServiceClient;
        this.alertRepository = alertRepository;
    }

    public boolean sendAlert(AlertNotificationRequest request) {
        if (request == null) {
            return false;
        }

        UUID alertId = UUID.randomUUID();

        // Save alert to database
        try {
            Alert alert = new Alert();
            alert.setId(alertId);
            alert.setService(request.service() != null ? request.service() : "Unknown Service");
            alert.setMessage(request.message() != null ? request.message() : "Error Triggered");
            alert.setCount(request.count());
            alert.setSeverity(request.severity() != null ? request.severity() : "MEDIUM");
            alert.setTimestamp(LocalDateTime.now());
            alertRepository.save(alert);
        } catch (Exception ex) {
            LOGGER.error("Failed to persist alert: {}", ex.getMessage(), ex);
        }

        // Trigger Jira automation gracefully as part of alert creation pipeline
        try {
            com.kovanlabs.notificationservice.dto.AlertRequest jiraRequest = new com.kovanlabs.notificationservice.dto.AlertRequest(
                    alertId.toString(),
                    request.message() != null ? request.message() : "Error Triggered",
                    request.service() != null ? request.service() : "Unknown Service",
                    request.severity() != null ? request.severity() : "MEDIUM",
                    java.time.Instant.now().toString(),
                    request.message(), // alertRule
                    "N/A", // observedValue
                    "N/A", // threshold
                    "N/A", // timeWindow
                    request.count(), // errorCount
                    request.message(), // topErrors
                    "N/A" // alertUrl
            );
            jiraStoryService.triggerJiraStoryCreation(jiraRequest);
        } catch (Exception ex) {
            LOGGER.error("Graceful Jira Story creation failed: {}", ex.getMessage(), ex);
        }

        if (request.recipientEmail() == null || request.recipientEmail().isBlank()) {
            LOGGER.info("No recipient email provided; skipping email notification");
            return true;
        }

        String recipientEmail = request.recipientEmail().trim().toLowerCase();
        String userId = userServiceClient.getUserIdByEmail(recipientEmail);
        String lookupKey = userId != null ? userId : "email-" + recipientEmail;

        if (!preferenceService.getOrCreatePreference(lookupKey).isEmailEnabled()) {
            LOGGER.debug("Email skipped because preference disabled for key {}", lookupKey);
            return false;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            LOGGER.info("Mail sender not configured; notification preview only for {}", recipientEmail);
            return true;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(recipientEmail);
            helper.setSubject(buildSubject(request));
            helper.setText(
                    templateBuilder.buildPlainTextEmail(request, request.recipientName()),
                    templateBuilder.buildHtmlEmail(request, request.recipientName()));
            mailSender.send(message);
            return true;
        } catch (MailException | MessagingException ex) {
            LOGGER.warn("Failed to send notification to {}: {}", recipientEmail, ex.getMessage());
            return false;
        }
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
}