package com.kovanlabs.servicemanagementservice.dto;

import java.util.UUID;

public record ServiceApiKeyRegenerateResponse(
        UUID serviceId,
        String serviceName,
        String apiKey
) {}
