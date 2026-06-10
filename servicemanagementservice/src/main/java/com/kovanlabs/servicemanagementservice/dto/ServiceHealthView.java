package com.kovanlabs.servicemanagementservice.dto;

import java.time.Instant;

public record ServiceHealthView(
        String service,
        String status,
        Instant lastSeen
) {}
