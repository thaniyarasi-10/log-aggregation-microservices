package com.kovanlabs.servicemanagementservice.dto;

import java.time.LocalDateTime;

public record ServiceRequestView(
        String id,
        String requestedBy,
        String serviceName,
        String description,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}