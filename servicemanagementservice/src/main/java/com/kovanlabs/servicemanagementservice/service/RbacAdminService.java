package com.kovanlabs.servicemanagementservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;


import com.kovanlabs.servicemanagementservice.dto.rbac.UserSummaryView;
import com.kovanlabs.servicemanagementservice.model.AppPermission;
import com.kovanlabs.servicemanagementservice.model.AppRole;
import com.kovanlabs.servicemanagementservice.model.AppService;
import com.kovanlabs.servicemanagementservice.model.AppUser;
import com.kovanlabs.servicemanagementservice.model.RolePermissionMapping;
import com.kovanlabs.servicemanagementservice.model.UserRoleMapping;
import com.kovanlabs.servicemanagementservice.model.UserServiceMapping;
import com.kovanlabs.servicemanagementservice.repository.AppPermissionRepository;
import com.kovanlabs.servicemanagementservice.repository.AppRoleRepository;
import com.kovanlabs.servicemanagementservice.repository.AppServiceRepository;
import com.kovanlabs.servicemanagementservice.repository.AppUserRepository;
import com.kovanlabs.servicemanagementservice.repository.RolePermissionMappingRepository;
import com.kovanlabs.servicemanagementservice.repository.UserRoleMappingRepository;
import com.kovanlabs.servicemanagementservice.repository.UserServiceMappingRepository;

@Service
public class RbacAdminService {

    private final AppUserRepository appUserRepository;
    private final AppRoleRepository appRoleRepository;
    private final AppPermissionRepository appPermissionRepository;
    private final UserRoleMappingRepository userRoleMappingRepository;
    private final RolePermissionMappingRepository rolePermissionMappingRepository;
    private final UserServiceMappingRepository userServiceMappingRepository;
    private final AppServiceRepository appServiceRepository;

    public RbacAdminService(
            AppUserRepository appUserRepository,
            AppRoleRepository appRoleRepository,
            AppPermissionRepository appPermissionRepository,
            UserRoleMappingRepository userRoleMappingRepository,
            RolePermissionMappingRepository rolePermissionMappingRepository,
            UserServiceMappingRepository userServiceMappingRepository,
            AppServiceRepository appServiceRepository) {
        this.appUserRepository = appUserRepository;
        this.appRoleRepository = appRoleRepository;
        this.appPermissionRepository = appPermissionRepository;
        this.userRoleMappingRepository = userRoleMappingRepository;
        this.rolePermissionMappingRepository = rolePermissionMappingRepository;
        this.userServiceMappingRepository = userServiceMappingRepository;
        this.appServiceRepository = appServiceRepository;
    }

    public List<UserSummaryView> users() {
        return appUserRepository.findAll().stream()
                .map(this::toUserSummaryView)
                .toList();
    }

