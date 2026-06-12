package com.kovanlabs.servicemanagementservice.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.kovanlabs.servicemanagementservice.model.UserOrganizationMapping;
import com.kovanlabs.servicemanagementservice.model.UserRoleMapping;
import com.kovanlabs.servicemanagementservice.model.RolePermissionMapping;
import com.kovanlabs.servicemanagementservice.repository.UserOrganizationMappingRepository;
import com.kovanlabs.servicemanagementservice.repository.UserRoleMappingRepository;
import com.kovanlabs.servicemanagementservice.repository.RolePermissionMappingRepository;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final UserOrganizationMappingRepository userOrganizationMappingRepository;
    private final UserRoleMappingRepository userRoleMappingRepository;
    private final RolePermissionMappingRepository rolePermissionMappingRepository;

    public JwtAuthenticationFilter(
            JwtTokenService jwtTokenService,
            UserOrganizationMappingRepository userOrganizationMappingRepository,
            UserRoleMappingRepository userRoleMappingRepository,
            RolePermissionMappingRepository rolePermissionMappingRepository) {
        this.jwtTokenService = jwtTokenService;
        this.userOrganizationMappingRepository = userOrganizationMappingRepository;
        this.userRoleMappingRepository = userRoleMappingRepository;
        this.rolePermissionMappingRepository = rolePermissionMappingRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            Claims claims = jwtTokenService.parseClaims(token);
            String email = claims.getSubject();
            String userId = claims.get("userId", String.class);
            String activeOrganizationIdStr = claims.get("activeOrganizationId", String.class);

            if (userId == null || activeOrganizationIdStr == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Access denied: Invalid token structure.");
                return;
            }

            UUID activeOrganizationId = UUID.fromString(activeOrganizationIdStr);

            // Verify membership exists and is ACTIVE
            Optional<UserOrganizationMapping> mapping = userOrganizationMappingRepository
                    .findByUser_IdAndOrganization_IdAndStatus(userId, activeOrganizationId, "ACTIVE");

            if (mapping.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("Access denied: Invalid or inactive organization membership.");
                return;
            }

            // Load roles dynamically from DB
            List<UserRoleMapping> roleMappings = userRoleMappingRepository
                    .findByUser_IdAndOrganization_Id(userId, activeOrganizationId);

            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            if (!roleMappings.isEmpty()) {
                List<UUID> roleIds = new ArrayList<>();
                for (UserRoleMapping rm : roleMappings) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + rm.getRole().getName().toUpperCase()));
                    roleIds.add(rm.getRole().getId());
                }

                // Load permissions dynamically from DB
                List<RolePermissionMapping> permMappings = rolePermissionMappingRepository
                        .findByRole_IdIn(roleIds);

                for (RolePermissionMapping pm : permMappings) {
                    authorities.add(new SimpleGrantedAuthority(pm.getPermission().getName().toUpperCase()));
                }
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(email, null, authorities);
            authentication.setDetails(claims);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception ignored) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
