package com.kovanlabs.notificationservice.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class UserServiceClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceClient.class);

    private final RestClient restClient;

    public UserServiceClient(@Value("${services.management.base-url:http://localhost:8082}") String baseUrl) {
        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(java.time.Duration.ofMillis(1000));
        requestFactory.setReadTimeout(java.time.Duration.ofMillis(2000));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public String getUserIdByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }

        try {
            Map<?, ?> response = restClient.get()
                    .uri("/api/users/by-email?email={email}", email.trim())
                    .retrieve()
                    .body(Map.class);
            if (response != null && response.containsKey("id")) {
                return (String) response.get("id");
            }
        } catch (Exception ex) {
            LOGGER.warn("Failed to fetch user ID by email {}: {}", email, ex.getMessage());
        }
        return null;
    }
}
