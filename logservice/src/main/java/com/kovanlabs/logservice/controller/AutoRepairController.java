package com.kovanlabs.logservice.controller;

import com.kovanlabs.logservice.auth.AuthenticatedUserContext;
import com.kovanlabs.logservice.auth.UserRole;
import com.kovanlabs.logservice.model.AutoRepairApplyRequest;
import com.kovanlabs.logservice.model.AutoRepairApplyResponse;
import com.kovanlabs.logservice.model.AutoRepairResponse;
import com.kovanlabs.logservice.model.AutoRepairSuggestRequest;
import com.kovanlabs.logservice.service.AutoRepairService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/logs/autorepair")
public class AutoRepairController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoRepairController.class);
    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("AUDIT");

    private final AutoRepairService autoRepairService;

    public AutoRepairController(AutoRepairService autoRepairService) {
        this.autoRepairService = autoRepairService;
    }


    @PostMapping("/suggest")
    public ResponseEntity<?> suggestRepair(
            @RequestBody AutoRepairSuggestRequest request,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Services", required = false) String userServices) {

        LOGGER.info("REST request to suggest repair for service={} file={}", request.getService(), request.getFileName());

        // 1. RBAC and Service validation
        AuthenticatedUserContext context = buildAccessContext(userEmail, userRole, userServices);
        
        if (request.getService() == null || request.getService().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Service name is required"));
        }
        if (request.getFileName() == null || request.getFileName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File name is required"));
        }

        if (!context.isServiceAllowed(request.getService())) {
            LOGGER.warn("User {} forbidden from requesting repair for service {}", context.email(), request.getService());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied. You do not have permissions for the service: " + request.getService()));
        }

        try {
            AutoRepairResponse response = autoRepairService.suggestRepair(request);
            
            // Audit Log
            AUDIT_LOGGER.info("[AUDIT] USER={} ACTION=SUGGEST_REPAIR SERVICE={} FILE={} TIMESTAMP={}",
                    context.email(), request.getService(), request.getFileName(), Instant.now());

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid arguments in repair suggestion request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("Failed to generate repair suggestion: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to generate repair suggestion: " + e.getMessage()));
        }
    }


    @PostMapping("/apply")
    public ResponseEntity<?> applyRepair(
            @RequestBody AutoRepairApplyRequest request,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Services", required = false) String userServices) {

        LOGGER.info("REST request to apply repair: path={} mode={}", request.getFilePath(), request.getApplyMode());

        // 1. RBAC Context Building
        AuthenticatedUserContext context = buildAccessContext(userEmail, userRole, userServices);

        if (request.getFilePath() == null || request.getFilePath().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File path is required"));
        }
        if (request.getApplyMode() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Apply mode (LOCAL or GITHUB) is required"));
        }

        // Deduce service name from relative file path (e.g. logservice/src/main/... -> logservice)
        String serviceName = deduceServiceName(request.getFilePath());
        if (!context.isServiceAllowed(serviceName)) {
            LOGGER.warn("User {} forbidden from applying repair for file {} (deduced service: {})",
                    context.email(), request.getFilePath(), serviceName);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Access denied. You do not have permissions for service: " + serviceName));
        }

        try {
            AutoRepairApplyResponse response = autoRepairService.applyRepair(request);

            if ("success".equals(response.getStatus())) {
                // Audit log
                AUDIT_LOGGER.info("[AUDIT] USER={} ACTION=APPLY_REPAIR MODE={} FILE={} SERVICE={} COMMIT_SHA={} TIMESTAMP={}",
                        context.email(), request.getApplyMode(), request.getFilePath(), serviceName,
                        response.getCommitSha() != null ? response.getCommitSha() : "N/A", Instant.now());
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
        } catch (SecurityException e) {
            LOGGER.error("Security violation applying repair: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("Failed to apply repair: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to apply code repair: " + e.getMessage()));
        }
    }

    private String deduceServiceName(String filePath) {
        if (filePath == null) return "unknown";
        String normalized = filePath.replace('\\', '/');
        int firstSlash = normalized.indexOf('/');
        if (firstSlash == -1) {
            return normalized;
        }
        return normalized.substring(0, firstSlash);
    }

    private AuthenticatedUserContext buildAccessContext(String userEmail, String userRole, String userServices) {
        List<String> allowedServices = new ArrayList<>();
        if (userServices != null && !userServices.isBlank()) {
            allowedServices = Arrays.stream(userServices.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        }

        UserRole role = isAdminRole(userRole) ? UserRole.ADMIN : UserRole.DEV;
        return new AuthenticatedUserContext(
                userEmail != null && !userEmail.isBlank() ? userEmail : "unknown@local",
                role,
                allowedServices
        );
    }

    private boolean isAdminRole(String userRole) {
        if (userRole == null || userRole.isBlank()) {
            return false;
        }
        return userRole.trim().toUpperCase(Locale.ROOT).contains("ADMIN");
    }
}
