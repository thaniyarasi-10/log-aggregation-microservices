package com.kovanlabs.notificationservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.kovanlabs.notificationservice.dto.AlertNotificationRequest;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class AlertNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertNotificationService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final NotificationPreferenceService preferenceService;
    private final AlertEmailTemplateBuilder templateBuilder;

    public AlertNotificationService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            NotificationPreferenceService preferenceService,
            AlertEmailTemplateBuilder templateBuilder) {
        this.mailSenderProvider = mailSenderProvider;
        this.preferenceService = preferenceService;
        this.templateBuilder = templateBuilder;
    }

    public boolean sendAlert(AlertNotificationRequest request) {
        if (request == null || request.recipientEmail() == null || request.recipientEmail().isBlank()) {
            return false;
        }

        String recipientEmail = request.recipientEmail().trim().toLowerCase();
        if (!preferenceService.getOrCreatePreference(recipientEmail).isEmailEnabled()) {
            LOGGER.debug("Email skipped because preference disabled for {}", recipientEmail);
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