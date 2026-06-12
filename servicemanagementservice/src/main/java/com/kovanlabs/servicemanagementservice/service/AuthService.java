package com.kovanlabs.servicemanagementservice.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.kovanlabs.servicemanagementservice.dto.auth.AuthResponse;
import com.kovanlabs.servicemanagementservice.model.AppRole;
import com.kovanlabs.servicemanagementservice.model.AppUser;
import com.kovanlabs.servicemanagementservice.model.RolePermissionMapping;
import com.kovanlabs.servicemanagementservice.model.UserRoleMapping;
import com.kovanlabs.servicemanagementservice.model.Organization;
import com.kovanlabs.servicemanagementservice.model.UserOrganizationMapping;
import com.kovanlabs.servicemanagementservice.model.AppService;
import com.kovanlabs.servicemanagementservice.repository.AppRoleRepository;
import com.kovanlabs.servicemanagementservice.repository.AppUserRepository;
import com.kovanlabs.servicemanagementservice.repository.RolePermissionMappingRepository;
import com.kovanlabs.servicemanagementservice.repository.UserRoleMappingRepository;
import com.kovanlabs.servicemanagementservice.repository.OrganizationRepository;
import com.kovanlabs.servicemanagementservice.repository.UserOrganizationMappingRepository;
import com.kovanlabs.servicemanagementservice.repository.AppServiceRepository;
import com.kovanlabs.servicemanagementservice.security.JwtTokenService;

@Service
public class AuthService {

    private static final java.util.Set<String> PERSONAL_DOMAINS = java.util.Set.of(
            "gmail.com", "yahoo.com", "outlook.com", "hotmail.com", "icloud.com",
            "aol.com", "live.com", "msn.com"
    );

