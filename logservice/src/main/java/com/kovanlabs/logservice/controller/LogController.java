package com.kovanlabs.logservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kovanlabs.logservice.auth.AuthenticatedUserContext;
import com.kovanlabs.logservice.auth.UserRole;
import com.kovanlabs.logservice.model.LogDto;
import com.kovanlabs.logservice.model.LogEvent;
import com.kovanlabs.logservice.model.AlertItemView;
import com.kovanlabs.logservice.model.ServiceLogMetrics;
import com.kovanlabs.logservice.repository.ElasticRepository;
import com.kovanlabs.logservice.service.LogProcessingService;
import com.kovanlabs.logservice.service.RedisLogService;
import com.kovanlabs.logservice.service.SourceCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * REST API for log ingestion and retrieval
 */
@RestController
@RequestMapping({"/logs", "/api/logs"})
public class LogController {

    public static final Logger LOGGER = LoggerFactory.getLogger(LogController.class);

    private final LogProcessingService processingService;
    private final RedisLogService redisLogService;
    private final ElasticRepository elasticRepository;
    private final SourceCodeService sourceCodeService;

    public LogController(LogProcessingService processingService, RedisLogService redisLogService, ElasticRepository elasticRepository, SourceCodeService sourceCodeService) {
        this.processingService = processingService;
        this.redisLogService = redisLogService;
        this.elasticRepository = elasticRepository;
        this.sourceCodeService = sourceCodeService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> ingest(@RequestBody LogEvent log) {
        processingService.processLogEvent(log);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Log received"));
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<String, String>> ingestBatch(@RequestBody List<LogEvent> logs) {
        if (logs != null) {
            for (LogEvent log : logs) {
                processingService.processLogEvent(log);
            }
        }
        int size = logs == null ? 0 : logs.size();
        return ResponseEntity.ok(Map.of("status", "success", "message", "Received " + size + " logs"));
    }

    // Search logs via Elasticsearch with RBAC context
    @GetMapping
    public ResponseEntity<List<LogEvent>> search(
            @RequestParam(value = "services", required = false) String services,
            @RequestParam(value = "service", required = false) String service,
            @RequestParam(value = "levels", required = false) String levels,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "traceId", required = false) String traceId,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "environment", required = false) String environment,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Services", required = false) String userServices) {

        String finalServices = (services != null && !services.isBlank()) ? services : service;
        String finalLevels = (levels != null && !levels.isBlank()) ? levels : level;

        List<String> resolvedServices = new ArrayList<>();
        if (finalServices != null && !finalServices.isBlank()) {
            resolvedServices = Arrays.stream(finalServices.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        }

        List<String> resolvedLevels = new ArrayList<>();
        if (finalLevels != null && !finalLevels.isBlank()) {
            resolvedLevels = Arrays.stream(finalLevels.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        }

        List<String> allowedServices = new ArrayList<>();
        if (userServices != null && !userServices.isBlank()) {
            allowedServices = Arrays.stream(userServices.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        }

        AuthenticatedUserContext context = new AuthenticatedUserContext(
                userEmail != null ? userEmail : "unknown@local",
                "ADMIN".equalsIgnoreCase(userRole) ? UserRole.ADMIN : UserRole.DEV,
                allowedServices
        );

        List<LogEvent> results = elasticRepository.searchMulti(
                resolvedServices,
                environment,
                resolvedLevels,
                traceId,
                message,
                from,
                to,
                page,
                size,
                context
        );
        return ResponseEntity.ok(results);
    }

    // Get metrics via Elasticsearch with RBAC context
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> metrics(
            @RequestParam(value = "services", required = false) String services,
            @RequestParam(value = "service", required = false) String service,
            @RequestParam(value = "levels", required = false) String levels,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "environment", required = false) String environment,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "timePreset", required = false) String timePreset,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Services", required = false) String userServices) {

        String finalServices = (services != null && !services.isBlank()) ? services : service;
        String finalLevels = (levels != null && !levels.isBlank()) ? levels : level;

        List<String> allowedServices = new ArrayList<>();
        if (userServices != null && !userServices.isBlank()) {
            allowedServices = Arrays.stream(userServices.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        }

        AuthenticatedUserContext context = new AuthenticatedUserContext(
                userEmail != null ? userEmail : "unknown@local",
                "ADMIN".equalsIgnoreCase(userRole) ? UserRole.ADMIN : UserRole.DEV,
                allowedServices
        );

        Map<String, Object> metrics = elasticRepository.getMetrics(
                finalServices,
                null,
                null,
                null,
                from,
                to,
                timePreset,
                context
        );
        return ResponseEntity.ok(metrics);
    }

    // List distinct services via Elasticsearch with RBAC context
    @GetMapping("/services")
    public ResponseEntity<List<String>> services(
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Services", required = false) String userServices) {

        List<String> allowedServices = new ArrayList<>();
        if (userServices != null && !userServices.isBlank()) {
            allowedServices = Arrays.stream(userServices.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        }

        AuthenticatedUserContext context = new AuthenticatedUserContext(
                userEmail != null ? userEmail : "unknown@local",
                "ADMIN".equalsIgnoreCase(userRole) ? UserRole.ADMIN : UserRole.DEV,
                allowedServices
        );

        List<String> services = elasticRepository.getDistinctServices(null, null, 100, context);
        return ResponseEntity.ok(services);
    }

    @GetMapping("/latest-errors")
    public ResponseEntity<List<LogDto>> getLatestErrors() {
        List<LogDto> cachedErrors = null;
        try {
            cachedErrors = redisLogService.getLatestErrors();
        } catch (Exception e) {
            LOGGER.warn("Redis is unavailable or failed to retrieve latest errors: {}. Falling back to Elasticsearch.", e.getMessage());
        }

        if (cachedErrors != null && !cachedErrors.isEmpty()) {
            boolean isValid = cachedErrors.stream().allMatch(log -> log != null && log.getLevel() != null && "ERROR".equalsIgnoreCase(log.getLevel().trim()));
            if (isValid) {
                return ResponseEntity.ok(cachedErrors);
            } else {
                LOGGER.warn("Redis returned invalid latest errors data. Falling back to Elasticsearch.");
            }
        } else {
            LOGGER.info("Redis latest errors cache is empty or expired. Falling back to Elasticsearch.");
        }

        AuthenticatedUserContext context = new AuthenticatedUserContext(
                "admin@local",
                UserRole.ADMIN,
                List.of()
        );
        List<LogEvent> errors = elasticRepository.searchMulti(
                null,
                null,
                List.of("error"),
                null,
                null,
                null,
                null,
                0,
                20,
                context
        );
        List<LogDto> errorDtos = errors.stream()
                .map(LogDto::new)
                .toList();
        return ResponseEntity.ok(errorDtos);
    }

    @GetMapping("/alerts")
    public ResponseEntity<Map<String, List<AlertItemView>>> getAlerts(
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Services", required = false) String userServices) {

        List<String> allowedServices = new ArrayList<>();
        if (userServices != null && !userServices.isBlank()) {
            allowedServices = Arrays.stream(userServices.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        }

        AuthenticatedUserContext context = new AuthenticatedUserContext(
                userEmail != null ? userEmail : "unknown@local",
                "ADMIN".equalsIgnoreCase(userRole) ? UserRole.ADMIN : UserRole.DEV,
                allowedServices
        );

        Map<String, List<AlertItemView>> alerts = elasticRepository.calculateAlerts(context);
        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/service-health")
    public ResponseEntity<List<ServiceLogMetrics>> getServiceHealth(
            @RequestParam(value = "windowMinutes", defaultValue = "15") int windowMinutes) {
        LOGGER.info("REST request to get service health metrics for last {} minutes", windowMinutes);
        List<ServiceLogMetrics> metrics = elasticRepository.getServiceHealthMetrics(windowMinutes);
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/source-code")
    public ResponseEntity<?> getSourceCode(
            @RequestParam("service") String service,
            @RequestParam(value = "class", required = false) String className,
            @RequestParam("file") String file,
            @RequestParam(value = "line", required = false) Integer line) {
        try {
            Map<String, Object> source = sourceCodeService.getSourceCode(service, className, file, line);
            if (source == null) {
                return ResponseEntity.status(404).body(Map.of("message", "Source file not found"));
            }
            return ResponseEntity.ok(source);
        } catch (SecurityException e) {
            LOGGER.error("Security violation accessing source code: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("Failed to retrieve source code: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("message", "Failed to retrieve source code: " + e.getMessage()));
        }
    }
}