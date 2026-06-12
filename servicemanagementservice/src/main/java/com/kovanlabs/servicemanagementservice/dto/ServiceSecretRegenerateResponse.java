package com.kovanlabs.servicemanagementservice.dto;

import java.util.UUID;

public record ServiceSecretRegenerateResponse(
        UUID serviceId,
        String serviceName,
        String serviceSecret
) {}
