package com.kovanlabs.logservice.service;

<<<<<<< HEAD
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
=======
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
>>>>>>> 6a01b900be15a6a689e602f89925f0c54101ef47
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.kovanlabs.logservice.model.AlertNotificationRequest;

@Component
public class NotificationServiceClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationServiceClient.class);

    private final RestClient restClient;

<<<<<<< HEAD
    public NotificationServiceClient(@Value("${services.notification.base-url:http://localhost:8083}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public void sendAlert(String service, String message, String level) {
        sendAlert(service, message, level, null);
    }

    public void sendAlert(String service, String message, String level, String organizationId) {
=======
    public NotificationServiceClient(
            @Value("${services.notification.base-url:http://localhost:8083}") String baseUrl,
            @Value("${services.notification.connect-timeout-ms:1000}") long connectTimeoutMs,
            @Value("${services.notification.read-timeout-ms:10000}") long readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public void sendAlert(String service, String message, String level) {
>>>>>>> 6a01b900be15a6a689e602f89925f0c54101ef47
        if (service == null || service.isBlank() || message == null || message.isBlank()) {
            return;
        }

<<<<<<< HEAD
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
=======
        AlertNotificationRequest request = new AlertNotificationRequest(
                null,
                null,
                service,
                level != null ? level : "ERROR",
                message,
                1
        );

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                restClient.post()
                        .uri("/api/notifications/alerts")
                        .body(request)
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception ex) {
                LOGGER.warn("Failed to send alert trigger to notification-service: {}", ex.getMessage(), ex);
            }
        });
>>>>>>> 6a01b900be15a6a689e602f89925f0c54101ef47
    }
}
