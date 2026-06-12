package com.kovanlabs.servicemanagementservice.dto;

import java.time.LocalDateTime;

public record ServiceApiKeyResponse(
        String apiKey,
        LocalDateTime apiKeyGeneratedAt
) {}
