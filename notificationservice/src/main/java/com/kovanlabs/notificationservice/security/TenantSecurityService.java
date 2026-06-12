package com.kovanlabs.notificationservice.security;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantSecurityService {

    private final JdbcTemplate jdbcTemplate;

    public TenantSecurityService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID validateMembership(String userId, String organizationIdStr) {
        if (userId == null || userId.isBlank() || organizationIdStr == null || organizationIdStr.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User and organization contexts are required");
        }
        try {
            UUID orgId = UUID.fromString(organizationIdStr.trim());
            String sql = "SELECT COUNT(*) FROM user_organization_mapping WHERE user_id = ? AND organization_id = ? AND status = 'ACTIVE'";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId.trim(), orgId);
            if (count == null || count == 0) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Active membership in organization not found");
            }
            return orgId;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid organization ID format");
        }
    }

    public UUID validateMembershipAndRole(String userId, String organizationIdStr, String requiredRole) {
        UUID orgId = validateMembership(userId, organizationIdStr);
        if (requiredRole != null && !requiredRole.isBlank()) {
            String sql = "SELECT COUNT(*) FROM user_role_mapping urm " +
                         "JOIN app_role r ON urm.role_id = r.id " +
                         "WHERE urm.user_id = ? AND urm.organization_id = ? AND UPPER(r.name) = UPPER(?)";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId.trim(), orgId, requiredRole.trim());
            if (count == null || count == 0) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Required role " + requiredRole + " not found in organization");
            }
        }
        return orgId;
    }
}
