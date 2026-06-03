package com.kovanlabs.notificationservice.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.kovanlabs.notificationservice.dto.AlertRequest;
import com.kovanlabs.notificationservice.dto.JiraStoryResponse;
import com.kovanlabs.notificationservice.model.JiraConfiguration;
import com.kovanlabs.notificationservice.model.JiraStory;
import com.kovanlabs.notificationservice.model.UserJiraMapping;
import com.kovanlabs.notificationservice.repository.JiraConfigurationRepository;
import com.kovanlabs.notificationservice.repository.JiraStoryRepository;
import com.kovanlabs.notificationservice.repository.UserJiraMappingRepository;

@Service
public class JiraStoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JiraStoryService.class);

    private final JiraStoryRepository jiraStoryRepository;
    private final UserJiraMappingRepository userJiraMappingRepository;
    private final JiraConfigurationRepository jiraConfigurationRepository;
    private final PriorityDeadlineResolver priorityDeadlineResolver;
    private final JiraStoryTemplateBuilder templateBuilder;
    private final JiraClient jiraClient;

    public JiraStoryService(
            JiraStoryRepository jiraStoryRepository,
            UserJiraMappingRepository userJiraMappingRepository,
            JiraConfigurationRepository jiraConfigurationRepository,
            PriorityDeadlineResolver priorityDeadlineResolver,
            JiraStoryTemplateBuilder templateBuilder,
            JiraClient jiraClient) {
        this.jiraStoryRepository = jiraStoryRepository;
        this.userJiraMappingRepository = userJiraMappingRepository;
        this.jiraConfigurationRepository = jiraConfigurationRepository;
        this.priorityDeadlineResolver = priorityDeadlineResolver;
        this.templateBuilder = templateBuilder;
        this.jiraClient = jiraClient;
    }

    public JiraStoryResponse triggerJiraStoryCreation(AlertRequest request) {
        if (request == null || request.alertId() == null || request.alertId().isBlank()) {
            LOGGER.error("Invalid AlertRequest: alertId is required");
            return new JiraStoryResponse("FAILED", "Invalid request: alertId is required", null, null);
        }

        String alertId = request.alertId().trim();
        String serviceName = request.serviceName() != null ? request.serviceName().trim() : "";

        // 1. Check if an OPEN Jira Story already exists for the same alert
        boolean existsOpen = jiraStoryRepository.existsByAlertIdAndStatusIgnoreCase(alertId, "OPEN");
        if (existsOpen) {
            LOGGER.info("Jira Story creation skipped: an OPEN story already exists for alertId {}", alertId);
            return new JiraStoryResponse("SKIPPED", "An OPEN Jira story already exists for this alert.", null, null);
        }

        // 2. Fetch the active system-wide Jira configuration
        Optional<JiraConfiguration> configOpt = jiraConfigurationRepository.findFirstByActiveTrue();
        if (configOpt.isEmpty()) {
            String errorMsg = "No active Jira configuration found. Cannot automate story creation.";
            LOGGER.error(errorMsg);
            return new JiraStoryResponse("FAILED", errorMsg, null, null);
        }
        JiraConfiguration config = configOpt.get();

        // Calculate due date using PriorityDeadlineResolver
        LocalDateTime dueDate = priorityDeadlineResolver.resolveDueDate(request.priority());
        String formattedDueDate = dueDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // Initialize JiraStory DB record
        JiraStory story = new JiraStory();
        story.setId(UUID.randomUUID());
        story.setAlertId(alertId);
        story.setServiceName(serviceName);
        story.setPriority(request.priority() != null ? request.priority().trim().toUpperCase() : "MEDIUM");
        story.setDueDate(dueDate);
        story.setCreatedAt(LocalDateTime.now());
        story.setUpdatedAt(LocalDateTime.now());

        // 3. Find the Primary Owner for the service
        List<Object[]> primaryOwners = userJiraMappingRepository.findPrimaryOwnersByServiceNameIgnoreCase(serviceName);
        if (primaryOwners.isEmpty()) {
            String errorMsg = "No primary owner configured for service: " + serviceName;
            LOGGER.error("Configuration Error: {}", errorMsg);

            story.setStatus("FAILED");
            jiraStoryRepository.save(story);

            return new JiraStoryResponse("FAILED", errorMsg, null, null);
        }

        if (primaryOwners.size() > 1) {
            String errorMsg = "Multiple primary owners configured for service: " + serviceName;
            LOGGER.error("Configuration Error: {}", errorMsg);

            story.setStatus("FAILED");
            jiraStoryRepository.save(story);

            return new JiraStoryResponse("FAILED", errorMsg, null, null);
        }

        String ownerUserId = (String) primaryOwners.get(0)[0];
        String ownerUsername = (String) primaryOwners.get(0)[1];

        // 4. Resolve the active Jira mapping for this owner
        Optional<UserJiraMapping> mappingOpt = userJiraMappingRepository.findByUserId(ownerUserId);
        if (mappingOpt.isEmpty() || !mappingOpt.get().isActive()) {
            String errorMsg = "No active Jira mapping found for primary owner: " + ownerUsername;
            LOGGER.error("Configuration Error: {}", errorMsg);

            story.setStatus("FAILED");
            jiraStoryRepository.save(story);

            return new JiraStoryResponse("FAILED", errorMsg, null, null);
        }

        UserJiraMapping mapping = mappingOpt.get();
        story.setJiraAssigneeAccountId(mapping.getJiraAccountId());
        story.setJiraAssigneeName(mapping.getJiraDisplayName());

        // 5. Generate summary and description
        String summary = templateBuilder.buildSummary(request);
        String description = templateBuilder.buildDescription(request);

        try {
            // 6. Create issue via JiraClient
            JiraClient.JiraCreateIssueResponse jiraResponse = jiraClient.createStory(
                    config.getJiraBaseUrl(),
                    config.getJiraApiToken(),
                    config.getJiraEmail(),
                    config.getJiraProjectKey(),
                    summary,
                    description,
                    mapping.getJiraAccountId(),
                    formattedDueDate
            );

            // Construct browse URL
            String baseUrl = config.getJiraBaseUrl().trim();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            String issueUrl = baseUrl + "/browse/" + jiraResponse.key();

            // 7. Store success response
            story.setJiraIssueId(jiraResponse.id());
            story.setJiraIssueKey(jiraResponse.key());
            story.setJiraIssueUrl(issueUrl);
            story.setStatus("OPEN");
            story.setUpdatedAt(LocalDateTime.now());
            jiraStoryRepository.save(story);

            LOGGER.info("Successfully created Jira Story {} for alertId {}", jiraResponse.key(), alertId);
            return new JiraStoryResponse("CREATED", "Jira Story created successfully", jiraResponse.key(), issueUrl);

        } catch (Exception ex) {
            LOGGER.error("Failed to create Jira Story for alertId {}: {}", alertId, ex.getMessage(), ex);

            // Store failed creation attempt
            story.setStatus("FAILED");
            story.setUpdatedAt(LocalDateTime.now());
            jiraStoryRepository.save(story);

            return new JiraStoryResponse("FAILED", "Jira story creation failed: " + ex.getMessage(), null, null);
        }
    }
}
