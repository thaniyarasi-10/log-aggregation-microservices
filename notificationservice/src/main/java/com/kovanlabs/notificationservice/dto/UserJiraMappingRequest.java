package com.kovanlabs.notificationservice.dto;

public record UserJiraMappingRequest(
    String userId,
    String jiraAccountId,
    String jiraDisplayName,
    Boolean active
) {}
