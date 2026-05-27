package com.kovanlabs.logservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kovanlabs.logservice.model.LogEvent;
import com.kovanlabs.logservice.service.LogProcessingService;

import java.util.List;
import java.util.Map;

/**
 * REST API for log ingestion and retrieval
 */
@RestController
@RequestMapping({"/logs", "/api/logs"})
public class LogController {

    private final LogProcessingService processingService;

    public LogController(LogProcessingService processingService) {
        this.processingService = processingService;
    }

    /**
     * Ingest a single log event
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> ingest(@RequestBody LogEvent log) {
        processingService.processLogEvent(log);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Log received"));
    }

    /**
     * Ingest batch of log events
     */
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

    /**
     * Search logs
     */
    @GetMapping
    public ResponseEntity<List<LogEvent>> search(
            @RequestParam(value = "service", required = false) String service,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(processingService.searchLogs(service, level, message, page, size));
    }

    /**
     * Get metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> metrics(
            @RequestParam(value = "service", required = false) String service,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        return ResponseEntity.ok(processingService.getMetrics(service));
    }

    /**
     * Get available services
     */
    @GetMapping("/services")
    public ResponseEntity<List<String>> services() {
        return ResponseEntity.ok(processingService.listServices());
    }
}