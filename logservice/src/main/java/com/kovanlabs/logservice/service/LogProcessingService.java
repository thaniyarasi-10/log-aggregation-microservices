package com.kovanlabs.logservice.service;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.logservice.model.LogDto;
import com.kovanlabs.logservice.model.LogEvent;
import com.kovanlabs.logservice.mongo.repository.MongoLogEventRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Service
public class LogProcessingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogProcessingService.class);

    private final ElasticSearchService elasticSearchService;
    private final MongoLogEventRepository mongoLogEventRepository;
    private final ServiceApprovalClient serviceApprovalClient;
    private final RedisLogService redisLogService;
    private final WebSocketSessionTracker sessionTracker;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationServiceClient notificationServiceClient;
    private final ErrorSuggestionService errorSuggestionService;
    private final LogProcessingMetricsTracker metricsTracker;
    private final ObjectMapper objectMapper;

    private final java.util.Set<String> loggedRejectedServices = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @org.springframework.beans.factory.annotation.Value("${logs.duplicate-window-minutes:5}")
    private long duplicateWindowMinutes;

    @org.springframework.beans.factory.annotation.Value("${logs.error-rate-limit-seconds:60}")
    private long errorRateLimitSeconds;

    public LogProcessingService(ElasticSearchService elasticSearchService,
                                MongoLogEventRepository mongoLogEventRepository,
                                ServiceApprovalClient serviceApprovalClient,
                                RedisLogService redisLogService,
                                WebSocketSessionTracker sessionTracker,
                                SimpMessagingTemplate messagingTemplate,
                                NotificationServiceClient notificationServiceClient,
                                ErrorSuggestionService errorSuggestionService,
                                LogProcessingMetricsTracker metricsTracker) {
        this.elasticSearchService = elasticSearchService;
        this.mongoLogEventRepository = mongoLogEventRepository;
        this.serviceApprovalClient = serviceApprovalClient;
        this.redisLogService = redisLogService;
        this.sessionTracker = sessionTracker;
        this.messagingTemplate = messagingTemplate;
        this.notificationServiceClient = notificationServiceClient;
        this.errorSuggestionService = errorSuggestionService;
        this.metricsTracker = metricsTracker;
        this.objectMapper = new ObjectMapper();
    }

    public void process(String json) {
        if (json == null || json.isBlank()) {
            LOGGER.warn("Received empty log JSON, skipping");
            return;
        }

        String trimmed = json.trim();
        if (!trimmed.startsWith("{")) {
            LOGGER.warn("Received invalid non-object log JSON, skipping: {}", trimmed.length() > 100 ? trimmed.substring(0, 100) + "..." : trimmed);
            return;
        }

        try {
            LogEvent logEvent = objectMapper.readValue(trimmed, LogEvent.class);
            processLogEvent(logEvent);
        } catch (Exception e) {
            LOGGER.error("Failed to deserialize log JSON: {}", e.getMessage());
            LOGGER.debug("Deserialization stack trace:", e);
        }
    }

    public void processLogEvent(LogEvent logEvent) {
        long start = System.currentTimeMillis();
        try {
            if (logEvent == null) {
                LOGGER.warn("Received null LogEvent, skipping");
                return;
            }

            metricsTracker.incrementTotalLogs(1);

            String service = logEvent.getService() != null ? logEvent.getService().trim() : "unknown-service";
            if (!serviceApprovalClient.isApproved(service)) {
                if (loggedRejectedServices.add(service)) {
                    LOGGER.warn("Discarding log from unapproved service: {}. Further rejections for this service will not be logged.", service);
                }
                metricsTracker.incrementUnapprovedDiscarded(1);
                return;
            }

            String fingerprint = computeFingerprint(logEvent);
            if (redisLogService.isDuplicateAndSet(fingerprint, duplicateWindowMinutes)) {
                metricsTracker.incrementDuplicatesSkipped(1);
                return;
            }

            String level = logEvent.getLevel();
            boolean isErrorOrFatal = level != null && (
                "ERROR".equalsIgnoreCase(level.trim()) ||
                "FATAL".equalsIgnoreCase(level.trim()) ||
                "CRITICAL".equalsIgnoreCase(level.trim())
            );

            if (isErrorOrFatal) {
                String errorFingerprint = computeErrorFingerprint(logEvent);
                if (redisLogService.acquireErrorAnalysisLock(errorFingerprint, java.time.Duration.ofSeconds(errorRateLimitSeconds))) {
                    errorSuggestionService.attachSuggestion(logEvent);
                    metricsTracker.incrementLogsSentToAi(1);
                }
            }

            mongoLogEventRepository.save(logEvent);
            boolean esSaved = elasticSearchService.save(logEvent);

            if (level != null && ("ERROR".equalsIgnoreCase(level.trim()) || "CRITICAL".equalsIgnoreCase(level.trim()) || "FATAL".equalsIgnoreCase(level.trim()))) {
                redisLogService.saveLatestError(new LogDto(logEvent));
                if (isLoopProne(logEvent.getService(), logEvent.getMessage())) {
                    LOGGER.warn("Skipping alert trigger to prevent infinite loop for service: {} message: {}", logEvent.getService(), logEvent.getMessage());
                } else {
                    notificationServiceClient.sendAlert(logEvent.getService(), logEvent.getMessage(), level);
                }
            }
            
            if (esSaved) {
                broadcastLogEvent(logEvent);
            } else {
                LOGGER.warn("Skipping WebSocket broadcast because Elasticsearch save failed for service: {}", logEvent.getService());
            }
        } catch (Exception e) {
            LOGGER.error("Error processing log event: {}", e.getMessage(), e);
        } finally {
            metricsTracker.recordProcessingTime(System.currentTimeMillis() - start);
        }
    }

    public void processBatch(java.util.List<String> jsons) {
        if (jsons == null || jsons.isEmpty()) {
            return;
        }

        long start = System.currentTimeMillis();
        int totalReceived = jsons.size();
        int unapprovedCount = 0;
        int duplicateCount = 0;
        int sentToAiCount = 0;

        java.util.List<LogEvent> eventsToSave = new java.util.ArrayList<>();

        for (String json : jsons) {
            if (json == null || json.isBlank()) {
                continue;
            }

            String trimmed = json.trim();
            if (!trimmed.startsWith("{")) {
                LOGGER.warn("Skipping invalid non-object log JSON: {}", trimmed.length() > 100 ? trimmed.substring(0, 100) + "..." : trimmed);
                continue;
            }

            try {
                LogEvent logEvent = objectMapper.readValue(trimmed, LogEvent.class);
                if (logEvent == null) {
                    continue;
                }

                String service = logEvent.getService() != null ? logEvent.getService().trim() : "unknown-service";
                if (!serviceApprovalClient.isApproved(service)) {
                    if (loggedRejectedServices.add(service)) {
                        LOGGER.warn("Discarding log from unapproved service: {}. Further rejections for this service will not be logged.", service);
                    }
                    unapprovedCount++;
                    continue;
                }

                String fingerprint = computeFingerprint(logEvent);
                if (redisLogService.isDuplicateAndSet(fingerprint, duplicateWindowMinutes)) {
                    duplicateCount++;
                    continue;
                }

                String level = logEvent.getLevel();
                boolean isErrorOrFatal = level != null && (
                    "ERROR".equalsIgnoreCase(level.trim()) ||
                    "FATAL".equalsIgnoreCase(level.trim()) ||
                    "CRITICAL".equalsIgnoreCase(level.trim())
                );

                if (isErrorOrFatal) {
                    String errorFingerprint = computeErrorFingerprint(logEvent);
                    if (redisLogService.acquireErrorAnalysisLock(errorFingerprint, java.time.Duration.ofSeconds(errorRateLimitSeconds))) {
                        errorSuggestionService.attachSuggestion(logEvent);
                        sentToAiCount++;
                    }
                }

                eventsToSave.add(logEvent);

            } catch (Exception e) {
                LOGGER.error("Failed to parse or pre-process log JSON: {}", e.getMessage());
                LOGGER.debug("Parse failure stack trace:", e);
            }
        }

        metricsTracker.incrementTotalLogs(totalReceived);
        metricsTracker.incrementUnapprovedDiscarded(unapprovedCount);
        metricsTracker.incrementDuplicatesSkipped(duplicateCount);
        metricsTracker.incrementLogsSentToAi(sentToAiCount);

        if (!eventsToSave.isEmpty()) {
            try {
                mongoLogEventRepository.saveAll(eventsToSave);
            } catch (Exception e) {
                LOGGER.error("Failed to bulk save logs to MongoDB: {}", e.getMessage(), e);
            }

            boolean esSaved = elasticSearchService.saveAll(eventsToSave);

            for (LogEvent logEvent : eventsToSave) {
                try {
                    String level = logEvent.getLevel();
                    boolean isErrorOrFatal = level != null && (
                        "ERROR".equalsIgnoreCase(level.trim()) ||
                        "FATAL".equalsIgnoreCase(level.trim()) ||
                        "CRITICAL".equalsIgnoreCase(level.trim())
                    );

                    if (isErrorOrFatal) {
                        redisLogService.saveLatestError(new LogDto(logEvent));
                        if (isLoopProne(logEvent.getService(), logEvent.getMessage())) {
                            LOGGER.warn("Skipping alert trigger to prevent infinite loop for service: {} message: {}", logEvent.getService(), logEvent.getMessage());
                        } else {
                            notificationServiceClient.sendAlert(logEvent.getService(), logEvent.getMessage(), level);
                        }
                    }

                    if (esSaved) {
                        broadcastLogEvent(logEvent);
                    }
                } catch (Exception e) {
                    LOGGER.error("Error performing post-save actions for log event: {}", e.getMessage(), e);
                }
            }
        }

        metricsTracker.recordProcessingTime(System.currentTimeMillis() - start);
    }

    private String computeFingerprint(LogEvent logEvent) {
        String service = logEvent.getService() != null ? logEvent.getService().trim() : "";
        String level = logEvent.getLevel() != null ? logEvent.getLevel().trim() : "";
        String message = logEvent.getMessage() != null ? com.kovanlabs.logservice.util.ErrorNormalizer.normalize(logEvent.getMessage()) : "";
        String errorDetails = logEvent.getErrorDetails() != null ? com.kovanlabs.logservice.util.ErrorNormalizer.normalize(logEvent.getErrorDetails()) : "";
        String raw = service + "|" + level + "|" + message + "|" + errorDetails;
        return sha256(raw);
    }

    private String computeErrorFingerprint(LogEvent logEvent) {
        String service = logEvent.getService() != null ? logEvent.getService().trim() : "";
        String message = logEvent.getMessage() != null ? com.kovanlabs.logservice.util.ErrorNormalizer.normalize(logEvent.getMessage()) : "";
        String errorDetails = logEvent.getErrorDetails() != null ? com.kovanlabs.logservice.util.ErrorNormalizer.normalize(logEvent.getErrorDetails()) : "";
        String raw = service + "|" + message + "|" + errorDetails;
        return sha256(raw);
    }

    private String sha256(String raw) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(raw.hashCode());
        }
    }

    public void broadcastLogEvent(LogEvent logEvent) {
        String service = logEvent.getService();
        sessionTracker.getActiveSessions().values().stream()
                .collect(Collectors.toMap(
                        WebSocketSessionTracker.UserSessionInfo::getEmail,
                        info -> info,
                        (existing, replacement) -> existing
                ))
                .values().stream()
                .filter(info -> info.isAuthorizedForService(service))
                .forEach(info -> {
                    try {
                        messagingTemplate.convertAndSendToUser(info.getEmail(), "/queue/logs", logEvent);
                    } catch (Exception e) {
                        LOGGER.error("Failed to send realtime log to user {}: {}", info.getEmail(), e.getMessage());
                    }
                });
    }

    private boolean isLoopProne(String service, String message) {
        if (service == null || message == null) {
            return false;
        }
        String serviceLower = service.trim().toLowerCase();
        String msgLower = message.trim().toLowerCase();
        boolean isSelfService = "notification-service".equalsIgnoreCase(serviceLower) || "logservice".equalsIgnoreCase(serviceLower);
        if (isSelfService) {
            return msgLower.contains("failed to create jira story") ||
                   msgLower.contains("jira story creation failed") ||
                   msgLower.contains("failed to send alert trigger") ||
                   msgLower.contains("failed to persist alert") ||
                   msgLower.contains("graceful jira story creation failed") ||
                   msgLower.contains("jira connection/operation failure");
        }
        return false;
    }
}
