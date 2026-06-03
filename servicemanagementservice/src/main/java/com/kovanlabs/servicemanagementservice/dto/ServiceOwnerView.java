package com.kovanlabs.servicemanagementservice.dto;

public record ServiceOwnerView(
        String userId,
        String username,
        boolean primary) {
}
