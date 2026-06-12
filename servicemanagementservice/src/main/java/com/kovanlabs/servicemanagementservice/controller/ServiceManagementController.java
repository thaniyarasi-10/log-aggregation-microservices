package com.kovanlabs.servicemanagementservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kovanlabs.servicemanagementservice.dto.ServiceRequestCreateRequest;
import com.kovanlabs.servicemanagementservice.dto.ServiceRequestView;
import com.kovanlabs.servicemanagementservice.dto.ServiceSummaryView;
import com.kovanlabs.servicemanagementservice.dto.ServiceHealthView;
import com.kovanlabs.servicemanagementservice.dto.ServiceVerifyRequest;
import com.kovanlabs.servicemanagementservice.dto.ServiceSecretRegenerateResponse;
import com.kovanlabs.servicemanagementservice.dto.ServiceSecretResponse;
import com.kovanlabs.servicemanagementservice.dto.ServiceApiKeyResponse;
import com.kovanlabs.servicemanagementservice.dto.ServiceApiKeyRegenerateResponse;
import com.kovanlabs.servicemanagementservice.model.AppService;
import com.kovanlabs.servicemanagementservice.service.ServiceRequestWorkflowService;
import com.kovanlabs.servicemanagementservice.service.ServiceHealthService;

@RestController
@RequestMapping("/api/services")
public class ServiceManagementController {

    private final ServiceRequestWorkflowService workflowService;
    private final ServiceHealthService healthService;

    public ServiceManagementController(ServiceRequestWorkflowService workflowService,
                                       ServiceHealthService healthService) {
        this.workflowService = workflowService;
        this.healthService = healthService;
    }

    @GetMapping
    public ResponseEntity<List<ServiceSummaryView>> services(
            @RequestHeader(value = "X-Organization-Id") String orgIdStr,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        UUID orgId = UUID.fromString(orgIdStr);
        return ResponseEntity.ok(workflowService.listServices(orgId, userEmail, userRole));
    }

    @GetMapping("/health")
    public ResponseEntity<List<ServiceHealthView>> getServicesHealth(
            @RequestHeader(value = "X-Organization-Id") String orgIdStr,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        UUID orgId = UUID.fromString(orgIdStr);
        return ResponseEntity.ok(healthService.getServicesHealth(orgId, userEmail, userRole));
    }

    @GetMapping("/details")
    public ResponseEntity<List<ServiceSummaryView>> details(
            @RequestHeader(value = "X-Organization-Id") String orgIdStr,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        UUID orgId = UUID.fromString(orgIdStr);
        return ResponseEntity.ok(workflowService.listServices(orgId, userEmail, userRole));
    }

    @GetMapping("/requests")
    public ResponseEntity<List<ServiceRequestView>> requests(
            @RequestHeader(value = "X-Organization-Id") String orgIdStr,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        UUID orgId = UUID.fromString(orgIdStr);
        boolean includeAll = userRole != null && userRole.equalsIgnoreCase("admin");
        return ResponseEntity.ok(workflowService.listRequests(orgId, userEmail, includeAll));
    }

    @GetMapping("/requests/mine")
    public ResponseEntity<List<ServiceRequestView>> myRequests(
            @RequestHeader(value = "X-Organization-Id") String orgIdStr,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        UUID orgId = UUID.fromString(orgIdStr);
        return ResponseEntity.ok(workflowService.listRequests(orgId, userEmail, false));
    }

