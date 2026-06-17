package com.kovanlabs.servicemanagementservice.dto.rbac;

import java.util.UUID;

public record AssignRoleRequest(String roleName, UUID organizationId) {
}
