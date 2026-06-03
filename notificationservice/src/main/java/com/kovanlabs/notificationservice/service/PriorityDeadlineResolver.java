package com.kovanlabs.notificationservice.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PriorityDeadlineResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(PriorityDeadlineResolver.class);

    public LocalDateTime resolveDueDate(String priority) {
        return resolveDueDate(priority, LocalDateTime.now());
    }

    public LocalDateTime resolveDueDate(String priority, LocalDateTime baseTime) {
        if (priority == null || priority.isBlank()) {
            LOGGER.warn("Null or blank priority received. Defaulting to MEDIUM (3 days).");
            return baseTime.plusDays(3);
        }

        String cleanedPriority = priority.trim().toUpperCase();

        switch (cleanedPriority) {
            case "CRITICAL":
                return baseTime.plusHours(4);
            case "HIGH":
                return baseTime.plusDays(1);
            case "MEDIUM":
                return baseTime.plusDays(3);
            case "LOW":
                return baseTime.plusDays(7);
            default:
                LOGGER.warn("Unknown priority '{}' received. Defaulting to MEDIUM (3 days).", priority);
                return baseTime.plusDays(3);
        }
    }
}
