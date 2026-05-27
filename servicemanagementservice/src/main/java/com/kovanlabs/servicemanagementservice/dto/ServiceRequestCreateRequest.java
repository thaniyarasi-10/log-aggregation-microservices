package com.kovanlabs.servicemanagementservice.dto;

public record ServiceRequestCreateRequest(
        String serviceName,
        String description,
        String requestedBy) {
}