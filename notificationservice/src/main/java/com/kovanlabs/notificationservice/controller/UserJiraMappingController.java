package com.kovanlabs.notificationservice.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.kovanlabs.notificationservice.dto.UserJiraMappingRequest;
import com.kovanlabs.notificationservice.dto.UserJiraMappingView;
import com.kovanlabs.notificationservice.model.UserJiraMapping;
import com.kovanlabs.notificationservice.repository.UserJiraMappingRepository;
import com.kovanlabs.notificationservice.security.TenantSecurityService;

@RestController
@RequestMapping("/api/jira/user-mappings")
public class UserJiraMappingController {

    private final UserJiraMappingRepository repository;
    private final TenantSecurityService tenantSecurityService;

    public UserJiraMappingController(
            UserJiraMappingRepository repository,
            TenantSecurityService tenantSecurityService) {
        this.repository = repository;
        this.tenantSecurityService = tenantSecurityService;
    }

    @GetMapping
    public List<UserJiraMappingView> getUserMappings(
            @RequestHeader(value = "X-User-Id") String loggedInUserId,
            @RequestHeader(value = "X-Organization-Id") String orgIdStr,
            @RequestHeader(value = "X-User-Services", required = false) String userServices) {
        
        tenantSecurityService.validateMembership(loggedInUserId, orgIdStr);

        boolean isAdmin = false;
        try {
            tenantSecurityService.validateMembershipAndRole(loggedInUserId, orgIdStr, "ADMIN");
            isAdmin = true;
        } catch (Exception ignored) {}
        
        List<Object[]> results = repository.findAllUserMappingsWithServices();
        List<UserJiraMappingView> views = results.stream().map(row -> {
            UUID mappingId = null;
            if (row[3] != null) {
                if (row[3] instanceof UUID) {
                    mappingId = (UUID) row[3];
                } else {
                    mappingId = UUID.fromString(row[3].toString());
                }
            }
            return new UserJiraMappingView(
                    mappingId,
                    (String) row[0],
                    (String) row[1],
                    (String) row[2],
                    (String) row[4],
                    (String) row[5],
                    row[6] != null ? (Boolean) row[6] : false
            );
        }).toList();

        if (!isAdmin) {
            String finalUserId = loggedInUserId != null ? loggedInUserId.trim() : "";
            List<UserJiraMappingView> filtered = views.stream()
                    .filter(v -> finalUserId.equalsIgnoreCase(v.userId()))
                    .toList();
            if (!filtered.isEmpty()) {
                return filtered;
            }
            
            Optional<String> usernameOpt = repository.findUsernameByUserId(finalUserId);
            if (usernameOpt.isPresent()) {
                String username = usernameOpt.get();
                Optional<UserJiraMapping> mappingOpt = repository.findByUserId(finalUserId);
                if (mappingOpt.isPresent()) {
                    UserJiraMapping m = mappingOpt.get();
                    return List.of(new UserJiraMappingView(m.getId(), m.getUserId(), username, "", m.getJiraAccountId(), m.getJiraDisplayName(), m.isActive()));
                } else {
                    return List.of(new UserJiraMappingView(null, finalUserId, username, "", null, null, false));
                }
            }
            return List.of();
        }

        return views;
    }

