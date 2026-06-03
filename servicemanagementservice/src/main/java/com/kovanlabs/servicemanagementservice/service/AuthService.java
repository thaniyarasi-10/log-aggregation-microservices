package com.kovanlabs.servicemanagementservice.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.kovanlabs.servicemanagementservice.dto.auth.AuthResponse;

import com.kovanlabs.servicemanagementservice.model.AppRole;
import com.kovanlabs.servicemanagementservice.model.AppUser;
import com.kovanlabs.servicemanagementservice.model.RolePermissionMapping;
import com.kovanlabs.servicemanagementservice.model.UserRoleMapping;
import com.kovanlabs.servicemanagementservice.model.UserServiceMapping;
import com.kovanlabs.servicemanagementservice.repository.AppRoleRepository;
import com.kovanlabs.servicemanagementservice.repository.AppUserRepository;
import com.kovanlabs.servicemanagementservice.repository.RolePermissionMappingRepository;
import com.kovanlabs.servicemanagementservice.repository.UserRoleMappingRepository;
import com.kovanlabs.servicemanagementservice.repository.UserServiceMappingRepository;
import com.kovanlabs.servicemanagementservice.security.JwtTokenService;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final AppRoleRepository appRoleRepository;
    private final UserRoleMappingRepository userRoleMappingRepository;
    private final RolePermissionMappingRepository rolePermissionMappingRepository;
    private final UserServiceMappingRepository userServiceMappingRepository;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            AppUserRepository appUserRepository,
            AppRoleRepository appRoleRepository,
            UserRoleMappingRepository userRoleMappingRepository,
            RolePermissionMappingRepository rolePermissionMappingRepository,
            UserServiceMappingRepository userServiceMappingRepository,
            JwtTokenService jwtTokenService) {
        this.appUserRepository = appUserRepository;
        this.appRoleRepository = appRoleRepository;
        this.userRoleMappingRepository = userRoleMappingRepository;
        this.rolePermissionMappingRepository = rolePermissionMappingRepository;
        this.userServiceMappingRepository = userServiceMappingRepository;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public AuthResponse loginWithEmail(String email, String displayName) {
        String normalizedEmail = normalizeEmail(email);
        AppUser user = upsertUser(normalizedEmail, displayName);
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse loginFromFederatedIdentity(String email, String displayName) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required from federated identity");
        }
        AppUser user = upsertUser(normalizeEmail(email), displayName);
        return buildAuthResponse(user);
    }

    public AuthResponse getCurrentUserContext(String email) {
        AppUser user = appUserRepository.findByEmailIgnoreCaseAndActiveTrue(normalizeEmail(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authorized"));
        return buildAuthResponse(user);
    }

    private AppUser upsertUser(String email, String displayName) {
        return appUserRepository.findByEmailIgnoreCaseAndActiveTrue(email)
                .orElseGet(() -> {
                    AppUser created = new AppUser();
                    created.setId(UUID.randomUUID().toString());
                    created.setEmail(email);
                    created.setUsername(resolveUsername(email, displayName));
                    created.setActive(true);
                    created.setCreatedAt(LocalDateTime.now());
                    created.setUpdatedAt(LocalDateTime.now());
                    AppUser saved = appUserRepository.save(created);
                    assignDefaultRole(saved);

                    return saved;
                });
    }

    private void assignDefaultRole(AppUser user) {
        AppRole defaultRole = appRoleRepository.findByNameIgnoreCase("DEV")
                .orElseGet(() -> {
                    AppRole role = new AppRole();
                    role.setName("DEV");
                    role.setDescription("Default developer role");
                    role.setCreatedAt(LocalDateTime.now());
                    return appRoleRepository.save(role);
                });

        if (!userRoleMappingRepository.existsByUser_IdAndRole_Id(user.getId(), defaultRole.getId())) {
            UserRoleMapping mapping = new UserRoleMapping();
            mapping.setUser(user);
            mapping.setRole(defaultRole);
            mapping.setAssignedAt(LocalDateTime.now());
            mapping.setUpdatedAt(LocalDateTime.now());
            userRoleMappingRepository.save(mapping);
        }
    }

    private AuthResponse buildAuthResponse(AppUser user) {
        List<UserRoleMapping> roleMappings = userRoleMappingRepository.findByUser_Id(user.getId());
        List<String> roles = roleMappings.stream()
                .map(mapping -> mapping.getRole().getName().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        List<RolePermissionMapping> permissionMappings = rolePermissionMappingRepository.findByRole_IdIn(
                roleMappings.stream().map(mapping -> mapping.getRole().getId()).distinct().toList());

        List<String> permissions = permissionMappings.stream()
                .map(mapping -> mapping.getPermission().getName().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        List<UserServiceMapping> serviceMappings = userServiceMappingRepository.findByUser_Id(user.getId());
        List<String> services = serviceMappings.stream()
                .map(mapping -> mapping.getService().getName())
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();

        String token = jwtTokenService.generateToken(
                user.getId(),
                user.getEmail(),
                roles,
                permissions,
                services);

        return new AuthResponse(
                "Bearer",
                token,
                jwtTokenService.expiresAtEpochSeconds(),
                user.getId(),
                user.getEmail(),
                roles,
                permissions,
                services,
                user.getProfileImageUrl());
    }



    private String resolveUsername(String email, String displayName) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim().toLowerCase(Locale.ROOT).replace(" ", ".");
        }
        int atIndex = email.indexOf('@');
        return atIndex > 0 ? email.substring(0, atIndex) : email;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
