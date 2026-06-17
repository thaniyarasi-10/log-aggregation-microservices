package com.kovanlabs.notificationservice.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import com.kovanlabs.notificationservice.dto.AlertRequest;
import com.kovanlabs.notificationservice.dto.JiraStoryResponse;
import com.kovanlabs.notificationservice.dto.JiraUserDto;
import com.kovanlabs.notificationservice.model.Alert;
import com.kovanlabs.notificationservice.model.JiraConfiguration;
import com.kovanlabs.notificationservice.model.JiraStory;
import com.kovanlabs.notificationservice.model.UserJiraMapping;
import com.kovanlabs.notificationservice.repository.AlertRepository;
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
    private final AlertRepository alertRepository;
    private final JiraFailureCache failureCache;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    public JiraStoryService(
            JiraStoryRepository jiraStoryRepository,
            UserJiraMappingRepository userJiraMappingRepository,
            JiraConfigurationRepository jiraConfigurationRepository,
            PriorityDeadlineResolver priorityDeadlineResolver,
            JiraStoryTemplateBuilder templateBuilder,
            JiraClient jiraClient,
            AlertRepository alertRepository,
            JiraFailureCache failureCache,
            io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.jiraStoryRepository = jiraStoryRepository;
        this.userJiraMappingRepository = userJiraMappingRepository;
        this.jiraConfigurationRepository = jiraConfigurationRepository;
        this.priorityDeadlineResolver = priorityDeadlineResolver;
        this.templateBuilder = templateBuilder;
        this.jiraClient = jiraClient;
        this.alertRepository = alertRepository;
        this.failureCache = failureCache;
        this.meterRegistry = meterRegistry;
    }


    public JiraStoryResponse triggerJiraStoryCreation(AlertRequest request) {
        if (request == null || request.alertId() == null || request.alertId().isBlank()) {
            LOGGER.warn("Invalid AlertRequest: alertId is required");
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

        // Prevent duplicate ticket creation for the same incident type using signatureHash
        String currentSignatureHash = ErrorNormalizer.hashSignature(request.alertName());
        Optional<JiraStory> duplicateStoryOpt = jiraStoryRepository.findFirstBySignatureHashAndStatusIgnoreCase(currentSignatureHash, "OPEN");
        if (duplicateStoryOpt.isPresent()) {
            JiraStory openStory = duplicateStoryOpt.get();
            LOGGER.info("Duplicate incident: an open Jira story {} already exists for alert type (signatureHash: {})", 
                    openStory.getJiraIssueKey(), currentSignatureHash);
            return new JiraStoryResponse("SKIPPED", "An OPEN Jira story already exists for this incident.", 
                    openStory.getJiraIssueKey(), openStory.getJiraIssueUrl());
        }

        // 2. Fetch the active system-wide Jira configuration
        Optional<JiraConfiguration> configOpt = jiraConfigurationRepository.findFirstByActiveTrue();
        if (configOpt.isEmpty()) {
            String errorMsg = "No active Jira configuration found. Cannot automate story creation.";
            LOGGER.warn(errorMsg);
            return new JiraStoryResponse("FAILED", errorMsg, null, null);
        }
        JiraConfiguration config = configOpt.get();

        String configHash = getConfigHash(config);
        if (failureCache.isActive(configHash)) {
            String cachedError = failureCache.getErrorMessage(configHash);
            String errorMsg = "Jira creation blocked due to active failure cooldown: " + cachedError;
            LOGGER.warn(errorMsg);
            return new JiraStoryResponse("FAILED", errorMsg, null, null);
        }

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
        story.setSignatureHash(currentSignatureHash);
        story.setCreatedAt(LocalDateTime.now());
        story.setUpdatedAt(LocalDateTime.now());

        String assigneeAccountId = null;
        String assigneeDisplayName = null;

        // 3. Find the Primary Owner for the service
        List<Object[]> primaryOwners = userJiraMappingRepository.findPrimaryOwnersByServiceNameIgnoreCase(serviceName);
        if (primaryOwners.isEmpty()) {
            LOGGER.warn("No primary owner configured for service: {}. Fallback to check other service owners.", serviceName);
            List<Object[]> allOwners = userJiraMappingRepository.findOwnersByServiceNameIgnoreCase(serviceName);
            for (Object[] owner : allOwners) {
                String ownerUserId = (String) owner[0];
                Optional<UserJiraMapping> mappingOpt = userJiraMappingRepository.findByUserId(ownerUserId);
                if (mappingOpt.isPresent() && mappingOpt.get().isActive()) {
                    UserJiraMapping mapping = mappingOpt.get();
                    assigneeAccountId = mapping.getJiraAccountId();
                    assigneeDisplayName = mapping.getJiraDisplayName();
                    LOGGER.info("No primary owner, fell back to service owner: {} ({})", assigneeDisplayName, assigneeAccountId);
                    break;
                }
            }
            if (assigneeAccountId == null) {
                LOGGER.warn("No service owner with active Jira mapping found for service: {}. Fallback to integration user.", serviceName);
                try {
                    List<JiraUserDto> assignableUsers = jiraClient.searchAssignableUsers(
                            config.getJiraBaseUrl(),
                            config.getJiraApiToken(),
                            config.getJiraEmail(),
                            config.getJiraProjectKey(),
                            config.getJiraEmail()
                    );
                    if (assignableUsers != null && !assignableUsers.isEmpty()) {
                        assigneeAccountId = assignableUsers.get(0).accountId();
                        assigneeDisplayName = assignableUsers.get(0).displayName();
                        LOGGER.info("Fallback assignee found for integration user: {} ({})", assigneeDisplayName, assigneeAccountId);
                    } else {
                        LOGGER.warn("Could not find any assignable Jira user matching integration email: {}", config.getJiraEmail());
                    }
                } catch (Exception ex) {
                    LOGGER.error("Failed to fetch assignable integration user: {}", ex.getMessage());
                }
            }
        } else if (primaryOwners.size() > 1) {
            String errorMsg = "Multiple primary owners configured for service: " + serviceName;
            LOGGER.warn("Configuration Error: {}", errorMsg);

            story.setStatus("FAILED");
            jiraStoryRepository.save(story);

            return new JiraStoryResponse("FAILED", errorMsg, null, null);
        } else {
            String ownerUserId = (String) primaryOwners.get(0)[0];
            String ownerUsername = (String) primaryOwners.get(0)[1];

            // 4. Resolve the active Jira mapping for this owner
            Optional<UserJiraMapping> mappingOpt = userJiraMappingRepository.findByUserId(ownerUserId);
            if (mappingOpt.isPresent() && mappingOpt.get().isActive()) {
                UserJiraMapping mapping = mappingOpt.get();
                assigneeAccountId = mapping.getJiraAccountId();
                assigneeDisplayName = mapping.getJiraDisplayName();
            } else {
                LOGGER.warn("No active Jira mapping found for primary owner: {}. Fallback to other service owners.", ownerUsername);
                List<Object[]> allOwners = userJiraMappingRepository.findOwnersByServiceNameIgnoreCase(serviceName);
                for (Object[] owner : allOwners) {
                    String ownerUserId2 = (String) owner[0];
                    if (ownerUserId2.equals(ownerUserId)) {
                        continue;
                    }
                    Optional<UserJiraMapping> mappingOpt2 = userJiraMappingRepository.findByUserId(ownerUserId2);
                    if (mappingOpt2.isPresent() && mappingOpt2.get().isActive()) {
                        UserJiraMapping mapping = mappingOpt2.get();
                        assigneeAccountId = mapping.getJiraAccountId();
                        assigneeDisplayName = mapping.getJiraDisplayName();
                        LOGGER.info("Fallback to service owner: {} ({})", assigneeDisplayName, assigneeAccountId);
                        break;
                    }
                }
                if (assigneeAccountId == null) {
                    LOGGER.warn("No other service owner with active Jira mapping found. Fallback to integration user.");
                    try {
                        List<JiraUserDto> assignableUsers = jiraClient.searchAssignableUsers(
                                config.getJiraBaseUrl(),
                                config.getJiraApiToken(),
                                config.getJiraEmail(),
                                config.getJiraProjectKey(),
                                config.getJiraEmail()
                        );
                        if (assignableUsers != null && !assignableUsers.isEmpty()) {
                            assigneeAccountId = assignableUsers.get(0).accountId();
                            assigneeDisplayName = assignableUsers.get(0).displayName();
                            LOGGER.info("Fallback assignee found for integration user: {} ({})", assigneeDisplayName, assigneeAccountId);
                        } else {
                            LOGGER.warn("Could not find any assignable Jira user matching integration email: {}", config.getJiraEmail());
                        }
                    } catch (Exception ex) {
                        LOGGER.error("Failed to fetch assignable integration user: {}", ex.getMessage());
                    }
                }
            }
        }

        story.setJiraAssigneeAccountId(assigneeAccountId);
        story.setJiraAssigneeName(assigneeDisplayName);

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
                    assigneeAccountId,
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
            LOGGER.warn("Failed to create Jira Story for alertId {}: {}", alertId, ex.getMessage(), ex);

            if (config != null) {
                failureCache.record(configHash, ex.getMessage());
                try {
                    meterRegistry.counter("jira.integration.failures", "project", config.getJiraProjectKey(), "error", ex.getClass().getSimpleName()).increment();
                } catch (Exception e) {
                    LOGGER.warn("Failed to increment Jira failure metric: {}", e.getMessage());
                }
            }

            // Store failed creation attempt
            story.setStatus("FAILED");
            story.setUpdatedAt(LocalDateTime.now());
            jiraStoryRepository.save(story);

            return new JiraStoryResponse("FAILED", "Jira story creation failed: " + ex.getMessage(), null, null);
        }
    }

    @Transactional
    public JiraStoryResponse createJiraStoryForAlert(String alertIdString) {
        if (alertIdString == null || alertIdString.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Alert ID is required");
        }

        UUID alertId;
        try {
            alertId = UUID.fromString(alertIdString.trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Alert ID format: " + alertIdString);
        }

        // 1. Validate alert exists
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found with ID: " + alertIdString));

        // 2. Prevent duplicate ticket creation
        List<JiraStory> existingStories = jiraStoryRepository.findByAlertId(alertIdString.trim());
        Optional<JiraStory> successfulStory = existingStories.stream()
                .filter(s -> s.getJiraIssueKey() != null && !s.getJiraIssueKey().isBlank() && !"FAILED".equalsIgnoreCase(s.getStatus()))
                .findFirst();
        if (successfulStory.isPresent()) {
            JiraStory story = successfulStory.get();
            LOGGER.info("Jira Story already exists for alertId {}: {}", alertIdString, story.getJiraIssueKey());
            return new JiraStoryResponse("SUCCESS", "Jira story already exists for this alert.", story.getJiraIssueKey(), story.getJiraIssueUrl());
        }

        // Prevent duplicate ticket creation for the same incident type using signatureHash
        String currentSignatureHash = alert.getSignatureHash() != null ? alert.getSignatureHash() : ErrorNormalizer.hashSignature(alert.getMessage());
        Optional<JiraStory> duplicateStoryOpt = jiraStoryRepository.findFirstBySignatureHashAndStatusIgnoreCase(currentSignatureHash, "OPEN");
        if (duplicateStoryOpt.isPresent()) {
            JiraStory openStory = duplicateStoryOpt.get();
            LOGGER.info("Duplicate incident: an open Jira story {} already exists for alert type (signatureHash: {})", 
                    openStory.getJiraIssueKey(), currentSignatureHash);
            return new JiraStoryResponse("SUCCESS", "Jira story already exists for this incident.", 
                    openStory.getJiraIssueKey(), openStory.getJiraIssueUrl());
        }

        // 3. Fetch the active system-wide Jira configuration
        Optional<JiraConfiguration> configOpt = jiraConfigurationRepository.findFirstByActiveTrue();
        if (configOpt.isEmpty()) {
            String errorMsg = "No active Jira configuration found. Cannot automate story creation.";
            LOGGER.warn(errorMsg);
            return new JiraStoryResponse("FAILED", errorMsg, null, null);
        }
        JiraConfiguration config = configOpt.get();

        String configHash = getConfigHash(config);
        if (failureCache.isActive(configHash)) {
            String cachedError = failureCache.getErrorMessage(configHash);
            String errorMsg = "Jira creation blocked due to active failure cooldown: " + cachedError;
            LOGGER.warn(errorMsg);
            return new JiraStoryResponse("FAILED", errorMsg, null, null);
        }

        // Calculate due date
        LocalDateTime dueDate = priorityDeadlineResolver.resolveDueDate(alert.getSeverity());
        String formattedDueDate = dueDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // Initialize JiraStory DB record
        JiraStory story = new JiraStory();
        story.setId(UUID.randomUUID());
        story.setAlertId(alertIdString.trim());
        story.setServiceName(alert.getService());
        story.setPriority(alert.getSeverity() != null ? alert.getSeverity().toUpperCase() : "LOW");
        story.setDueDate(dueDate);
        story.setSignatureHash(currentSignatureHash);
        story.setCreatedAt(LocalDateTime.now());
        story.setUpdatedAt(LocalDateTime.now());

        // 4. Resolve the Assignee
        List<Object[]> owners = userJiraMappingRepository.findOwnersByServiceNameIgnoreCase(alert.getService());
        UserJiraMapping assigneeMapping = null;
        for (Object[] owner : owners) {
            String ownerUserId = (String) owner[0];
            Optional<UserJiraMapping> mappingOpt = userJiraMappingRepository.findByUserId(ownerUserId);
            if (mappingOpt.isPresent() && mappingOpt.get().isActive()) {
                assigneeMapping = mappingOpt.get();
                break;
            }
        }

        String assigneeAccountId = null;
        String assigneeDisplayName = null;
        if (assigneeMapping != null) {
            assigneeAccountId = assigneeMapping.getJiraAccountId();
            assigneeDisplayName = assigneeMapping.getJiraDisplayName();
            LOGGER.info("Jira story for service '{}' assigned to owner '{}'", alert.getService(), assigneeDisplayName);
        } else {
            LOGGER.warn("No active Jira mapping found for service owners of service: {}. Fallback to integration user.", alert.getService());
            try {
                List<JiraUserDto> assignableUsers = jiraClient.searchAssignableUsers(
                        config.getJiraBaseUrl(),
                        config.getJiraApiToken(),
                        config.getJiraEmail(),
                        config.getJiraProjectKey(),
                        config.getJiraEmail()
                );
                if (assignableUsers != null && !assignableUsers.isEmpty()) {
                    assigneeAccountId = assignableUsers.get(0).accountId();
                    assigneeDisplayName = assignableUsers.get(0).displayName();
                    LOGGER.info("Fallback assignee found for integration user: {} ({})", assigneeDisplayName, assigneeAccountId);
                } else {
                    LOGGER.warn("Could not find any assignable Jira user matching integration email: {}", config.getJiraEmail());
                }
            } catch (Exception ex) {
                LOGGER.error("Failed to fetch assignable integration user: {}", ex.getMessage());
            }
        }

        story.setJiraAssigneeAccountId(assigneeAccountId);
        story.setJiraAssigneeName(assigneeDisplayName);

        // 5. Generate summary and description
        AlertRequest jiraRequest = new AlertRequest(
                alertIdString.trim(),
                alert.getMessage() != null ? alert.getMessage() : "Error Triggered",
                alert.getService() != null ? alert.getService() : "Unknown Service",
                alert.getSeverity() != null ? alert.getSeverity().toUpperCase() : "LOW",
                alert.getTimestamp().toString(),
                alert.getMessage(), // alertRule
                "N/A", // observedValue
                "N/A", // threshold
                "N/A", // timeWindow
                alert.getCount(), // errorCount
                alert.getMessage(), // topErrors
                "N/A" // alertUrl
        );

        String summary = templateBuilder.buildSummary(jiraRequest);
        String description = templateBuilder.buildDescription(jiraRequest);

        try {
            // 6. Create issue via JiraClient
            JiraClient.JiraCreateIssueResponse jiraResponse = jiraClient.createStory(
                    config.getJiraBaseUrl(),
                    config.getJiraApiToken(),
                    config.getJiraEmail(),
                    config.getJiraProjectKey(),
                    summary,
                    description,
                    assigneeAccountId,
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

            LOGGER.info("Successfully created Jira Story {} for alertId {}", jiraResponse.key(), alertIdString);
            return new JiraStoryResponse("CREATED", "Jira Story created successfully", jiraResponse.key(), issueUrl);

        } catch (Exception ex) {
            LOGGER.warn("Failed to create Jira Story for alertId {}: {}", alertIdString, ex.getMessage(), ex);

            if (config != null) {
                failureCache.record(configHash, ex.getMessage());
                try {
                    meterRegistry.counter("jira.integration.failures", "project", config.getJiraProjectKey(), "error", ex.getClass().getSimpleName()).increment();
                } catch (Exception e) {
                    LOGGER.warn("Failed to increment Jira failure metric: {}", e.getMessage());
                }
            }

            // Store failed creation attempt
            story.setStatus("FAILED");
            story.setUpdatedAt(LocalDateTime.now());
            jiraStoryRepository.save(story);

            return new JiraStoryResponse("FAILED", "Jira story creation failed: " + ex.getMessage(), null, null);
        }
    }

    public JiraStory getJiraStoryByAlertId(String alertId) {
        if (alertId == null || alertId.isBlank()) {
            return null;
        }
        List<JiraStory> stories = jiraStoryRepository.findByAlertId(alertId.trim());
        return stories.stream()
                .filter(s -> s.getJiraIssueKey() != null && !s.getJiraIssueKey().isBlank() && !"FAILED".equalsIgnoreCase(s.getStatus()))
                .findFirst()
                .orElse(null);
    }

    private String getConfigHash(JiraConfiguration config) {
        if (config == null) return "";
        return (config.getJiraBaseUrl() != null ? config.getJiraBaseUrl().trim() : "") + "|" +
               (config.getJiraEmail() != null ? config.getJiraEmail().trim() : "") + "|" +
               (config.getJiraProjectKey() != null ? config.getJiraProjectKey().trim().toUpperCase() : "");
    }
}