    @PostMapping
    public ResponseEntity<?> createMapping(
            @RequestHeader(value = "X-User-Id") String loggedInUserId,
            @RequestHeader(value = "X-Organization-Id") String orgIdStr,
            @RequestBody UserJiraMappingRequest request) {
        
        tenantSecurityService.validateMembership(loggedInUserId, orgIdStr);

        boolean isAdmin = false;
        try {
            tenantSecurityService.validateMembershipAndRole(loggedInUserId, orgIdStr, "ADMIN");
            isAdmin = true;
        } catch (Exception ignored) {}

        if (request.userId() == null || request.userId().isBlank()) {
            return ResponseEntity.badRequest().body("userId is required");
        }
        if (request.jiraAccountId() == null || request.jiraAccountId().isBlank()) {
            return ResponseEntity.badRequest().body("jiraAccountId is required");
        }
        if (request.jiraDisplayName() == null || request.jiraDisplayName().isBlank()) {
            return ResponseEntity.badRequest().body("jiraDisplayName is required");
        }

        if (!isAdmin) {
            if (!loggedInUserId.equalsIgnoreCase(request.userId().trim())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Developers can only manage their own mapping");
            }
        }

        if (repository.findByUserId(request.userId().trim()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Mapping already exists for user: " + request.userId());
        }

        UserJiraMapping mapping = new UserJiraMapping();
        mapping.setId(UUID.randomUUID());
        mapping.setUserId(request.userId().trim());
        mapping.setJiraAccountId(request.jiraAccountId().trim());
        mapping.setJiraDisplayName(request.jiraDisplayName().trim());
        mapping.setActive(request.active() == null || request.active());
        mapping.setCreatedAt(LocalDateTime.now());
        mapping.setUpdatedAt(LocalDateTime.now());

        UserJiraMapping saved = repository.save(mapping);
        return ResponseEntity.status(HttpStatus.CREATED).body(toView(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateMapping(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "X-User-Id") String loggedInUserId,
            @RequestHeader(value = "X-Organization-Id") String orgIdStr,
            @RequestBody UserJiraMappingRequest request) {
        
        tenantSecurityService.validateMembership(loggedInUserId, orgIdStr);

        boolean isAdmin = false;
        try {
            tenantSecurityService.validateMembershipAndRole(loggedInUserId, orgIdStr, "ADMIN");
            isAdmin = true;
        } catch (Exception ignored) {}
        
        Optional<UserJiraMapping> existingOpt = repository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        UserJiraMapping mapping = existingOpt.get();

        if (!isAdmin) {
            if (!loggedInUserId.equalsIgnoreCase(mapping.getUserId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Developers can only manage their own mapping");
            }
            if (request.userId() != null && !loggedInUserId.equalsIgnoreCase(request.userId().trim())) {
                return ResponseEntity.badRequest().body("Cannot change mapping to a different user");
            }
        }

        if (request.userId() != null && !request.userId().isBlank()) {
            String newUserId = request.userId().trim();
            if (!newUserId.equals(mapping.getUserId()) && repository.findByUserId(newUserId).isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Mapping already exists for user: " + newUserId);
            }
            mapping.setUserId(newUserId);
        }
        if (request.jiraAccountId() != null) mapping.setJiraAccountId(request.jiraAccountId().trim());
        if (request.jiraDisplayName() != null) mapping.setJiraDisplayName(request.jiraDisplayName().trim());
        if (request.active() != null) mapping.setActive(request.active());
        mapping.setUpdatedAt(LocalDateTime.now());

        UserJiraMapping saved = repository.save(mapping);
        return ResponseEntity.ok((Object) toView(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMapping(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "X-User-Id") String loggedInUserId,
            @RequestHeader(value = "X-Organization-Id") String orgIdStr) {
        
        tenantSecurityService.validateMembership(loggedInUserId, orgIdStr);

        boolean isAdmin = false;
        try {
            tenantSecurityService.validateMembershipAndRole(loggedInUserId, orgIdStr, "ADMIN");
            isAdmin = true;
        } catch (Exception ignored) {}
        
        Optional<UserJiraMapping> existingOpt = repository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        UserJiraMapping mapping = existingOpt.get();

        if (!isAdmin) {
            if (!loggedInUserId.equalsIgnoreCase(mapping.getUserId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private UserJiraMappingView toView(UserJiraMapping mapping) {
        return new UserJiraMappingView(
                mapping.getId(),
                mapping.getUserId(),
                null,
                null,
                mapping.getJiraAccountId(),
                mapping.getJiraDisplayName(),
                mapping.isActive()
        );
    }
}
