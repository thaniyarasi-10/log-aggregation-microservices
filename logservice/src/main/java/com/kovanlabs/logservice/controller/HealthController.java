package com.kovanlabs.logservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health check endpoints
 */
@RestController
public class HealthController {

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "logservice",
                "version", "1.0.0"
        ));
    }

    /**
     * Ready check endpoint
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> ready() {
        return ResponseEntity.ok(Map.of(
                "status", "READY",
                "service", "logservice"
        ));
    }
}
