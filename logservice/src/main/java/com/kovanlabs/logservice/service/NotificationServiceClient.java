package com.kovanlabs.logservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.kovanlabs.logservice.model.AlertNotificationRequest;

@Component
public class NotificationServiceClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationServiceClient.class);

    private final RestClient restClient;

    public NotificationServiceClient(@Value("${services.notification.base-url:http://localhost:8083}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public void sendAlert(String service, String message, String level) {
        sendAlert(service, message, level, null);
    }

    public void sendAlert(String service, String message, String level, String organizationId) {
        if (service == null || service.isBlank() || message == null || message.isBlank()) {
            return;
        }

        try {
            LOGGER.info("Sending alert trigger to notification-service for service: {}, message: {}", service, message);
            AlertNotificationRequest request = new AlertNotificationRequest(
                    null,
                    null,
                    service,
                    level != null ? level : "ERROR",
                    message,
                    1,
                    organizationId
            );
            restClient.post()
                    .uri("/api/notifications/alerts")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            LOGGER.info("Alert trigger successfully dispatched to notification-service");
        } catch (Exception ex) {
            LOGGER.error("Failed to send alert trigger to notification-service: {}", ex.getMessage(), ex);
        }
    }
}
