package com.kovanlabs.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import com.kovanlabs.notificationservice.dto.AlertNotificationRequest;
import com.kovanlabs.notificationservice.model.NotificationPreference;
import com.kovanlabs.notificationservice.model.Alert;
import com.kovanlabs.notificationservice.repository.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class AlertNotificationServiceTest {

    @Mock private ObjectProvider<JavaMailSender> mailSenderProvider;
    @Mock private NotificationPreferenceService preferenceService;
    @Mock private AlertEmailTemplateBuilder templateBuilder;
    @Mock private JiraStoryService jiraStoryService;
    @Mock private UserServiceClient userServiceClient;
    @Mock private JavaMailSender mailSender;
    @Mock private AlertRepository alertRepository;

    private AlertNotificationService service;

    @BeforeEach
    void setUp() {
        service = new AlertNotificationService(
                mailSenderProvider,
                preferenceService,
                templateBuilder,
                jiraStoryService,
                userServiceClient,
                alertRepository
        );
    }

    @Test
    void sendAlert_nullRequest_returnsFalse() {
        assertThat(service.sendAlert(null)).isFalse();
        verify(mailSenderProvider, never()).getIfAvailable();
        verify(alertRepository, never()).save(any(Alert.class));
        verify(jiraStoryService, never()).triggerJiraStoryCreation(any());
    }

    @Test
    void sendAlert_disabledPreference_skipsSend() {
        when(userServiceClient.getUserIdByEmail("dev@test.com")).thenReturn("user-123");
        when(preferenceService.getOrCreatePreference("user-123")).thenReturn(preference(false));

        boolean sent = service.sendAlert(new AlertNotificationRequest(
                "dev@test.com", "Dev User", "payment-service", "CRITICAL", "DB timeout", 5));

        assertThat(sent).isFalse();
        verify(mailSenderProvider, never()).getIfAvailable();
        verify(alertRepository).save(any(Alert.class));
        verify(jiraStoryService).triggerJiraStoryCreation(any());
    }

    @Test
    void sendAlert_enabledPreference_sendsEmail() throws Exception {
        when(userServiceClient.getUserIdByEmail("dev@test.com")).thenReturn("user-123");
        when(preferenceService.getOrCreatePreference("user-123")).thenReturn(preference(true));
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        when(templateBuilder.buildPlainTextEmail(any(), anyString())).thenReturn("plain text");
        when(templateBuilder.buildHtmlEmail(any(), anyString())).thenReturn("<html>ok</html>");

        boolean sent = service.sendAlert(new AlertNotificationRequest(
                "dev@test.com", "Dev User", "payment-service", "CRITICAL", "DB timeout", 5));

        assertThat(sent).isTrue();
        verify(mailSender).send(any(MimeMessage.class));
        verify(alertRepository).save(any(Alert.class));
        verify(jiraStoryService).triggerJiraStoryCreation(any());
    }

    @Test
    void sendAlert_nullOrBlankEmail_savesAlertAndJiraButSkipsEmail() {
        boolean sent = service.sendAlert(new AlertNotificationRequest(
                null, "Dev User", "payment-service", "CRITICAL", "DB timeout", 5));

        assertThat(sent).isTrue();
        verify(alertRepository).save(any(Alert.class));
        verify(jiraStoryService).triggerJiraStoryCreation(any());
        verify(mailSenderProvider, never()).getIfAvailable();
    }

    private NotificationPreference preference(boolean enabled) {
        NotificationPreference preference = new NotificationPreference();
        preference.setEmailEnabled(enabled);
        return preference;
    }
}