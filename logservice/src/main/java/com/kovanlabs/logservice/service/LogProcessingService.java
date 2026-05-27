package com.kovanlabs.logservice.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.logservice.model.LogEvent;
import com.kovanlabs.logservice.mongo.repository.MongoLogEventRepository;

@Service
public class LogProcessingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogProcessingService.class);

    private final ElasticSearchService elasticSearchService;
    private final MongoLogEventRepository mongoLogEventRepository;
    private final ServiceApprovalClient serviceApprovalClient;
    private final ObjectMapper objectMapper;

    public LogProcessingService(ElasticSearchService elasticSearchService,
                                MongoLogEventRepository mongoLogEventRepository,
                                ServiceApprovalClient serviceApprovalClient) {
        this.elasticSearchService = elasticSearchService;
        this.mongoLogEventRepository = mongoLogEventRepository;
        this.serviceApprovalClient = serviceApprovalClient;
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
        if (logEvent == null) {
            LOGGER.warn("Received null LogEvent, skipping");
            return;
        }

        if (!serviceApprovalClient.isApproved(logEvent.getService())) {
            LOGGER.info("Discarding log from unapproved service: {}", logEvent.getService());
            return;
        }

        try {
            mongoLogEventRepository.save(logEvent);
            elasticSearchService.save(logEvent);
//            LOGGER.debug("Log processed successfully - service: {}, level: {}",
//                    logEvent.getService(), logEvent.getLevel());
        } catch (Exception e) {
            LOGGER.error("Error processing log event: {}", e.getMessage(), e);
        }
    }

    public List<LogEvent> searchLogs(String service, String level, String message, int page, int size) {
        return mongoLogEventRepository.findAll().stream()
                .filter(event -> service == null || service.isBlank() || containsIgnoreCase(event.getService(), service))
                .filter(event -> level == null || level.isBlank() || containsIgnoreCase(event.getLevel(), level))
                .filter(event -> message == null || message.isBlank() || containsIgnoreCase(event.getMessage(), message))
                .skip((long) Math.max(0, page) * Math.max(1, size))
                .limit(Math.max(1, size))
                .toList();
    }

    public Map<String, Object> getMetrics(String serviceFilter) {
        List<LogEvent> logs = mongoLogEventRepository.findAll().stream()
                .filter(event -> serviceFilter == null || serviceFilter.isBlank() || containsIgnoreCase(event.getService(), serviceFilter))
                .toList();

        long total = logs.size();
        long errorCount = logs.stream().filter(this::isAnomaly).count();
        double errorRate = total == 0 ? 0.0 : (double) errorCount / total;
        double avgResponseTime = logs.stream()
                .map(LogEvent::getResponseTime)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalLogs", total);
        metrics.put("errorCount", errorCount);
        metrics.put("errorRate", errorRate);
        metrics.put("avgResponseTime", avgResponseTime);
        return metrics;
    }

    public List<String> listServices() {
        return mongoLogEventRepository.findAll().stream()
                .map(LogEvent::getService)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private boolean containsIgnoreCase(String source, String probe) {
        if (source == null || probe == null) {
            return false;
        }
        return source.toLowerCase().contains(probe.toLowerCase());
    }

    private boolean isAnomaly(LogEvent event) {
        if (event == null || event.getLevel() == null) {
            return false;
        }
        String lvl = event.getLevel().toUpperCase();
        return "ERROR".equals(lvl) || "FATAL".equals(lvl) || "CRITICAL".equals(lvl);
    }
}
