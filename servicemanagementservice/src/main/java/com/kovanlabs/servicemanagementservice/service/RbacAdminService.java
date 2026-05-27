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


import com.kovanlabs.servicemanagementservice.model.AppPermission;
import com.kovanlabs.servicemanagementservice.model.AppRole;
import com.kovanlabs.servicemanagementservice.model.AppUser;
import com.kovanlabs.servicemanagementservice.model.RolePermissionMapping;
import com.kovanlabs.servicemanagementservice.model.UserRoleMapping;
import com.kovanlabs.servicemanagementservice.repository.AppPermissionRepository;
import com.kovanlabs.servicemanagementservice.repository.AppRoleRepository;
import com.kovanlabs.servicemanagementservice.repository.AppUserRepository;
import com.kovanlabs.servicemanagementservice.repository.RolePermissionMappingRepository;
import com.kovanlabs.servicemanagementservice.repository.UserRoleMappingRepository;

@Service
public class RbacAdminService {

    private final AppUserRepository appUserRepository;
    private final AppRoleRepository appRoleRepository;
    private final AppPermissionRepository appPermissionRepository;
    private final UserRoleMappingRepository userRoleMappingRepository;
    private final RolePermissionMappingRepository rolePermissionMappingRepository;

    public RbacAdminService(
            AppUserRepository appUserRepository,
            AppRoleRepository appRoleRepository,
            AppPermissionRepository appPermissionRepository,
            UserRoleMappingRepository userRoleMappingRepository,
            RolePermissionMappingRepository rolePermissionMappingRepository) {
        this.appUserRepository = appUserRepository;
        this.appRoleRepository = appRoleRepository;
        this.appPermissionRepository = appPermissionRepository;
        this.userRoleMappingRepository = userRoleMappingRepository;
        this.rolePermissionMappingRepository = rolePermissionMappingRepository;
    }

    public List<AppUser> users() {
        return appUserRepository.findAll();
    }

    public List<AppRole> roles() {
        return appRoleRepository.findAll();
    }

    public List<AppPermission> permissions() {
        return appPermissionRepository.findAll();
    }

    @Transactional
    public void assignRoleToUser(String userId, String roleName) {
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

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }
}
