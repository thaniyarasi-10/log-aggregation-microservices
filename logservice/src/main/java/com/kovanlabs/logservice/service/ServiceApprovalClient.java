package com.kovanlabs.logservice.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ServiceApprovalClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceApprovalClient.class);

    private final RestClient restClient;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private org.springframework.cache.CacheManager cacheManager;

    public ServiceApprovalClient(@Value("${services.management.base-url:http://localhost:8082}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

<<<<<<< HEAD
    @Cacheable(value = "serviceApprovals", key = "#serviceName.trim().toLowerCase()", condition = "#serviceName != null && !#serviceName.isBlank()")
    public boolean isApproved(String serviceName) {
        return fetchApprovedFromApi(serviceName);
    }

=======
>>>>>>> 6a01b900be15a6a689e602f89925f0c54101ef47
    public boolean fetchApprovedFromApi(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return false;
        }

        LOGGER.info("Calling Service Management API to check approval for service: {}", serviceName);

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

    public boolean isApproved(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return false;
        }
        String normalized = serviceName.trim().toLowerCase();
        if (cacheManager != null) {
            org.springframework.cache.Cache cache = cacheManager.getCache("serviceApprovals");
            if (cache != null) {
                Boolean val = cache.get(normalized, Boolean.class);
                if (val != null) {
                    return val;
                }
                boolean approved = fetchApprovedFromApi(normalized);
                cache.put(normalized, approved);
                return approved;
            }
        }
        return fetchApprovedFromApi(normalized);
    }
}