package com.kovanlabs.notificationservice.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.kovanlabs.notificationservice.dto.JiraConfigurationRequest;
import com.kovanlabs.notificationservice.dto.JiraConfigurationView;
import com.kovanlabs.notificationservice.dto.JiraUserDto;
import com.kovanlabs.notificationservice.model.JiraConfiguration;
import com.kovanlabs.notificationservice.repository.JiraConfigurationRepository;
import com.kovanlabs.notificationservice.service.JiraClient;
import com.kovanlabs.notificationservice.service.JiraFailureCache;

@RestController
@RequestMapping("/api/jira")
public class JiraConfigurationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(JiraConfigurationController.class);

    private final JiraConfigurationRepository repository;
    private final JiraClient jiraClient;
    private final JiraFailureCache failureCache;

    public JiraConfigurationController(JiraConfigurationRepository repository, JiraClient jiraClient, JiraFailureCache failureCache) {
        this.repository = repository;
        this.jiraClient = jiraClient;
        this.failureCache = failureCache;
    }

    @GetMapping("/configuration")
    public ResponseEntity<JiraConfigurationView> getConfiguration() {
        return repository.findFirstByActiveTrue()
                .or(() -> repository.findAll().stream().findFirst())
                .map(this::toView)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/configuration")
    public ResponseEntity<?> createConfiguration(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestBody JiraConfigurationRequest request) {
        if (userRole != null && !"ADMIN".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only administrators can manage Jira connection configuration");
        }

        if (request.jiraBaseUrl() == null || request.jiraBaseUrl().isBlank()) {
            return ResponseEntity.badRequest().body("jiraBaseUrl is required");
        }
        if (request.jiraEmail() == null || request.jiraEmail().isBlank()) {
            return ResponseEntity.badRequest().body("jiraEmail is required");
        }
        if (request.jiraApiToken() == null || request.jiraApiToken().isBlank()) {
            return ResponseEntity.badRequest().body("jiraApiToken is required");
        }
        if (request.jiraProjectKey() == null || request.jiraProjectKey().isBlank()) {
            return ResponseEntity.badRequest().body("jiraProjectKey is required");
        }

        JiraConfiguration config = new JiraConfiguration();
        config.setId(UUID.randomUUID());
        config.setJiraBaseUrl(request.jiraBaseUrl().trim());
        config.setJiraEmail(request.jiraEmail().trim());
        config.setJiraApiToken(request.jiraApiToken().trim());
        config.setJiraProjectKey(request.jiraProjectKey().trim().toUpperCase());
        config.setActive(request.active() == null || request.active());
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());

        if (config.isActive()) {
            deactivateOtherConfigurations(null);
        }

        JiraConfiguration saved = repository.save(config);
        failureCache.clear();
        return ResponseEntity.status(HttpStatus.CREATED).body(toView(saved));
    }

    @PutMapping("/configuration")
    public ResponseEntity<?> updateConfiguration(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestBody JiraConfigurationRequest request) {
        if (userRole != null && !"ADMIN".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only administrators can manage Jira connection configuration");
        }

        JiraConfiguration config = repository.findFirstByActiveTrue()
                .or(() -> repository.findAll().stream().findFirst())
                .orElseGet(() -> {
                    JiraConfiguration newConfig = new JiraConfiguration();
                    newConfig.setId(UUID.randomUUID());
                    newConfig.setCreatedAt(LocalDateTime.now());
                    return newConfig;
                });

        if (request.jiraBaseUrl() != null) config.setJiraBaseUrl(request.jiraBaseUrl().trim());
        if (request.jiraEmail() != null) config.setJiraEmail(request.jiraEmail().trim());
        if (request.jiraApiToken() != null && !request.jiraApiToken().isBlank()) {
            config.setJiraApiToken(request.jiraApiToken().trim());
        }
        if (request.jiraProjectKey() != null) config.setJiraProjectKey(request.jiraProjectKey().trim().toUpperCase());
        if (request.active() != null) config.setActive(request.active());
        config.setUpdatedAt(LocalDateTime.now());

        if (config.isActive()) {
            deactivateOtherConfigurations(config.getId());
        }

        JiraConfiguration saved = repository.save(config);
        failureCache.clear();
        return ResponseEntity.ok(toView(saved));
    }

    @PostMapping("/test-connection")
    public ResponseEntity<?> testConnection(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestBody JiraConfigurationRequest request) {
        if (userRole != null && !"ADMIN".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only administrators can manage Jira connection configuration");
        }

        if (request.jiraBaseUrl() == null || request.jiraBaseUrl().isBlank()) {
            return ResponseEntity.badRequest().body("Jira Base URL is required");
        }
        if (request.jiraEmail() == null || request.jiraEmail().isBlank()) {
            return ResponseEntity.badRequest().body("Jira Email is required");
        }
        if (request.jiraApiToken() == null || request.jiraApiToken().isBlank()) {
            return ResponseEntity.badRequest().body("Jira API Token is required");
        }
        if (request.jiraProjectKey() == null || request.jiraProjectKey().isBlank()) {
            return ResponseEntity.badRequest().body("Jira Project Key is required");
        }

        try {
            jiraClient.testConnection(
                    request.jiraBaseUrl(),
                    request.jiraApiToken(),
                    request.jiraEmail(),
                    request.jiraProjectKey()
            );
            return ResponseEntity.ok(Map.of("message", "Connection test succeeded. Configuration is valid."));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Unexpected error testing connection: " + ex.getMessage()));
        }
    }

    @GetMapping("/users")
    public ResponseEntity<?> getJiraUsers(@RequestParam(value = "query", required = false) String query) {
        JiraConfiguration config = repository.findFirstByActiveTrue().orElse(null);
        if (config == null) {
            return ResponseEntity.ok(List.of());
        }

        try {
            List<JiraUserDto> users = jiraClient.searchAssignableUsers(
                    config.getJiraBaseUrl(),
                    config.getJiraApiToken(),
                    config.getJiraEmail(),
                    config.getJiraProjectKey(),
                    query
            );
            return ResponseEntity.ok(users);
        } catch (Exception ex) {
            LOGGER.warn("Failed to lookup Jira users (Jira connection or project key might be invalid): {}", ex.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    private void deactivateOtherConfigurations(UUID activeConfigId) {
        List<JiraConfiguration> configs = repository.findAll();
        for (JiraConfiguration config : configs) {
            if (config.isActive() && !config.getId().equals(activeConfigId)) {
                config.setActive(false);
                repository.save(config);
            }
        }
    }

    private JiraConfigurationView toView(JiraConfiguration config) {
        // Mask the api token for security
        String maskedToken = config.getJiraApiToken() != null && config.getJiraApiToken().length() > 4
                ? "********" + config.getJiraApiToken().substring(config.getJiraApiToken().length() - 4)
                : "********";

        return new JiraConfigurationView(
                config.getId(),
                config.getJiraBaseUrl(),
                config.getJiraEmail(),
                maskedToken,
                config.getJiraProjectKey(),
                config.isActive(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }
}