    @PostMapping({"/request", "/requests"})
    public ResponseEntity<ServiceRequestView> requestService(
            @RequestHeader(value = "X-Organization-Id") String orgIdStr,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestBody ServiceRequestCreateRequest request) {
        UUID orgId = UUID.fromString(orgIdStr);
        return ResponseEntity.status(201).body(workflowService.createRequest(orgId, request, userEmail));
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<ServiceRequestView> approveRequest(
            @RequestHeader(value = "X-Organization-Id") String orgIdStr,
            @PathVariable("requestId") String requestId,
            @RequestBody(required = false) ServiceRequestCreateRequest request) {
        UUID orgId = UUID.fromString(orgIdStr);
        return ResponseEntity.ok(workflowService.approveRequest(orgId, requestId, request));
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<ServiceRequestView> rejectRequest(
            @RequestHeader(value = "X-Organization-Id") String orgIdStr,
            @PathVariable("requestId") String requestId) {
        UUID orgId = UUID.fromString(orgIdStr);
        return ResponseEntity.ok(workflowService.rejectRequest(orgId, requestId));
    }

    @GetMapping("/requests/{requestId}")
    public ResponseEntity<ServiceRequestView> requestById(
            @RequestHeader(value = "X-Organization-Id") String orgIdStr,
            @PathVariable("requestId") String requestId) {
        UUID orgId = UUID.fromString(orgIdStr);
        return ResponseEntity.ok(workflowService.getRequestById(orgId, requestId));
    }

    @GetMapping("/{serviceName}/approved")
    public ResponseEntity<java.util.Map<String, Boolean>> checkApproval(
            @RequestHeader(value = "X-Organization-Id") String orgIdStr,
            @PathVariable("serviceName") String serviceName) {
        UUID orgId = UUID.fromString(orgIdStr);
        return ResponseEntity.ok(java.util.Map.of("approved", workflowService.isServiceApproved(orgId, serviceName)));
    }

    @PostMapping("/{serviceName}/primary-owner")
    public ResponseEntity<?> setPrimaryOwner(
            @PathVariable("serviceName") String serviceName,
            @RequestParam("userId") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        if (userRole == null || !userRole.equalsIgnoreCase("admin")) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body("Only administrators can assign primary owners");
        }
        workflowService.setPrimaryOwner(serviceName, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<java.util.Map<String, Object>> verifyService(@RequestBody ServiceVerifyRequest request) {
        return ResponseEntity.ok(workflowService.verifyServiceSecret(request.apiKey(), request.serviceSecret()));
    }

    @GetMapping("/{serviceId}/secret")
    public ResponseEntity<ServiceSecretResponse> getServiceSecret(
            @RequestHeader(value = "X-Organization-Id") String orgIdStr,
            @PathVariable("serviceId") String serviceId,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        try {
            UUID orgId = UUID.fromString(orgIdStr);
            UUID uuid = UUID.fromString(serviceId);
            ServiceSecretResponse response = workflowService.getServiceSecret(orgId, uuid, userEmail, userRole);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{serviceId}/regenerate-secret")
    public ResponseEntity<ServiceSecretRegenerateResponse> regenerateSecret(
            @RequestHeader(value = "X-Organization-Id") String orgIdStr,
            @PathVariable("serviceId") String serviceId,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        try {
            UUID orgId = UUID.fromString(orgIdStr);
            UUID uuid = UUID.fromString(serviceId);
            AppService updated = workflowService.regenerateServiceSecret(orgId, uuid, userEmail, userRole);
            return ResponseEntity.ok(new ServiceSecretRegenerateResponse(
                    updated.getId(),
                    updated.getName(),
                    updated.getServiceSecret()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{serviceId}/api-key")
    public ResponseEntity<ServiceApiKeyResponse> getServiceApiKey(
            @RequestHeader(value = "X-Organization-Id") String orgIdStr,
            @PathVariable("serviceId") String serviceId,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        try {
            UUID orgId = UUID.fromString(orgIdStr);
            UUID uuid = UUID.fromString(serviceId);
            ServiceApiKeyResponse response = workflowService.getServiceApiKey(orgId, uuid, userEmail, userRole);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{serviceId}/regenerate-api-key")
    public ResponseEntity<ServiceApiKeyRegenerateResponse> regenerateApiKey(
            @RequestHeader(value = "X-Organization-Id") String orgIdStr,
            @PathVariable("serviceId") String serviceId,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        try {
            UUID orgId = UUID.fromString(orgIdStr);
            UUID uuid = UUID.fromString(serviceId);
            AppService updated = workflowService.regenerateServiceApiKey(orgId, uuid, userEmail, userRole);
            return ResponseEntity.ok(new ServiceApiKeyRegenerateResponse(
                    updated.getId(),
                    updated.getName(),
                    updated.getApiKey()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}