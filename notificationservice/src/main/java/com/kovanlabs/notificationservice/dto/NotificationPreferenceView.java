package com.kovanlabs.notificationservice.dto;

import java.time.LocalDateTime;

public record NotificationPreferenceView(
        boolean emailEnabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}