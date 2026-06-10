package com.kovanlabs.logservice.service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ServiceApprovalClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceApprovalClient.class);

    private final RestClient restClient;
    private final ConcurrentMap<String, ApprovalCacheEntry> approvalCache = new ConcurrentHashMap<>();
    private final long cacheTtlMs;
    private final long failureCacheTtlMs;
    private final boolean failOpenOnValidationError;

    public ServiceApprovalClient(
            @Value("${services.management.base-url:http://localhost:8082}") String baseUrl,
            @Value("${services.management.approval-cache-ttl-ms:300000}") long cacheTtlMs,
            @Value("${services.management.approval-failure-cache-ttl-ms:30000}") long failureCacheTtlMs,
            @Value("${services.management.connect-timeout-ms:500}") long connectTimeoutMs,
            @Value("${services.management.read-timeout-ms:1000}") long readTimeoutMs,
            @Value("${services.management.fail-open-on-validation-error:true}") boolean failOpenOnValidationError) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.cacheTtlMs = cacheTtlMs;
        this.failureCacheTtlMs = failureCacheTtlMs;
        this.failOpenOnValidationError = failOpenOnValidationError;
    }

    public boolean isApproved(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return false;
        }

        String normalizedServiceName = serviceName.trim();
        long now = System.currentTimeMillis();
        ApprovalCacheEntry cached = approvalCache.get(normalizedServiceName);
        if (cached != null && cached.expiresAtMs() > now) {
            return cached.approved();
        }

        try {
            Map response = restClient.get()
                    .uri("/api/services/{serviceName}/approved", normalizedServiceName)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                cacheApproval(normalizedServiceName, false, failureCacheTtlMs);
                return false;
            }
            Object approved = response.get("approved");
            boolean isApproved = approved instanceof Boolean value && value;
            cacheApproval(normalizedServiceName, isApproved, cacheTtlMs);
            return isApproved;
        } catch (Exception ex) {
            boolean fallbackApproval = failOpenOnValidationError;
            LOGGER.warn("Failed to validate approval for service {}: {}. Falling back to approved={}",
                    serviceName, ex.getMessage(), fallbackApproval);
            cacheApproval(normalizedServiceName, fallbackApproval, failureCacheTtlMs);
            return fallbackApproval;
        }
    }

    private void cacheApproval(String serviceName, boolean approved, long ttlMs) {
        approvalCache.put(serviceName, new ApprovalCacheEntry(approved, System.currentTimeMillis() + ttlMs));
    }

    private record ApprovalCacheEntry(boolean approved, long expiresAtMs) {
    }
}
