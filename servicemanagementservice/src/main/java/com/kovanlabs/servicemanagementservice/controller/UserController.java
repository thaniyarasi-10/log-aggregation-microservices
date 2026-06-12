package com.kovanlabs.servicemanagementservice.controller;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.kovanlabs.servicemanagementservice.model.AppUser;
import com.kovanlabs.servicemanagementservice.service.UserService;
import com.kovanlabs.servicemanagementservice.service.OrganizationService;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final OrganizationService organizationService;

    public UserController(UserService userService, OrganizationService organizationService) {
        this.userService = userService;
        this.organizationService = organizationService;
    }

    @org.springframework.web.bind.annotation.GetMapping("/by-email")
    public ResponseEntity<AppUser> getUserByEmail(@org.springframework.web.bind.annotation.RequestParam("email") String email) {
        return ResponseEntity.ok(userService.getUserByEmail(email));
    }

    @PostMapping(value = "/upload-profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadProfileImage(
            @RequestHeader(value = "X-User-Email", required = false) String proxiedUserEmail,
            Principal principal,
            @RequestParam("file") MultipartFile file) {

        String email = proxiedUserEmail;
        if ((email == null || email.isBlank()) && principal != null) {
            email = principal.getName();
        }

        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty or missing");
        }

        // Validate file size (max 5MB)
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File size exceeds the limit of 5MB");
        }

        // Validate MIME type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image files are allowed");
        }

        AppUser user = userService.getUserByEmail(email);
        String imageUrl = userService.uploadProfileImage(user.getId(), file);

        return ResponseEntity.ok(Map.of(
                "message", "Image uploaded successfully",
                "imageUrl", imageUrl
        ));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{userId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ORGANIZATION:MANAGE')")
    public ResponseEntity<Void> removeMember(
            @RequestHeader(value = "X-Organization-Id") String organizationId,
            @RequestHeader(value = "X-User-Email", required = false) String proxiedUserEmail,
            Principal principal,
            @PathVariable("userId") String userId) {
        String actorEmail = getEmail(proxiedUserEmail, principal);
        UUID orgId = UUID.fromString(organizationId);
        organizationService.removeMember(orgId, userId, actorEmail);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/promote")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ORGANIZATION:MANAGE')")
    public ResponseEntity<Void> promoteMember(
            @RequestHeader(value = "X-Organization-Id") String organizationId,
            @RequestHeader(value = "X-User-Email", required = false) String proxiedUserEmail,
            Principal principal,
            @PathVariable("userId") String userId) {
        String actorEmail = getEmail(proxiedUserEmail, principal);
        UUID orgId = UUID.fromString(organizationId);
        organizationService.changeMemberRole(orgId, userId, "ADMIN", actorEmail);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/demote")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ORGANIZATION:MANAGE')")
    public ResponseEntity<Void> demoteMember(
            @RequestHeader(value = "X-Organization-Id") String organizationId,
            @RequestHeader(value = "X-User-Email", required = false) String proxiedUserEmail,
            Principal principal,
            @PathVariable("userId") String userId) {
        String actorEmail = getEmail(proxiedUserEmail, principal);
        UUID orgId = UUID.fromString(organizationId);
        organizationService.changeMemberRole(orgId, userId, "DEV", actorEmail);
        return ResponseEntity.ok().build();
    }

    private String getEmail(String headerEmail, Principal principal) {
        String email = headerEmail;
        if ((email == null || email.isBlank()) && principal != null) {
            email = principal.getName();
        }
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }
        return email;
    }
}
