package com.kovanlabs.servicemanagementservice.dto;

public record ServiceSummaryView(
        String id,
        String name,
        String description,
        boolean active) {
}