    private final AppUserRepository appUserRepository;
    private final AppRoleRepository appRoleRepository;
    private final UserRoleMappingRepository userRoleMappingRepository;
    private final RolePermissionMappingRepository rolePermissionMappingRepository;
    private final OrganizationRepository organizationRepository;
    private final UserOrganizationMappingRepository userOrganizationMappingRepository;
    private final AppServiceRepository appServiceRepository;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            AppUserRepository appUserRepository,
            AppRoleRepository appRoleRepository,
            UserRoleMappingRepository userRoleMappingRepository,
            RolePermissionMappingRepository rolePermissionMappingRepository,
            OrganizationRepository organizationRepository,
            UserOrganizationMappingRepository userOrganizationMappingRepository,
            AppServiceRepository appServiceRepository,
            JwtTokenService jwtTokenService) {
        this.appUserRepository = appUserRepository;
        this.appRoleRepository = appRoleRepository;
        this.userRoleMappingRepository = userRoleMappingRepository;
        this.rolePermissionMappingRepository = rolePermissionMappingRepository;
        this.organizationRepository = organizationRepository;
        this.userOrganizationMappingRepository = userOrganizationMappingRepository;
        this.appServiceRepository = appServiceRepository;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public AuthResponse loginWithEmail(String email, String displayName) {
        String normalizedEmail = normalizeEmail(email);
        AppUser user = upsertUser(normalizedEmail, displayName);
        Organization activeOrg = onboardOrGetOrganization(user, displayName);
        return buildAuthResponse(user, activeOrg);
    }

    @Transactional
    public AuthResponse loginFromFederatedIdentity(String email, String displayName) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required from federated identity");
        }
        AppUser user = upsertUser(normalizeEmail(email), displayName);
        Organization activeOrg = onboardOrGetOrganization(user, displayName);
        return buildAuthResponse(user, activeOrg);
    }

    public AuthResponse getCurrentUserContext(String email) {
        AppUser user = appUserRepository.findByEmailIgnoreCaseAndActiveTrue(normalizeEmail(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authorized"));

        List<UserOrganizationMapping> mappings = userOrganizationMappingRepository
                .findByUser_IdAndStatus(user.getId(), "ACTIVE");

        Organization activeOrg;
        if (mappings.isEmpty()) {
            activeOrg = onboardOrGetOrganization(user, user.getUsername());
        } else {
            activeOrg = mappings.get(0).getOrganization();
        }

        return buildAuthResponse(user, activeOrg);
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
                    return appUserRepository.save(created);
                });
    }

    private Organization onboardOrGetOrganization(AppUser user, String displayName) {
        List<UserOrganizationMapping> mappings = userOrganizationMappingRepository
                .findByUser_IdAndStatus(user.getId(), "ACTIVE");

        if (!mappings.isEmpty()) {
            return mappings.get(0).getOrganization();
        }

        String email = user.getEmail();
        int atIndex = email.indexOf('@');
        String domain = atIndex > 0 ? email.substring(atIndex + 1).toLowerCase(Locale.ROOT) : null;

        if (domain != null && !isPersonalDomain(domain)) {
            Optional<Organization> existingOrg = organizationRepository.findByDomainIgnoreCase(domain)
                    .filter(o -> "BUSINESS".equalsIgnoreCase(o.getOrganizationType()));

            if (existingOrg.isPresent()) {
                Organization org = existingOrg.get();
                UserOrganizationMapping mapping = new UserOrganizationMapping();
                mapping.setUser(user);
                mapping.setOrganization(org);
                mapping.setStatus("ACTIVE");
                mapping.setAssignedAt(LocalDateTime.now());
                mapping.setUpdatedAt(LocalDateTime.now());
                userOrganizationMappingRepository.save(mapping);

                assignRoleToUserInOrg(user, org, "DEV");
                return org;
            } else {
                Organization org = new Organization();
                org.setName(capitalize(domain.split("\\.")[0]) + " Business");
                org.setDomain(domain);
                org.setOrganizationType("BUSINESS");
                org.setCreatedAt(LocalDateTime.now());
                org.setUpdatedAt(LocalDateTime.now());
                Organization savedOrg = organizationRepository.save(org);

                UserOrganizationMapping mapping = new UserOrganizationMapping();
                mapping.setUser(user);
                mapping.setOrganization(savedOrg);
                mapping.setStatus("ACTIVE");
                mapping.setAssignedAt(LocalDateTime.now());
                mapping.setUpdatedAt(LocalDateTime.now());
                userOrganizationMappingRepository.save(mapping);

                assignRoleToUserInOrg(user, savedOrg, "ADMIN");
                return savedOrg;
            }
        } else {
            Organization org = new Organization();
            String name = (displayName != null && !displayName.isBlank()) ? displayName.trim() : user.getUsername();
            org.setName(name + "'s Workspace");
            org.setDomain(null);
            org.setOrganizationType("PERSONAL");
            org.setCreatedAt(LocalDateTime.now());
            org.setUpdatedAt(LocalDateTime.now());
            Organization savedOrg = organizationRepository.save(org);

            UserOrganizationMapping mapping = new UserOrganizationMapping();
            mapping.setUser(user);
            mapping.setOrganization(savedOrg);
            mapping.setStatus("ACTIVE");
            mapping.setAssignedAt(LocalDateTime.now());
            mapping.setUpdatedAt(LocalDateTime.now());
            userOrganizationMappingRepository.save(mapping);

            assignRoleToUserInOrg(user, savedOrg, "ADMIN");
            return savedOrg;
        }
    }

    private void assignRoleToUserInOrg(AppUser user, Organization org, String roleName) {
        AppRole role = appRoleRepository.findByNameIgnoreCase(roleName)
                .orElseGet(() -> {
                    AppRole r = new AppRole();
                    r.setName(roleName.toUpperCase(Locale.ROOT));
                    r.setDescription("Provisioned " + roleName + " role");
                    r.setCreatedAt(LocalDateTime.now());
                    return appRoleRepository.save(r);
                });

        if (!userRoleMappingRepository.existsByUser_IdAndOrganization_IdAndRole_Id(user.getId(), org.getId(), role.getId())) {
            UserRoleMapping mapping = new UserRoleMapping();
            mapping.setUser(user);
            mapping.setOrganization(org);
            mapping.setRole(role);
            mapping.setAssignedAt(LocalDateTime.now());
            mapping.setUpdatedAt(LocalDateTime.now());
            userRoleMappingRepository.save(mapping);
        }
    }

    private AuthResponse buildAuthResponse(AppUser user, Organization activeOrg) {
        List<UserRoleMapping> roleMappings = userRoleMappingRepository
                .findByUser_IdAndOrganization_Id(user.getId(), activeOrg.getId());

        List<String> roles = roleMappings.stream()
                .map(mapping -> mapping.getRole().getName().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        List<RolePermissionMapping> permissionMappings = new ArrayList<>();
        if (!roleMappings.isEmpty()) {
            permissionMappings = rolePermissionMappingRepository.findByRole_IdIn(
                    roleMappings.stream().map(mapping -> mapping.getRole().getId()).distinct().toList());
        }

        List<String> permissions = permissionMappings.stream()
                .map(mapping -> mapping.getPermission().getName().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        List<AppService> orgServices = appServiceRepository.findByOrganizationIdAndActiveTrueOrderByNameAsc(activeOrg.getId());
        List<String> services = orgServices.stream()
                .map(AppService::getName)
                .toList();

        String token = jwtTokenService.generateToken(
                user.getId(),
                user.getEmail(),
                activeOrg.getId().toString());

        return new AuthResponse(
                "Bearer",
                token,
                jwtTokenService.expiresAtEpochSeconds(),
                user.getId(),
                user.getEmail(),
                roles,
                permissions,
                services,
                user.getProfileImageUrl(),
                activeOrg.getId().toString());
    }

    private boolean isPersonalDomain(String domain) {
        if (domain == null) return true;
        return PERSONAL_DOMAINS.contains(domain.trim().toLowerCase(Locale.ROOT));
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase(Locale.ROOT) + str.substring(1);
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
