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
    public ResponseEntity<List<ServiceSummaryView>> services() {
        return ResponseEntity.ok(workflowService.listServices());
    }

    @GetMapping("/health")
    public ResponseEntity<List<ServiceHealthView>> getServicesHealth() {
        return ResponseEntity.ok(healthService.getServicesHealth());
    }

    @GetMapping("/details")
    public ResponseEntity<List<ServiceSummaryView>> details() {
        return ResponseEntity.ok(workflowService.listServices());
    }

    @GetMapping("/requests")
    public ResponseEntity<List<ServiceRequestView>> requests(
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        boolean includeAll = userRole != null && userRole.equalsIgnoreCase("admin");
        return ResponseEntity.ok(workflowService.listRequests(userEmail, includeAll));
    }

    @GetMapping("/requests/mine")
    public ResponseEntity<List<ServiceRequestView>> myRequests(
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        return ResponseEntity.ok(workflowService.listRequests(userEmail, false));
    }

    @PostMapping({"/request", "/requests"})
    public ResponseEntity<ServiceRequestView> requestService(
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestBody ServiceRequestCreateRequest request) {
        return ResponseEntity.status(201).body(workflowService.createRequest(request, userEmail));
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<ServiceRequestView> approveRequest(
            @PathVariable("requestId") String requestId,
            @RequestBody(required = false) ServiceRequestCreateRequest request) {
        return ResponseEntity.ok(workflowService.approveRequest(requestId, request));
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<ServiceRequestView> rejectRequest(@PathVariable("requestId") String requestId) {
        return ResponseEntity.ok(workflowService.rejectRequest(requestId));
    }

    @GetMapping("/requests/{requestId}")
    public ResponseEntity<ServiceRequestView> requestById(@PathVariable("requestId") String requestId) {
        return ResponseEntity.ok(workflowService.getRequestById(requestId));
    }

    @GetMapping("/{serviceName}/approved")
    public ResponseEntity<java.util.Map<String, Boolean>> checkApproval(@PathVariable("serviceName") String serviceName) {
        return ResponseEntity.ok(java.util.Map.of("approved", workflowService.isServiceApproved(serviceName)));
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
}