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
    private final ObjectMapper objectMapper;

    public LogProcessingService(ElasticSearchService elasticSearchService,
                                MongoLogEventRepository mongoLogEventRepository,
                                ServiceApprovalClient serviceApprovalClient,
                                RedisLogService redisLogService,
                                WebSocketSessionTracker sessionTracker,
                                SimpMessagingTemplate messagingTemplate,
                                NotificationServiceClient notificationServiceClient,
                                ErrorSuggestionService errorSuggestionService) {
        this.elasticSearchService = elasticSearchService;
        this.mongoLogEventRepository = mongoLogEventRepository;
        this.serviceApprovalClient = serviceApprovalClient;
        this.redisLogService = redisLogService;
        this.sessionTracker = sessionTracker;
        this.messagingTemplate = messagingTemplate;
        this.notificationServiceClient = notificationServiceClient;
        this.errorSuggestionService = errorSuggestionService;
        this.objectMapper = new ObjectMapper();
    }

    public void process(String json) {
        if (json == null || json.isBlank()) {
            LOGGER.warn("Received empty log JSON, skipping");
            return;
        }

        try {
            LogEvent logEvent = objectMapper.readValue(json, LogEvent.class);
            processLogEvent(logEvent);
        } catch (Exception e) {
            LOGGER.error("Failed to deserialize log JSON: {}", e.getMessage(), e);
        }
    }

    public void processLogEvent(LogEvent logEvent) {
        long start = System.currentTimeMillis();
        try {
            if (logEvent == null) {
                LOGGER.warn("Received null LogEvent, skipping");
                return;
            }

            if (!serviceApprovalClient.isApproved(logEvent.getService())) {
                LOGGER.info("Discarding log from unapproved service: {}", logEvent.getService());
                return;
            }

            String level = logEvent.getLevel();
            if (level != null && "ERROR".equalsIgnoreCase(level.trim())) {
                errorSuggestionService.attachSuggestion(logEvent);
            }

            mongoLogEventRepository.save(logEvent);
            boolean esSaved = elasticSearchService.save(logEvent);

            if (level != null && ("ERROR".equalsIgnoreCase(level.trim()) || "CRITICAL".equalsIgnoreCase(level.trim()) || "FATAL".equalsIgnoreCase(level.trim()))) {
                redisLogService.saveLatestError(new LogDto(logEvent));
                notificationServiceClient.sendAlert(logEvent.getService(), logEvent.getMessage(), level);
            }
            
            if (esSaved) {
                broadcastLogEvent(logEvent);
            } else {
                LOGGER.warn("Skipping WebSocket broadcast because Elasticsearch save failed for service: {}", logEvent.getService());
            }
        } catch (Exception e) {
            LOGGER.error("Error processing log event: {}", e.getMessage(), e);
        } finally {
//            LOGGER.info("Processed event in {} ms", System.currentTimeMillis() - start);
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
}
