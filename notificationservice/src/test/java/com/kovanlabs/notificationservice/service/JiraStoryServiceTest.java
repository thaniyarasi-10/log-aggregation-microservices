package com.kovanlabs.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.kovanlabs.notificationservice.dto.AlertRequest;
import com.kovanlabs.notificationservice.dto.JiraStoryResponse;
import com.kovanlabs.notificationservice.model.JiraConfiguration;
import com.kovanlabs.notificationservice.model.JiraStory;
import com.kovanlabs.notificationservice.model.UserJiraMapping;
import com.kovanlabs.notificationservice.repository.AlertRepository;
import com.kovanlabs.notificationservice.repository.JiraConfigurationRepository;
import com.kovanlabs.notificationservice.repository.JiraStoryRepository;
import com.kovanlabs.notificationservice.repository.UserJiraMappingRepository;

@ExtendWith(MockitoExtension.class)
class JiraStoryServiceTest {

    @Mock private JiraStoryRepository jiraStoryRepository;
    @Mock private UserJiraMappingRepository userJiraMappingRepository;
    @Mock private JiraConfigurationRepository jiraConfigurationRepository;
    @Mock private PriorityDeadlineResolver priorityDeadlineResolver;
    @Mock private JiraStoryTemplateBuilder templateBuilder;
    @Mock private JiraClient jiraClient;
    @Mock private AlertRepository alertRepository;
    @Mock private JiraFailureCache failureCache;
    @Mock private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    private JiraStoryService service;
    private AlertRequest validAlert;
    private JiraConfiguration mockConfig;

    @BeforeEach
    void setUp() {
        service = new JiraStoryService(
                jiraStoryRepository,
                userJiraMappingRepository,
                jiraConfigurationRepository,
                priorityDeadlineResolver,
                templateBuilder,
                jiraClient,
                alertRepository,
                failureCache,
                meterRegistry
        );

        validAlert = new AlertRequest(
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

        mockConfig = new JiraConfiguration();
        mockConfig.setId(UUID.randomUUID());
        mockConfig.setJiraBaseUrl("https://company.atlassian.net");
        mockConfig.setJiraEmail("arun@company.com");
        mockConfig.setJiraApiToken("fake-token");
        mockConfig.setJiraProjectKey("PAY");
        mockConfig.setActive(true);
    }

    @Test
    void triggerJiraStoryCreation_nullAlert_returnsFailed() {
        JiraStoryResponse response = service.triggerJiraStoryCreation(null);
        assertThat(response.status()).isEqualTo("FAILED");
        verify(jiraStoryRepository, never()).save(any());
    }

    @Test
    void triggerJiraStoryCreation_duplicateOpenAlert_skipsAndReturnsSkipped() {
        when(jiraStoryRepository.existsByAlertIdAndStatusIgnoreCase("alert-123", "OPEN")).thenReturn(true);

        JiraStoryResponse response = service.triggerJiraStoryCreation(validAlert);

        assertThat(response.status()).isEqualTo("SKIPPED");
        verify(userJiraMappingRepository, never()).findPrimaryOwnersByServiceNameIgnoreCase(anyString());
        verify(jiraClient, never()).createStory(any(), any(), any(), any(), any(), any(), any(), any());
        verify(jiraStoryRepository, never()).save(any());
    }

    @Test
    void triggerJiraStoryCreation_missingJiraConfig_returnsFailed() {
        when(jiraStoryRepository.existsByAlertIdAndStatusIgnoreCase("alert-123", "OPEN")).thenReturn(false);
        when(jiraConfigurationRepository.findFirstByActiveTrue()).thenReturn(Optional.empty());

        JiraStoryResponse response = service.triggerJiraStoryCreation(validAlert);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.message()).contains("No active Jira configuration found");
        verify(jiraStoryRepository, never()).save(any());
    }

