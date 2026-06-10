package com.kovanlabs.servicemanagementservice.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.kovanlabs.servicemanagementservice.dto.ServiceHealthView;
import com.kovanlabs.servicemanagementservice.model.AppService;
import com.kovanlabs.servicemanagementservice.repository.AppServiceRepository;

@Service
public class ServiceHealthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceHealthService.class);

    private final AppServiceRepository appServiceRepository;
    private final RestClient restClient;

    @Value("${services.health.window-minutes:15}")
    private int windowMinutes;

    public ServiceHealthService(AppServiceRepository appServiceRepository,
                                @Value("${services.health.log-service-url:http://localhost:8081}") String logServiceUrl) {
        this.appServiceRepository = appServiceRepository;
        this.restClient = RestClient.builder().baseUrl(logServiceUrl).build();
    }

    private record ServiceLogMetrics(
            String service,
            long errorCount,
            long warnCount,
            Instant lastSeen
    ) {}

    public List<ServiceHealthView> getServicesHealth() {
        LOGGER.info("Calculating service health based on registered services and log metrics");

        // 1. Fetch registered active services from database
        List<AppService> registeredServices = appServiceRepository.findByActiveTrueOrderByNameAsc();
        Set<String> activeServiceNames = registeredServices.stream()
                .map(AppService::getName)
                .filter(Objects::nonNull)
                .map(name -> name.trim().toLowerCase())
                .collect(Collectors.toSet());

        // 2. Fetch log metrics from logservice
        List<ServiceLogMetrics> logMetrics = new ArrayList<>();
        try {
            LOGGER.info("Calling Log Service to fetch service health aggregation for window: {} minutes", windowMinutes);
            List<ServiceLogMetrics> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/logs/service-health")
                            .queryParam("windowMinutes", windowMinutes)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ServiceLogMetrics>>() {});

            if (response != null) {
                logMetrics = response;
            }
        } catch (Exception ex) {
            LOGGER.error("Failed to retrieve service log metrics from Log Service: {}", ex.getMessage(), ex);
            // We proceed with empty log metrics, which will cause all active registered services to be NO_DATA
        }

        // Create a map of lowercase service name -> metrics
        Map<String, ServiceLogMetrics> logMetricsMap = logMetrics.stream()
                .filter(m -> m.service() != null)
                .collect(Collectors.toMap(
                        m -> m.service().trim().toLowerCase(),
                        m -> m,
                        (existing, replacement) -> existing // pick first if duplicates exist
                ));

        List<ServiceHealthView> healthViews = new ArrayList<>();

        // 3. Process registered active services
        for (AppService appService : registeredServices) {
            String originalName = appService.getName();
            String keyName = originalName.trim().toLowerCase();

            if (logMetricsMap.containsKey(keyName)) {
                ServiceLogMetrics metrics = logMetricsMap.get(keyName);
                String status;
                if (metrics.errorCount() > 0) {
                    status = "ERROR";
                } else if (metrics.warnCount() > 0) {
                    status = "WARNING";
                } else {
                    status = "OK";
                }
                healthViews.add(new ServiceHealthView(originalName, status, metrics.lastSeen()));
            } else {
                // If a registered service is not returned in the aggregation result, mark it as NO_DATA
                healthViews.add(new ServiceHealthView(originalName, "NO_DATA", null));
            }
        }

        // 4. Also include any unregistered services found in Elasticsearch log metrics!
        // This is useful in case services exist that are logging but not yet registered.
        for (Map.Entry<String, ServiceLogMetrics> entry : logMetricsMap.entrySet()) {
            String logServiceName = entry.getValue().service();
            String keyName = entry.getKey();

            if (!activeServiceNames.contains(keyName)) {
                ServiceLogMetrics metrics = entry.getValue();
                String status;
                if (metrics.errorCount() > 0) {
                    status = "ERROR";
                } else if (metrics.warnCount() > 0) {
                    status = "WARNING";
                } else {
                    status = "OK";
                }
                healthViews.add(new ServiceHealthView(logServiceName, status, metrics.lastSeen()));
            }
        }

        // Sort by service name case-insensitively
        healthViews.sort((a, b) -> a.service().compareToIgnoreCase(b.service()));

        return healthViews;
    }
}
