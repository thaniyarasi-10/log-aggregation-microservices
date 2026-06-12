package com.kovanlabs.servicemanagementservice.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
<<<<<<< HEAD
import java.util.UUID;
=======
>>>>>>> 6a01b900be15a6a689e602f89925f0c54101ef47
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.kovanlabs.servicemanagementservice.dto.ServiceHealthView;
import com.kovanlabs.servicemanagementservice.model.AppService;
<<<<<<< HEAD
import com.kovanlabs.servicemanagementservice.model.UserServiceMapping;
import com.kovanlabs.servicemanagementservice.repository.AppServiceRepository;
import com.kovanlabs.servicemanagementservice.repository.UserServiceMappingRepository;
import com.kovanlabs.servicemanagementservice.repository.AppUserRepository;
=======
import com.kovanlabs.servicemanagementservice.repository.AppServiceRepository;
>>>>>>> 6a01b900be15a6a689e602f89925f0c54101ef47

@Service
public class ServiceHealthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceHealthService.class);

    private final AppServiceRepository appServiceRepository;
<<<<<<< HEAD
    private final UserServiceMappingRepository userServiceMappingRepository;
    private final AppUserRepository appUserRepository;
=======
>>>>>>> 6a01b900be15a6a689e602f89925f0c54101ef47
    private final RestClient restClient;

    @Value("${services.health.window-minutes:15}")
    private int windowMinutes;

    public ServiceHealthService(AppServiceRepository appServiceRepository,
<<<<<<< HEAD
                                UserServiceMappingRepository userServiceMappingRepository,
                                AppUserRepository appUserRepository,
                                @Value("${services.health.log-service-url:http://localhost:8081}") String logServiceUrl) {
        this.appServiceRepository = appServiceRepository;
        this.userServiceMappingRepository = userServiceMappingRepository;
        this.appUserRepository = appUserRepository;
=======
                                @Value("${services.health.log-service-url:http://localhost:8081}") String logServiceUrl) {
        this.appServiceRepository = appServiceRepository;
>>>>>>> 6a01b900be15a6a689e602f89925f0c54101ef47
        this.restClient = RestClient.builder().baseUrl(logServiceUrl).build();
    }

    private record ServiceLogMetrics(
            String service,
            long errorCount,
            long warnCount,
            Instant lastSeen
    ) {}

    public List<ServiceHealthView> getServicesHealth() {
<<<<<<< HEAD
        return getServicesHealth(UUID.randomUUID(), null, "admin");
    }

    public List<ServiceHealthView> getServicesHealth(UUID orgId, String userEmail, String userRole) {
//        LOGGER.info("Calculating service health based on registered services and log metrics for user: {}, role: {}", userEmail, userRole);

        boolean isAdmin = userRole != null && userRole.equalsIgnoreCase("admin");

        // 1. Fetch registered active services from database
        List<AppService> registeredServices;
        if (isAdmin) {
            registeredServices = appServiceRepository.findByOrganizationIdAndActiveTrueOrderByNameAsc(orgId);
        } else {
            if (userEmail == null || userEmail.isBlank()) {
                return List.of();
            }
            registeredServices = appUserRepository.findByEmailIgnoreCaseAndActiveTrue(userEmail.trim())
                    .map(user -> userServiceMappingRepository.findByUser_Id(user.getId()).stream()
                            .map(UserServiceMapping::getService)
                            .filter(s -> s.isActive() && orgId.equals(s.getOrganizationId()))
                            .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                            .toList())
                    .orElse(List.of());
        }

=======
        //LOGGER.info("Calculating service health based on registered services and log metrics");

        // 1. Fetch registered active services from database
        List<AppService> registeredServices = appServiceRepository.findByActiveTrueOrderByNameAsc();
>>>>>>> 6a01b900be15a6a689e602f89925f0c54101ef47
        Set<String> activeServiceNames = registeredServices.stream()
                .map(AppService::getName)
                .filter(Objects::nonNull)
                .map(name -> name.trim().toLowerCase())
                .collect(Collectors.toSet());

        // 2. Fetch log metrics from logservice
        List<ServiceLogMetrics> logMetrics = new ArrayList<>();
        try {
<<<<<<< HEAD
//            LOGGER.info("Calling Log Service to fetch service health aggregation for window: {} minutes", windowMinutes);
=======
           // LOGGER.info("Calling Log Service to fetch service health aggregation for window: {} minutes", windowMinutes);
>>>>>>> 6a01b900be15a6a689e602f89925f0c54101ef47
            List<ServiceLogMetrics> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/logs/service-health")
                            .queryParam("windowMinutes", windowMinutes)
                            .build())
<<<<<<< HEAD
                    .header("X-Organization-Id", orgId.toString())
=======
>>>>>>> 6a01b900be15a6a689e602f89925f0c54101ef47
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

<<<<<<< HEAD
        // 4. Also include any unregistered services found in Elasticsearch log metrics (ADMIN ONLY!)
        if (isAdmin) {
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
=======
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
>>>>>>> 6a01b900be15a6a689e602f89925f0c54101ef47
            }
        }

        // Sort by service name case-insensitively
        healthViews.sort((a, b) -> a.service().compareToIgnoreCase(b.service()));

        return healthViews;
    }
}
