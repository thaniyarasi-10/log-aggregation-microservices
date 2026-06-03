package com.kovanlabs.notificationservice.dto;

import java.util.UUID;

public record UserJiraMappingView(
    UUID id,
    String userId,
    String username,
    String ownedServices,
    String jiraAccountId,
    String jiraDisplayName,
    Boolean active
) {}
