package com.kovanlabs.servicemanagementservice.dto;

import java.time.LocalDateTime;

public record ServiceSecretResponse(
        String serviceSecret,
        boolean hidden,
        Long secondsRemaining,
        LocalDateTime secretGeneratedAt
) {}
