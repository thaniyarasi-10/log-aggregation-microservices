package com.kovanlabs.notificationservice.dto;

public record JiraStoryResponse(
    String status,
    String message,
    String jiraIssueKey,
    String jiraIssueUrl
) {}
