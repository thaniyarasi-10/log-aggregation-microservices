package com.kovanlabs.servicemanagementservice.dto;

import java.util.List;

public record ServiceSummaryView(
        String id,
        String name,
        String description,
        boolean active,
        List<ServiceOwnerView> owners) {
}