    private UserSummaryView toUserSummaryView(AppUser user) {
        List<UserRoleMapping> roleMappings = userRoleMappingRepository.findByUser_Id(user.getId());
        List<String> roles = roleMappings.stream()
                .map(mapping -> mapping.getRole().getName().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        List<UserServiceMapping> serviceMappings = userServiceMappingRepository.findByUser_Id(user.getId());
        List<String> services = serviceMappings.stream()
                .map(mapping -> mapping.getService().getName())
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();

        return new UserSummaryView(
                user.getId(),
                user.getUsername(),
                user.getUsername(),
                user.getEmail(),
                roles,
                services);
    }

    public List<AppRole> roles() {
        return appRoleRepository.findAll();
    }

    public List<AppPermission> permissions() {
        return appPermissionRepository.findAll();
    }

    @Transactional
    public void assignRoleToUser(String userId, String roleName, UUID organizationId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        AppRole role = appRoleRepository.findByNameIgnoreCase(required(roleName, "Role name is required"))
                .orElseGet(() -> {
                    AppRole created = new AppRole();
                    created.setName(roleName.toUpperCase(Locale.ROOT));
                    created.setDescription("Provisioned role");
                    created.setCreatedAt(LocalDateTime.now());
                    return appRoleRepository.save(created);
                });

        if (!userRoleMappingRepository.existsByUser_IdAndRole_Id(user.getId(), role.getId())) {
            UserRoleMapping mapping = new UserRoleMapping();
            mapping.setUser(user);
            mapping.setRole(role);
            mapping.setAssignedAt(LocalDateTime.now());
            mapping.setUpdatedAt(LocalDateTime.now());
            mapping.setOrganizationId(organizationId != null ? organizationId : UUID.fromString("00000000-0000-0000-0000-000000000000"));
            userRoleMappingRepository.save(mapping);
        }


    }

    @Transactional
    public void assignPermissionToRole(String roleName, String permissionName) {
        AppRole role = appRoleRepository.findByNameIgnoreCase(required(roleName, "Role name is required"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));

        AppPermission permission = appPermissionRepository.findByNameIgnoreCase(required(permissionName, "Permission name is required"))
                .orElseGet(() -> {
                    AppPermission created = new AppPermission();
                    created.setName(permissionName.toUpperCase(Locale.ROOT));
                    created.setResource("services");
                    created.setDescription("Provisioned permission");
                    created.setCreatedAt(LocalDateTime.now());
                    return appPermissionRepository.save(created);
                });

        if (!rolePermissionMappingRepository.existsByRole_IdAndPermission_Id(role.getId(), permission.getId())) {
            RolePermissionMapping mapping = new RolePermissionMapping();
            mapping.setRole(role);
            mapping.setPermission(permission);
            mapping.setAssignedAt(LocalDateTime.now());
            mapping.setUpdatedAt(LocalDateTime.now());
            rolePermissionMappingRepository.save(mapping);
        }


    }

    @Transactional
    public UserSummaryView createUser(UserSummaryView request) {
        if (request.id() == null || request.id().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User ID is required");
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        if (appUserRepository.existsById(request.id())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User with ID " + request.id() + " already exists");
        }
        if (appUserRepository.findByEmailIgnoreCase(request.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User with email " + request.email() + " already exists");
        }

        AppUser user = new AppUser();
        user.setId(request.id().trim());
        user.setUsername(request.username() != null && !request.username().isBlank() ? request.username().trim() : request.id().trim().toLowerCase());
        user.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        AppUser savedUser = appUserRepository.save(user);

        // Assign Roles
        if (request.roles() != null) {
            for (String roleName : request.roles()) {
                if (roleName != null && !roleName.isBlank()) {
                    AppRole role = appRoleRepository.findByNameIgnoreCase(roleName.trim())
                            .orElseGet(() -> {
                                AppRole created = new AppRole();
                                created.setId(UUID.randomUUID());
                                created.setName(roleName.trim().toUpperCase(Locale.ROOT));
                                created.setDescription("Provisioned role");
                                created.setCreatedAt(LocalDateTime.now());
                                return appRoleRepository.save(created);
                            });

                    if (!userRoleMappingRepository.existsByUser_IdAndRole_Id(savedUser.getId(), role.getId())) {
                        UserRoleMapping mapping = new UserRoleMapping();
                        mapping.setUser(savedUser);
                        mapping.setRole(role);
                        mapping.setAssignedAt(LocalDateTime.now());
                        mapping.setUpdatedAt(LocalDateTime.now());
                        mapping.setOrganizationId(UUID.fromString("00000000-0000-0000-0000-000000000000"));
                        userRoleMappingRepository.save(mapping);
                    }
                }
            }
        }

        // Assign Services
        if (request.services() != null) {
            for (String serviceName : request.services()) {
                if (serviceName != null && !serviceName.isBlank()) {
                    AppService service = appServiceRepository.findByNameIgnoreCase(serviceName.trim())
                            .orElseGet(() -> {
                                AppService created = new AppService();
                                created.setId(UUID.randomUUID());
                                created.setName(serviceName.trim().toLowerCase(Locale.ROOT));
                                created.setDescription("Auto-seeded system service");
                                created.setActive(true);
                                created.setCreatedAt(LocalDateTime.now());
                                created.setUpdatedAt(LocalDateTime.now());
                                return appServiceRepository.save(created);
                            });

                    if (!userServiceMappingRepository.existsByUser_IdAndService_Id(savedUser.getId(), service.getId())) {
                        UserServiceMapping mapping = new UserServiceMapping();
                        mapping.setUser(savedUser);
                        mapping.setService(service);
                        List<UserServiceMapping> serviceMappings = userServiceMappingRepository.findByService_Id(service.getId());
                        boolean hasPrimary = serviceMappings.stream().anyMatch(UserServiceMapping::isPrimary);
                        mapping.setPrimary(!hasPrimary);
                        mapping.setCreatedAt(LocalDateTime.now());
                        mapping.setUpdatedAt(LocalDateTime.now());
                        userServiceMappingRepository.save(mapping);
                    }
                }
            }
        }

        return toUserSummaryView(savedUser);
    }

    @Transactional
    public UserSummaryView updateUser(String userId, UserSummaryView request) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }

        appUserRepository.findByEmailIgnoreCase(request.email().trim()).ifPresent(existing -> {
            if (!existing.getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already taken by another user");
            }
        });

        user.setUsername(request.username() != null && !request.username().isBlank() ? request.username().trim() : user.getUsername());
        user.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
        user.setUpdatedAt(LocalDateTime.now());
        AppUser savedUser = appUserRepository.save(user);

        // Delete old role mappings
        List<UserRoleMapping> existingRoleMappings = userRoleMappingRepository.findByUser_Id(userId);
        userRoleMappingRepository.deleteAll(existingRoleMappings);

        // Delete old service mappings
        List<UserServiceMapping> existingServiceMappings = userServiceMappingRepository.findByUser_Id(userId);
        userServiceMappingRepository.deleteAll(existingServiceMappings);

        // Assign Roles
        if (request.roles() != null) {
            for (String roleName : request.roles()) {
                if (roleName != null && !roleName.isBlank()) {
                    AppRole role = appRoleRepository.findByNameIgnoreCase(roleName.trim())
                            .orElseGet(() -> {
                                AppRole created = new AppRole();
                                created.setId(UUID.randomUUID());
                                created.setName(roleName.trim().toUpperCase(Locale.ROOT));
                                created.setDescription("Provisioned role");
                                created.setCreatedAt(LocalDateTime.now());
                                return appRoleRepository.save(created);
                            });

                    UserRoleMapping mapping = new UserRoleMapping();
                    mapping.setUser(savedUser);
                    mapping.setRole(role);
                    mapping.setAssignedAt(LocalDateTime.now());
                    mapping.setUpdatedAt(LocalDateTime.now());
                    mapping.setOrganizationId(UUID.fromString("00000000-0000-0000-0000-000000000000"));
                    userRoleMappingRepository.save(mapping);
                }
            }
        }

        // Assign Services
        if (request.services() != null) {
            for (String serviceName : request.services()) {
                if (serviceName != null && !serviceName.isBlank()) {
                    AppService service = appServiceRepository.findByNameIgnoreCase(serviceName.trim())
                            .orElseGet(() -> {
                                AppService created = new AppService();
                                created.setId(UUID.randomUUID());
                                created.setName(serviceName.trim().toLowerCase(Locale.ROOT));
                                created.setDescription("Auto-seeded system service");
                                created.setActive(true);
                                created.setCreatedAt(LocalDateTime.now());
                                created.setUpdatedAt(LocalDateTime.now());
                                return appServiceRepository.save(created);
                            });

                    UserServiceMapping mapping = new UserServiceMapping();
                    mapping.setUser(savedUser);
                    mapping.setService(service);
                    List<UserServiceMapping> serviceMappings = userServiceMappingRepository.findByService_Id(service.getId());
                    boolean hasPrimary = serviceMappings.stream().anyMatch(UserServiceMapping::isPrimary);
                    mapping.setPrimary(!hasPrimary);
                    mapping.setCreatedAt(LocalDateTime.now());
                    mapping.setUpdatedAt(LocalDateTime.now());
                    userServiceMappingRepository.save(mapping);
                }
            }
        }

        return toUserSummaryView(savedUser);
    }

    @Transactional
    public void deleteUser(String userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<UserRoleMapping> existingRoleMappings = userRoleMappingRepository.findByUser_Id(userId);
        userRoleMappingRepository.deleteAll(existingRoleMappings);

        List<UserServiceMapping> existingServiceMappings = userServiceMappingRepository.findByUser_Id(userId);
        userServiceMappingRepository.deleteAll(existingServiceMappings);

        appUserRepository.delete(user);
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }
}
