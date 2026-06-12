package com.kovanlabs.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.*;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.kovanlabs.notificationservice.dto.AlertNotificationRequest;
import com.kovanlabs.notificationservice.dto.JiraStoryResponse;
import com.kovanlabs.notificationservice.model.Alert;
import com.kovanlabs.notificationservice.model.JiraConfiguration;
import com.kovanlabs.notificationservice.model.JiraStory;
import com.kovanlabs.notificationservice.model.NotificationPreference;
import com.kovanlabs.notificationservice.model.UserJiraMapping;
import com.kovanlabs.notificationservice.repository.AlertRepository;
import com.kovanlabs.notificationservice.repository.JiraConfigurationRepository;
import com.kovanlabs.notificationservice.repository.JiraStoryRepository;
import com.kovanlabs.notificationservice.repository.UserJiraMappingRepository;

@ExtendWith(MockitoExtension.class)
class AlertJiraFlowTest {

    @Mock private ObjectProvider<JavaMailSender> mailSenderProvider;
    @Mock private NotificationPreferenceService preferenceService;
    @Mock private JiraStoryRepository jiraStoryRepository;
    @Mock private UserJiraMappingRepository userJiraMappingRepository;
    @Mock private JiraConfigurationRepository jiraConfigurationRepository;
    @Mock private PriorityDeadlineResolver priorityDeadlineResolver;
    @Mock private JiraClient jiraClient;
    @Mock private UserServiceClient userServiceClient;
    @Mock private JavaMailSender mailSender;
    @Mock private AlertRepository alertRepository;
    @Mock private JiraStoryTemplateBuilder jiraStoryTemplateBuilder;

    private AlertEmailTemplateBuilder templateBuilder;
    private JiraStoryService jiraStoryService;
    private AlertNotificationService alertNotificationService;

    private JiraConfiguration mockConfig;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        templateBuilder = new AlertEmailTemplateBuilder();
        jiraStoryService = new JiraStoryService(
                jiraStoryRepository,
                userJiraMappingRepository,
                jiraConfigurationRepository,
                priorityDeadlineResolver,
                jiraStoryTemplateBuilder,
                jiraClient,
                alertRepository
        );

        alertNotificationService = new AlertNotificationService(
                mailSenderProvider,
                preferenceService,
                templateBuilder,
                jiraStoryService,
                userServiceClient,
                alertRepository,
                userJiraMappingRepository
        );

        // Inject thresholds and keywords via ReflectionTestUtils
        ReflectionTestUtils.setField(alertNotificationService, "criticalHighThreshold", 2);
        ReflectionTestUtils.setField(alertNotificationService, "normalHighThreshold", 5);
        ReflectionTestUtils.setField(alertNotificationService, "criticalKeywords", 
                Arrays.asList("database connection failed", "unable to connect to database", "kafka broker unavailable", "outofmemoryerror", "connection refused", "service unavailable"));

        mockConfig = new JiraConfiguration();
        mockConfig.setId(UUID.randomUUID());
        mockConfig.setJiraBaseUrl("https://company.atlassian.net");
        mockConfig.setJiraEmail("arun@company.com");
        mockConfig.setJiraApiToken("fake-token");
        mockConfig.setJiraProjectKey("PAY");
        mockConfig.setActive(true);

