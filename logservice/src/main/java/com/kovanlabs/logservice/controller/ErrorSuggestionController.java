package com.kovanlabs.logservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kovanlabs.logservice.auth.AuthenticatedUserContext;
import com.kovanlabs.logservice.auth.UserRole;
import com.kovanlabs.logservice.model.LogEvent;
import com.kovanlabs.logservice.repository.ElasticRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for retrieving error logs, specific suggestion entities,
 * and error statistics/patterns.
 */
@RestController
@RequestMapping("/api/logs/errors")
public class ErrorSuggestionController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorSuggestionController.class);

    private final ElasticRepository elasticRepository;

    /**
     * Constructor injection.
     *
     * @param elasticRepository the elasticsearch query repository
     */
    public ErrorSuggestionController(ElasticRepository elasticRepository) {
        this.elasticRepository = elasticRepository;
    }

    /**
     * GET /api/logs/errors
     * Retrieves all error-level logs with suggestion details attached.
     */
    @GetMapping
    public ResponseEntity<List<LogEvent>> getErrors(
            @RequestParam(value = "service", required = false) String service,
            @RequestParam(value = "errorType", required = false) String errorType,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Services", required = false) String userServices) {

        LOGGER.info("GET /api/logs/errors: service={}, errorType={}, page={}, size={}", service, errorType, page, size);
        AuthenticatedUserContext context = buildAccessContext(userEmail, userRole, userServices);

        List<LogEvent> results = elasticRepository.searchErrors(service, errorType, from, to, page, size, context);
        return ResponseEntity.ok(results);
    }

    /**
     * GET /api/logs/errors/{id}
     * Retrieves a specific error log with suggestion details by its Elasticsearch ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<LogEvent> getErrorById(
            @PathVariable("id") String id,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Services", required = false) String userServices) {

        LOGGER.info("GET /api/logs/errors/{}", id);
        AuthenticatedUserContext context = buildAccessContext(userEmail, userRole, userServices);

        Optional<LogEvent> logEvent = elasticRepository.findById(id, context);
        return logEvent.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * GET /api/logs/errors/top-patterns
     * Aggregates and returns the top recurring exception patterns.
     */
    @GetMapping("/top-patterns")
    public ResponseEntity<List<Map<String, Object>>> getTopPatterns(
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Services", required = false) String userServices) {

        LOGGER.info("GET /api/logs/errors/top-patterns");
        AuthenticatedUserContext context = buildAccessContext(userEmail, userRole, userServices);

        Map<String, Object> stats = elasticRepository.getErrorStats(context);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topRecurring = (List<Map<String, Object>>) stats.getOrDefault("topRecurringErrors", new ArrayList<>());

        // Format to map pattern to errorType for compatibility
        List<Map<String, Object>> formattedPatterns = topRecurring.stream()
                .map(item -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("pattern", item.get("errorType"));
                    map.put("count", item.get("count"));
                    return map;
                })
                .toList();

        return ResponseEntity.ok(formattedPatterns);
    }

    /**
     * GET /api/logs/errors/by-error-type/{errorType}
     * Retrieves all error-level logs belonging to a specific error pattern type.
     */
    @GetMapping("/by-error-type/{errorType}")
    public ResponseEntity<List<LogEvent>> getErrorsByErrorType(
            @PathVariable("errorType") String errorType,
            @RequestParam(value = "service", required = false) String service,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Services", required = false) String userServices) {

        LOGGER.info("GET /api/logs/errors/by-error-type/{}: service={}", errorType, service);
        AuthenticatedUserContext context = buildAccessContext(userEmail, userRole, userServices);

        List<LogEvent> results = elasticRepository.searchErrors(service, errorType, from, to, page, size, context);
        return ResponseEntity.ok(results);
    }

    /**
     * GET /api/logs/errors/stats
     * Exposes consolidated error statistics including count per type,
     * top recurring, and most affected services.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Services", required = false) String userServices) {

        LOGGER.info("GET /api/logs/errors/stats");
        AuthenticatedUserContext context = buildAccessContext(userEmail, userRole, userServices);

        Map<String, Object> stats = elasticRepository.getErrorStats(context);
        return ResponseEntity.ok(stats);
    }

    /**
     * GET /api/logs/errors/ai-stats
     * Exposes AI analysis statistics including most common AI-generated errors,
     * most reused knowledge-base entries, saved Gemini API calls, and top root causes.
     */
    @GetMapping("/ai-stats")
    public ResponseEntity<Map<String, Object>> getAiStats(
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Services", required = false) String userServices) {

        LOGGER.info("GET /api/logs/errors/ai-stats");
        AuthenticatedUserContext context = buildAccessContext(userEmail, userRole, userServices);

        Map<String, Object> aiStats = elasticRepository.getAiStats(context);
        return ResponseEntity.ok(aiStats);
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
