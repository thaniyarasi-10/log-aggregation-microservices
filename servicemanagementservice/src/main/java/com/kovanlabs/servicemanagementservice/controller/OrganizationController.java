package com.kovanlabs.servicemanagementservice.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.kovanlabs.servicemanagementservice.dto.auth.AuthResponse;
import com.kovanlabs.servicemanagementservice.model.Organization;
import com.kovanlabs.servicemanagementservice.model.OrganizationInvite;
import com.kovanlabs.servicemanagementservice.model.OrganizationJoinRequest;
import com.kovanlabs.servicemanagementservice.model.OrganizationApiKey;
import com.kovanlabs.servicemanagementservice.service.OrganizationService;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    public record CreateOrgRequest(String name, String type) {}
    public record SwitchOrgRequest(String organizationId) {}
    public record InviteUserRequest(String organizationId, String email) {}
    public record CreateApiKeyRequest(String organizationId, String name) {}

    @PostMapping
    public ResponseEntity<Organization> createOrganization(
            @RequestHeader(value = "X-User-Email", required = false) String proxiedUserEmail,
            Principal principal,
            @RequestBody CreateOrgRequest request) {
        String email = getEmail(proxiedUserEmail, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationService.createOrganization(request.name(), request.type(), email));
    }

    @PostMapping("/switch")
    public ResponseEntity<AuthResponse> switchOrganization(
            @RequestHeader(value = "X-User-Email", required = false) String proxiedUserEmail,
            Principal principal,
            @RequestBody SwitchOrgRequest request) {
        String email = getEmail(proxiedUserEmail, principal);
        UUID orgId = UUID.fromString(request.organizationId());
        return ResponseEntity.ok(organizationService.switchOrganization(orgId, email));
    }

    @PostMapping("/invite")
    @PreAuthorize("hasAuthority('ORGANIZATION:MANAGE')")
    public ResponseEntity<OrganizationInvite> inviteUser(
            @RequestHeader(value = "X-User-Email", required = false) String proxiedUserEmail,
            Principal principal,
            @RequestBody InviteUserRequest request) {
        String email = getEmail(proxiedUserEmail, principal);
        UUID orgId = UUID.fromString(request.organizationId());
        return ResponseEntity.ok(organizationService.inviteUser(orgId, request.email(), email));
    }

    @PostMapping("/invite/accept")
    public ResponseEntity<Void> acceptInvite(
            @RequestHeader(value = "X-User-Email", required = false) String proxiedUserEmail,
            Principal principal,
            @RequestParam("inviteId") String inviteId) {
        String email = getEmail(proxiedUserEmail, principal);
        organizationService.acceptInvite(UUID.fromString(inviteId), email);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/join-request")
    public ResponseEntity<OrganizationJoinRequest> requestToJoin(
            @RequestHeader(value = "X-User-Email", required = false) String proxiedUserEmail,
            Principal principal,
            @RequestParam("organizationId") String organizationId) {
        String email = getEmail(proxiedUserEmail, principal);
        return ResponseEntity.ok(organizationService.requestToJoin(UUID.fromString(organizationId), email));
    }

    @PostMapping("/join-request/{requestId}/approve")
    @PreAuthorize("hasAuthority('ORGANIZATION:MANAGE')")
    public ResponseEntity<Void> approveJoinRequest(
            @RequestHeader(value = "X-User-Email", required = false) String proxiedUserEmail,
            Principal principal,
            @PathVariable("requestId") String requestId) {
        String email = getEmail(proxiedUserEmail, principal);
        organizationService.approveJoinRequest(UUID.fromString(requestId), email);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/join-request/{requestId}/reject")
    @PreAuthorize("hasAuthority('ORGANIZATION:MANAGE')")
    public ResponseEntity<Void> rejectJoinRequest(
            @RequestHeader(value = "X-User-Email", required = false) String proxiedUserEmail,
            Principal principal,
            @PathVariable("requestId") String requestId) {
        String email = getEmail(proxiedUserEmail, principal);
        organizationService.rejectJoinRequest(UUID.fromString(requestId), email);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api-keys")
    @PreAuthorize("hasAuthority('API-KEYS:MANAGE')")
    public ResponseEntity<Map<String, String>> createApiKey(
            @RequestHeader(value = "X-User-Email", required = false) String proxiedUserEmail,
            Principal principal,
            @RequestBody CreateApiKeyRequest request) {
        String email = getEmail(proxiedUserEmail, principal);
        UUID orgId = UUID.fromString(request.organizationId());
        String rawKey = organizationService.createApiKey(orgId, request.name(), email);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("apiKey", rawKey));
    }

    @GetMapping("/api-keys")
    @PreAuthorize("hasAuthority('API-KEYS:MANAGE')")
    public ResponseEntity<List<OrganizationApiKey>> listApiKeys(
            @RequestHeader(value = "X-User-Email", required = false) String proxiedUserEmail,
            Principal principal,
            @RequestParam("organizationId") String organizationId) {
        String email = getEmail(proxiedUserEmail, principal);
        UUID orgId = UUID.fromString(organizationId);
        return ResponseEntity.ok(organizationService.listApiKeys(orgId, email));
    }

    private String getEmail(String headerEmail, Principal principal) {
        String email = headerEmail;
        if ((email == null || email.isBlank()) && principal != null) {
            email = principal.getName();
        }
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User email not found in request context");
        }
        return email;
    }
}