    @Test
    void triggerJiraStoryCreation_missingPrimaryOwner_fallsBackToIntegrationUserAndSucceeds() {
        when(jiraStoryRepository.existsByAlertIdAndStatusIgnoreCase("alert-123", "OPEN")).thenReturn(false);
        when(jiraConfigurationRepository.findFirstByActiveTrue()).thenReturn(Optional.of(mockConfig));
        when(priorityDeadlineResolver.resolveDueDate("CRITICAL")).thenReturn(LocalDateTime.now().plusHours(4));
        when(userJiraMappingRepository.findPrimaryOwnersByServiceNameIgnoreCase("payment-service")).thenReturn(Collections.emptyList());

        when(jiraClient.searchAssignableUsers(any(), any(), any(), any(), eq("arun@company.com")))
                .thenReturn(Collections.singletonList(new com.kovanlabs.notificationservice.dto.JiraUserDto("fallback-id", "Arun Fallback")));

        when(templateBuilder.buildSummary(validAlert)).thenReturn("summary");
        when(templateBuilder.buildDescription(validAlert)).thenReturn("description");

        JiraClient.JiraCreateIssueResponse jiraResp = new JiraClient.JiraCreateIssueResponse("10002", "PAY-13", "http://jira/PAY-13");
        when(jiraClient.createStory(
                eq("https://company.atlassian.net"),
                eq("fake-token"),
                eq("arun@company.com"),
                eq("PAY"),
                eq("summary"),
                eq("description"),
                eq("fallback-id"),
                anyString()
        )).thenReturn(jiraResp);

        JiraStoryResponse response = service.triggerJiraStoryCreation(validAlert);

        assertThat(response.status()).isEqualTo("CREATED");
        assertThat(response.jiraIssueKey()).isEqualTo("PAY-13");

        ArgumentCaptor<JiraStory> storyCaptor = ArgumentCaptor.forClass(JiraStory.class);
        verify(jiraStoryRepository).save(storyCaptor.capture());
        JiraStory saved = storyCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo("OPEN");
        assertThat(saved.getJiraAssigneeAccountId()).isEqualTo("fallback-id");
        assertThat(saved.getJiraAssigneeName()).isEqualTo("Arun Fallback");
    }

    @Test
    void triggerJiraStoryCreation_multiplePrimaryOwners_savesFailedAndReturnsFailed() {
        when(jiraStoryRepository.existsByAlertIdAndStatusIgnoreCase("alert-123", "OPEN")).thenReturn(false);
        when(jiraConfigurationRepository.findFirstByActiveTrue()).thenReturn(Optional.of(mockConfig));
        when(priorityDeadlineResolver.resolveDueDate("CRITICAL")).thenReturn(LocalDateTime.now().plusHours(4));
        
        List<Object[]> multipleOwners = new ArrayList<>();
        multipleOwners.add(new Object[] { "user-1", "Arun" });
        multipleOwners.add(new Object[] { "user-2", "Priya" });
        when(userJiraMappingRepository.findPrimaryOwnersByServiceNameIgnoreCase("payment-service")).thenReturn(multipleOwners);

        JiraStoryResponse response = service.triggerJiraStoryCreation(validAlert);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.message()).contains("Multiple primary owners configured for service");

