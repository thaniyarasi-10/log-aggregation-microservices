package com.kovanlabs.notificationservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record JiraConfigurationView(
    UUID id,
    String jiraBaseUrl,
    String jiraEmail,
    String jiraApiToken,
    String jiraProjectKey,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
