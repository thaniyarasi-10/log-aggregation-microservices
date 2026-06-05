package com.kovanlabs.servicemanagementservice.dto.rbac;

import java.util.List;

public record UserSummaryView(
        String id,
        String username,
        String name,
        String email,
        List<String> roles,
        List<String> services) {
}
