package com.kovanlabs.servicemanagementservice.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kovanlabs.servicemanagementservice.dto.rbac.AssignPermissionRequest;
import com.kovanlabs.servicemanagementservice.dto.rbac.AssignRoleRequest;
import com.kovanlabs.servicemanagementservice.dto.rbac.UserSummaryView;
import com.kovanlabs.servicemanagementservice.model.AppPermission;
import com.kovanlabs.servicemanagementservice.model.AppRole;
import com.kovanlabs.servicemanagementservice.service.RbacAdminService;

@RestController
@RequestMapping("/api/rbac")
public class RbacController {

    private final RbacAdminService rbacAdminService;

    public RbacController(RbacAdminService rbacAdminService) {
        this.rbacAdminService = rbacAdminService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserSummaryView>> users() {
        return ResponseEntity.ok(rbacAdminService.users());
    }

    @PostMapping("/users")
    public ResponseEntity<UserSummaryView> createUser(@RequestBody UserSummaryView request) {
        return ResponseEntity.status(201).body(rbacAdminService.createUser(request));
    }

    @PostMapping("/users/{userId}")
    public ResponseEntity<UserSummaryView> updateUser(
            @PathVariable("userId") String userId,
            @RequestBody UserSummaryView request) {
        return ResponseEntity.ok(rbacAdminService.updateUser(userId, request));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable("userId") String userId) {
        rbacAdminService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/roles")
    public ResponseEntity<List<AppRole>> roles() {
        return ResponseEntity.ok(rbacAdminService.roles());
    }

    @GetMapping("/permissions")
    public ResponseEntity<List<AppPermission>> permissions() {
        return ResponseEntity.ok(rbacAdminService.permissions());
    }

    @PostMapping("/users/{userId}/roles")
    public ResponseEntity<Void> assignRole(
            @PathVariable("userId") String userId,
            @RequestBody AssignRoleRequest request) {
        rbacAdminService.assignRoleToUser(userId, request.roleName(), request.organizationId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/roles/{roleName}/permissions")
    public ResponseEntity<Void> assignPermission(
            @PathVariable("roleName") String roleName,
            @RequestBody AssignPermissionRequest request) {
        rbacAdminService.assignPermissionToRole(roleName, request.permissionName());
        return ResponseEntity.noContent().build();
    }
}