        ArgumentCaptor<JiraStory> storyCaptor = ArgumentCaptor.forClass(JiraStory.class);
        verify(jiraStoryRepository).save(storyCaptor.capture());
        assertThat(storyCaptor.getValue().getStatus()).isEqualTo("FAILED");
    }

    @Test
    void triggerJiraStoryCreation_missingJiraMappingForPrimaryOwner_fallsBackToIntegrationUserAndSucceeds() {
        when(jiraStoryRepository.existsByAlertIdAndStatusIgnoreCase("alert-123", "OPEN")).thenReturn(false);
        when(jiraConfigurationRepository.findFirstByActiveTrue()).thenReturn(Optional.of(mockConfig));
        when(priorityDeadlineResolver.resolveDueDate("CRITICAL")).thenReturn(LocalDateTime.now().plusHours(4));
        
        List<Object[]> singleOwner = Collections.singletonList(new Object[] { "user-1", "Arun" });
        when(userJiraMappingRepository.findPrimaryOwnersByServiceNameIgnoreCase("payment-service")).thenReturn(singleOwner);
        when(userJiraMappingRepository.findByUserId("user-1")).thenReturn(Optional.empty());

        when(jiraClient.searchAssignableUsers(any(), any(), any(), any(), eq("arun@company.com")))
                .thenReturn(Collections.singletonList(new com.kovanlabs.notificationservice.dto.JiraUserDto("fallback-id", "Arun Fallback")));

        when(templateBuilder.buildSummary(validAlert)).thenReturn("summary");
        when(templateBuilder.buildDescription(validAlert)).thenReturn("description");

        JiraClient.JiraCreateIssueResponse jiraResp = new JiraClient.JiraCreateIssueResponse("10002", "PAY-13", "http://jira/PAY-13");
        when(jiraClient.createStory(
                eq("https://company.atlassian.net"),
                eq("fake-token"),
                eq("arun@company.com"),
                eq("PAY"),
                eq("summary"),
                eq("description"),
                eq("fallback-id"),
                anyString()
        )).thenReturn(jiraResp);

        JiraStoryResponse response = service.triggerJiraStoryCreation(validAlert);

        assertThat(response.status()).isEqualTo("CREATED");
        assertThat(response.jiraIssueKey()).isEqualTo("PAY-13");

        ArgumentCaptor<JiraStory> storyCaptor = ArgumentCaptor.forClass(JiraStory.class);
        verify(jiraStoryRepository).save(storyCaptor.capture());
        JiraStory saved = storyCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo("OPEN");
        assertThat(saved.getJiraAssigneeAccountId()).isEqualTo("fallback-id");
        assertThat(saved.getJiraAssigneeName()).isEqualTo("Arun Fallback");
    }

    @Test
    void triggerJiraStoryCreation_jiraClientException_savesFailedAndReturnsFailed() {
        UserJiraMapping mapping = createMapping();
        when(jiraStoryRepository.existsByAlertIdAndStatusIgnoreCase("alert-123", "OPEN")).thenReturn(false);
        when(jiraConfigurationRepository.findFirstByActiveTrue()).thenReturn(Optional.of(mockConfig));
        when(priorityDeadlineResolver.resolveDueDate("CRITICAL")).thenReturn(LocalDateTime.now().plusHours(4));
        
        List<Object[]> singleOwner = Collections.singletonList(new Object[] { "user-1", "Arun" });
        when(userJiraMappingRepository.findPrimaryOwnersByServiceNameIgnoreCase("payment-service")).thenReturn(singleOwner);
        when(userJiraMappingRepository.findByUserId("user-1")).thenReturn(Optional.of(mapping));
        
        when(templateBuilder.buildSummary(validAlert)).thenReturn("summary");
        when(templateBuilder.buildDescription(validAlert)).thenReturn("description");

        when(jiraClient.createStory(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Jira API Offline"));

        JiraStoryResponse response = service.triggerJiraStoryCreation(validAlert);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.message()).contains("Jira story creation failed: Jira API Offline");

        ArgumentCaptor<JiraStory> storyCaptor = ArgumentCaptor.forClass(JiraStory.class);
        verify(jiraStoryRepository).save(storyCaptor.capture());
        assertThat(storyCaptor.getValue().getStatus()).isEqualTo("FAILED");
    }

    @Test
    void triggerJiraStoryCreation_success_savesOpenAndReturnsCreated() {
        UserJiraMapping mapping = createMapping();
        when(jiraStoryRepository.existsByAlertIdAndStatusIgnoreCase("alert-123", "OPEN")).thenReturn(false);
        when(jiraConfigurationRepository.findFirstByActiveTrue()).thenReturn(Optional.of(mockConfig));
        when(priorityDeadlineResolver.resolveDueDate("CRITICAL")).thenReturn(LocalDateTime.now().plusHours(4));
        
        List<Object[]> singleOwner = Collections.singletonList(new Object[] { "user-1", "Arun" });
        when(userJiraMappingRepository.findPrimaryOwnersByServiceNameIgnoreCase("payment-service")).thenReturn(singleOwner);
        when(userJiraMappingRepository.findByUserId("user-1")).thenReturn(Optional.of(mapping));
        
        when(templateBuilder.buildSummary(validAlert)).thenReturn("summary");
        when(templateBuilder.buildDescription(validAlert)).thenReturn("description");

        JiraClient.JiraCreateIssueResponse jiraResp = new JiraClient.JiraCreateIssueResponse("10001", "PAY-12", "http://jira/PAY-12");
        when(jiraClient.createStory(
                eq("https://company.atlassian.net"),
                eq("fake-token"),
                eq("arun@company.com"),
                eq("PAY"),
                eq("summary"),
                eq("description"),
                eq("abc123"),
                anyString()
        )).thenReturn(jiraResp);

        JiraStoryResponse response = service.triggerJiraStoryCreation(validAlert);

        assertThat(response.status()).isEqualTo("CREATED");
        assertThat(response.jiraIssueKey()).isEqualTo("PAY-12");
        assertThat(response.jiraIssueUrl()).isEqualTo("https://company.atlassian.net/browse/PAY-12");

        ArgumentCaptor<JiraStory> storyCaptor = ArgumentCaptor.forClass(JiraStory.class);
        verify(jiraStoryRepository).save(storyCaptor.capture());
        JiraStory saved = storyCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo("OPEN");
        assertThat(saved.getJiraIssueId()).isEqualTo("10001");
        assertThat(saved.getJiraIssueKey()).isEqualTo("PAY-12");
        assertThat(saved.getJiraIssueUrl()).isEqualTo("https://company.atlassian.net/browse/PAY-12");
    }

    private UserJiraMapping createMapping() {
        UserJiraMapping mapping = new UserJiraMapping();
        mapping.setUserId("user-1");
        mapping.setJiraAccountId("abc123");
        mapping.setJiraDisplayName("Arun Kumar");
        mapping.setActive(true);
        return mapping;
    }
}
