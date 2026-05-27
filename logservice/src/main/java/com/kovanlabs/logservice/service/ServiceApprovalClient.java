package com.kovanlabs.logservice.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ServiceApprovalClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceApprovalClient.class);

    private final RestClient restClient;

    public ServiceApprovalClient(@Value("${services.management.base-url:http://localhost:8082}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public boolean isApproved(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return false;
        }

        try {
            Map response = restClient.get()
                    .uri("/api/services/{serviceName}/approved", serviceName.trim())
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                return false;
            }
            Object approved = response.get("approved");
            return approved instanceof Boolean value && value;
        } catch (Exception ex) {
            LOGGER.warn("Failed to validate approval for service {}: {}", serviceName, ex.getMessage());
            return false;
        }
    }
}