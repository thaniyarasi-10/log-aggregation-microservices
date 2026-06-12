package com.kovanlabs.servicemanagementservice.dto.auth;

import java.util.List;

public record AuthResponse(
        String tokenType,
        String accessToken,
        long expiresAtEpochSeconds,
        String userId,
        String email,
        List<String> roles,
        List<String> permissions,
        List<String> services,
        String profileImageUrl,
        String activeOrganizationId) {
}
