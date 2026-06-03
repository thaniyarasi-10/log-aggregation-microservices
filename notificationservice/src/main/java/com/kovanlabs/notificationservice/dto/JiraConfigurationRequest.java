package com.kovanlabs.notificationservice.dto;

public record JiraConfigurationRequest(
    String jiraBaseUrl,
    String jiraEmail,
    String jiraApiToken,
    String jiraProjectKey,
    Boolean active
) {}
