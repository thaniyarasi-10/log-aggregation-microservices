package com.kovanlabs.notificationservice.dto;

public record AlertRequest(
    String alertId,
    String alertName,
    String serviceName,
    String priority,
    String triggeredAt,
    String alertRule,
    String observedValue,
    String threshold,
    String timeWindow,
    Integer errorCount,
    String topErrors,
    String alertUrl
) {}
