package com.kovanlabs.notificationservice.dto;

import java.time.LocalDateTime;

public record AlertItemView(
        String service,
        String message,
        int count,
        String severity,
        LocalDateTime timestamp
) {
}