        lenient().when(userJiraMappingRepository.findEmailsByServiceNameIgnoreCase(anyString()))
                .thenReturn(Collections.emptyList());
        lenient().when(jiraStoryTemplateBuilder.buildSummary(any())).thenReturn("Test Summary");
        lenient().when(jiraStoryTemplateBuilder.buildDescription(any())).thenReturn("Test Description");
    }

    private NotificationPreference preference(boolean enabled) {
        NotificationPreference preference = new NotificationPreference();
        preference.setEmailEnabled(enabled);
        return preference;
    }

    @Test
    void lowSeverityEmailSent_JiraNotAutoCreated() throws Exception {
        // Setup
        when(alertRepository.findByOrganizationIdAndServiceIgnoreCaseAndTimestampAfter(eq(orgId), eq("payment-service"), any()))
                .thenReturn(new ArrayList<>());
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(userServiceClient.getUserIdByEmail("dev@test.com")).thenReturn("user-123");
        when(preferenceService.getOrCreatePreference("user-123")).thenReturn(preference(true));
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        AlertNotificationRequest request = new AlertNotificationRequest(
                "dev@test.com", "Dev User", "payment-service", "LOW", "NullPointerException triggered", 1, orgId.toString());

        // Act
        boolean sent = alertNotificationService.sendAlert(request);

        // Assert
        assertThat(sent).isTrue();
        verify(alertRepository).save(any(Alert.class));
        verify(jiraClient, never()).createStory(any(), any(), any(), any(), any(), any(), any(), any());
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void highSeverityAutoJiraCreation_assignedToMappedUser() throws Exception {
        // Setup
        Alert existingAlert = new Alert();
        existingAlert.setId(UUID.randomUUID());
        existingAlert.setOrganizationId(orgId);
        existingAlert.setService("payment-service");
        existingAlert.setMessage("NullPointerException triggered");
        existingAlert.setCount(4); // Increments to 5, which escalates to HIGH
        existingAlert.setSeverity("LOW");
        existingAlert.setTimestamp(LocalDateTime.now().minusHours(1));

        when(alertRepository.findByOrganizationIdAndServiceIgnoreCaseAndTimestampAfter(eq(orgId), eq("payment-service"), any()))
                .thenReturn(Collections.singletonList(existingAlert));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(alertRepository.findById(existingAlert.getId())).thenReturn(Optional.of(existingAlert));

        when(jiraConfigurationRepository.findFirstByActiveTrue()).thenReturn(Optional.of(mockConfig));
        when(priorityDeadlineResolver.resolveDueDate("HIGH")).thenReturn(LocalDateTime.now().plusDays(1));

        // Mapping mock
        List<Object[]> owners = new ArrayList<>();
        owners.add(new Object[]{"user-primary", "Arun", true}); // is_primary = true
        when(userJiraMappingRepository.findOwnersByServiceNameIgnoreCase("payment-service")).thenReturn(owners);
        
        UserJiraMapping mapping = new UserJiraMapping();
        mapping.setUserId("user-primary");
        mapping.setJiraAccountId("jira-arun-123");
        mapping.setJiraDisplayName("Arun Kumar");
        mapping.setActive(true);
        when(userJiraMappingRepository.findByUserId("user-primary")).thenReturn(Optional.of(mapping));

        // Mock JiraClient
        JiraClient.JiraCreateIssueResponse jiraResp = new JiraClient.JiraCreateIssueResponse("10001", "PAY-12", "https://company.atlassian.net/browse/PAY-12");
        when(jiraClient.createStory(any(), any(), any(), any(), any(), any(), eq("jira-arun-123"), any())).thenReturn(jiraResp);

        when(userServiceClient.getUserIdByEmail("dev@test.com")).thenReturn("user-123");
        when(preferenceService.getOrCreatePreference("user-123")).thenReturn(preference(true));
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        AlertNotificationRequest request = new AlertNotificationRequest(
                "dev@test.com", "Dev User", "payment-service", "LOW", "NullPointerException triggered", 1, orgId.toString());

        // Act
        boolean sent = alertNotificationService.sendAlert(request);

        // Assert
        assertThat(sent).isTrue();
        assertThat(existingAlert.getCount()).isEqualTo(5);
        assertThat(existingAlert.getSeverity()).isEqualTo("HIGH");
        verify(jiraClient).createStory(any(), any(), any(), any(), any(), any(), eq("jira-arun-123"), any());
        verify(jiraStoryRepository).save(any(JiraStory.class));
    }

    @Test
    void missingMappingFallback_createsJiraUnassigned() throws Exception {
        // Setup
        Alert alert = new Alert();
        alert.setId(UUID.randomUUID());
        alert.setOrganizationId(orgId);
        alert.setService("payment-service");
        alert.setMessage("Database connection failed!"); // Critical keyword, count=1 -> LOW, but let's test count=2 -> HIGH
        alert.setCount(1);
        alert.setSeverity("LOW");
        alert.setTimestamp(LocalDateTime.now().minusHours(1));

        when(alertRepository.findByOrganizationIdAndServiceIgnoreCaseAndTimestampAfter(eq(orgId), eq("payment-service"), any()))
                .thenReturn(Collections.singletonList(alert));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(alertRepository.findById(alert.getId())).thenReturn(Optional.of(alert));

        when(jiraConfigurationRepository.findFirstByActiveTrue()).thenReturn(Optional.of(mockConfig));
        when(priorityDeadlineResolver.resolveDueDate("HIGH")).thenReturn(LocalDateTime.now().plusDays(1));

        // No owner mappings
        when(userJiraMappingRepository.findOwnersByServiceNameIgnoreCase("payment-service")).thenReturn(new ArrayList<>());

        JiraClient.JiraCreateIssueResponse jiraResp = new JiraClient.JiraCreateIssueResponse("10001", "PAY-12", "https://company.atlassian.net/browse/PAY-12");
        when(jiraClient.createStory(any(), any(), any(), any(), any(), any(), eq(null), any())).thenReturn(jiraResp);

        when(userServiceClient.getUserIdByEmail("dev@test.com")).thenReturn("user-123");
        when(preferenceService.getOrCreatePreference("user-123")).thenReturn(preference(true));
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        AlertNotificationRequest request = new AlertNotificationRequest(
                "dev@test.com", "Dev User", "payment-service", "LOW", "Database connection failed!", 1, orgId.toString());

        // Act
        boolean sent = alertNotificationService.sendAlert(request);

        // Assert
        assertThat(sent).isTrue();
        assertThat(alert.getSeverity()).isEqualTo("HIGH"); // Escales because count = 2 (Critical error count >= 2)
        verify(jiraClient).createStory(any(), any(), any(), any(), any(), any(), eq(null), any());
        
        ArgumentCaptor<JiraStory> storyCaptor = ArgumentCaptor.forClass(JiraStory.class);
        verify(jiraStoryRepository).save(storyCaptor.capture());
        assertThat(storyCaptor.getValue().getJiraAssigneeAccountId()).isNull();

        ArgumentCaptor<MimeMessage> msgCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(msgCaptor.capture());
        // Verify email contains missing mapping mention
    }

    @Test
    void jiraCreatedFromEmailAction_duplicatePrevention() {
        // Setup
        UUID alertId = UUID.randomUUID();
        Alert alert = new Alert();
        alert.setId(alertId);
        alert.setOrganizationId(orgId);
        alert.setService("payment-service");
        alert.setMessage("Database timeout");
        alert.setCount(1);
        alert.setSeverity("LOW");
        alert.setTimestamp(LocalDateTime.now());

        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));

        // First call duplicate check finds nothing
        when(jiraStoryRepository.findByAlertId(alertId.toString())).thenReturn(new ArrayList<>());

        when(jiraConfigurationRepository.findFirstByActiveTrue()).thenReturn(Optional.of(mockConfig));
        when(priorityDeadlineResolver.resolveDueDate("LOW")).thenReturn(LocalDateTime.now().plusDays(7));
        when(userJiraMappingRepository.findOwnersByServiceNameIgnoreCase("payment-service")).thenReturn(new ArrayList<>());

        JiraClient.JiraCreateIssueResponse jiraResp = new JiraClient.JiraCreateIssueResponse("10001", "PAY-12", "https://company.atlassian.net/browse/PAY-12");
        when(jiraClient.createStory(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(jiraResp);

        // Act 1: Create
        JiraStoryResponse response1 = jiraStoryService.createJiraStoryForAlert(alertId.toString());
        assertThat(response1.status()).isEqualTo("CREATED");
        assertThat(response1.jiraIssueKey()).isEqualTo("PAY-12");

        // Act 2: Create again (simulating duplicate prevention)
        JiraStory existingStory = new JiraStory();
        existingStory.setAlertId(alertId.toString());
        existingStory.setJiraIssueKey("PAY-12");
        existingStory.setJiraIssueUrl("https://company.atlassian.net/browse/PAY-12");
        existingStory.setStatus("OPEN");
        when(jiraStoryRepository.findByAlertId(alertId.toString())).thenReturn(Collections.singletonList(existingStory));

        JiraStoryResponse response2 = jiraStoryService.createJiraStoryForAlert(alertId.toString());

        // Assert 2
        assertThat(response2.status()).isEqualTo("SUCCESS");
        assertThat(response2.jiraIssueKey()).isEqualTo("PAY-12");
        // Verify createStory was only called once
        verify(jiraClient, times(1)).createStory(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void jiraCreationFailureHandling() throws Exception {
        // Setup
        Alert alert = new Alert();
        alert.setId(UUID.randomUUID());
        alert.setOrganizationId(orgId);
        alert.setService("payment-service");
        alert.setMessage("Database connection failed!");
        alert.setCount(5);
        alert.setSeverity("HIGH");
        alert.setTimestamp(LocalDateTime.now());

        when(alertRepository.findByOrganizationIdAndServiceIgnoreCaseAndTimestampAfter(eq(orgId), eq("payment-service"), any()))
                .thenReturn(Collections.singletonList(alert));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(alertRepository.findById(alert.getId())).thenReturn(Optional.of(alert));

        when(jiraConfigurationRepository.findFirstByActiveTrue()).thenReturn(Optional.of(mockConfig));
        when(priorityDeadlineResolver.resolveDueDate("HIGH")).thenReturn(LocalDateTime.now().plusDays(1));
        when(userJiraMappingRepository.findOwnersByServiceNameIgnoreCase("payment-service")).thenReturn(new ArrayList<>());

        when(jiraClient.createStory(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Jira API offline"));

        when(userServiceClient.getUserIdByEmail("dev@test.com")).thenReturn("user-123");
        when(preferenceService.getOrCreatePreference("user-123")).thenReturn(preference(true));
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        AlertNotificationRequest request = new AlertNotificationRequest(
                "dev@test.com", "Dev User", "payment-service", "HIGH", "Database connection failed!", 1, orgId.toString());

        // Act
        boolean sent = alertNotificationService.sendAlert(request);

        // Assert
        assertThat(sent).isTrue(); // Email is still sent even though Jira failed!
        verify(jiraStoryRepository).save(any(JiraStory.class));
    }
}
