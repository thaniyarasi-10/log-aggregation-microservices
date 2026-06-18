package com.kovanlabs.servicemanagementservice.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.kovanlabs.servicemanagementservice.dto.ServiceOwnerView;
import com.kovanlabs.servicemanagementservice.dto.ServiceRequestCreateRequest;
import com.kovanlabs.servicemanagementservice.dto.ServiceRequestView;
import com.kovanlabs.servicemanagementservice.dto.ServiceSummaryView;
import com.kovanlabs.servicemanagementservice.dto.ServiceSecretResponse;
import com.kovanlabs.servicemanagementservice.dto.ServiceApiKeyResponse;

import com.kovanlabs.servicemanagementservice.model.AppService;
import com.kovanlabs.servicemanagementservice.model.AppUser;
import com.kovanlabs.servicemanagementservice.model.ServiceAccessRequest;
import com.kovanlabs.servicemanagementservice.model.UserServiceMapping;
import com.kovanlabs.servicemanagementservice.model.UserRoleMapping;
import com.kovanlabs.servicemanagementservice.repository.AppServiceRepository;
import com.kovanlabs.servicemanagementservice.repository.ServiceAccessRequestRepository;
import com.kovanlabs.servicemanagementservice.repository.UserServiceMappingRepository;
import com.kovanlabs.servicemanagementservice.repository.AppUserRepository;
import com.kovanlabs.servicemanagementservice.repository.UserRoleMappingRepository;

@Service
public class ServiceRequestWorkflowService {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ServiceRequestWorkflowService.class);

    private final ServiceAccessRequestRepository serviceAccessRequestRepository;
    private final AppServiceRepository appServiceRepository;
    private final UserServiceMappingRepository userServiceMappingRepository;
    private final AppUserRepository appUserRepository;
    private final UserRoleMappingRepository userRoleMappingRepository;

    public ServiceRequestWorkflowService(ServiceAccessRequestRepository serviceAccessRequestRepository,
                                         AppServiceRepository appServiceRepository,
                                         UserServiceMappingRepository userServiceMappingRepository,
                                         AppUserRepository appUserRepository,
                                         UserRoleMappingRepository userRoleMappingRepository) {
        this.serviceAccessRequestRepository = serviceAccessRequestRepository;
        this.appServiceRepository = appServiceRepository;
        this.userServiceMappingRepository = userServiceMappingRepository;
        this.appUserRepository = appUserRepository;
        this.userRoleMappingRepository = userRoleMappingRepository;
    }

    public static String generateSecret() {
        return "sv_" + java.util.UUID.randomUUID().toString().replace("-", "");
    }

    public static String generateApiKey() {
        return "ak_" + java.util.UUID.randomUUID().toString().replace("-", "");
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @org.springframework.transaction.annotation.Transactional
    public void seedDefaultServices() {
        UUID defaultOrgId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        java.util.List<String> defaults = java.util.List.of(
            "gateway-service",
            "logservice",
            "log-service",
            "notification-service",
            "service-management-service",
            "servicemanagementservice"
        );
        for (String name : defaults) {
            if (appServiceRepository.findByNameIgnoreCase(name).isEmpty()) {
                AppService svc = new AppService();
                svc.setId(java.util.UUID.randomUUID());
                svc.setName(name);
                svc.setDescription("Auto-seeded system service");
                svc.setOrganizationId(defaultOrgId);
                svc.setActive(true);
                svc.setCreatedAt(java.time.LocalDateTime.now());
                svc.setUpdatedAt(java.time.LocalDateTime.now());
                svc.setServiceSecret(generateSecret());
                svc.setSecretGeneratedAt(java.time.LocalDateTime.now());
                svc.setApiKey(generateApiKey());
                svc.setApiKeyGeneratedAt(java.time.LocalDateTime.now());
                appServiceRepository.save(svc);
                LOGGER.info("Seeded default service: {}", name);
            }
        }

        List<AppService> allServices = appServiceRepository.findAll();
        for (AppService service : allServices) {
            boolean updated = false;
            if (service.getOrganizationId() == null) {
                service.setOrganizationId(defaultOrgId);
                updated = true;
            }
            if (service.isActive() && (service.getServiceSecret() == null || service.getServiceSecret().isBlank())) {
                service.setServiceSecret(generateSecret());
                service.setSecretGeneratedAt(java.time.LocalDateTime.now());
                updated = true;
                LOGGER.info("Migrated secret for existing active service: {}", service.getName());
            }
            if (service.isActive() && (service.getApiKey() == null || service.getApiKey().isBlank())) {
                service.setApiKey(generateApiKey());
                service.setApiKeyGeneratedAt(java.time.LocalDateTime.now());
                updated = true;
                LOGGER.info("Migrated API key for existing active service: {}", service.getName());
            }
            if (updated) {
                appServiceRepository.save(service);
            }
        }
    }

    public List<ServiceSummaryView> listServices(UUID organizationId, String userEmail, String userRole) {
        boolean isAdmin = false;
        Optional<AppUser> oUser = appUserRepository.findByEmailIgnoreCaseAndActiveTrue(userEmail == null ? "" : userEmail.trim());
        if (oUser.isPresent()) {
            isAdmin = userRoleMappingRepository.findByUser_IdAndOrganization_Id(oUser.get().getId(), organizationId).stream()
                    .anyMatch(rm -> "ADMIN".equalsIgnoreCase(rm.getRole().getName()) || "OWNER".equalsIgnoreCase(rm.getRole().getName()));
        }

        List<AppService> services;
        if (isAdmin) {
            services = appServiceRepository.findByOrganizationIdAndActiveTrueOrderByNameAsc(organizationId);
        } else {
            if (oUser.isEmpty()) {
                return List.of();
            }
            AppUser user = oUser.get();
            services = userServiceMappingRepository.findByUser_Id(user.getId()).stream()
                    .map(UserServiceMapping::getService)
                    .filter(AppService::isActive)
                    .filter(s -> organizationId.equals(s.getOrganizationId()))
                    .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                    .toList();
        }

        return services.stream()
                .map(entry -> {
                    List<UserServiceMapping> mappings = userServiceMappingRepository.findByService_Id(entry.getId());
                    List<ServiceOwnerView> owners = mappings.stream()
                            .map(m -> new ServiceOwnerView(
                                     m.getUser().getId(),
                                     m.getUser().getUsername(),
                                     m.isPrimary()))
                            .toList();
                    return new ServiceSummaryView(
                            entry.getId() == null ? null : entry.getId().toString(),
                            entry.getName(),
                            entry.getDescription(),
                            entry.isActive(),
                            owners);
                })
                .toList();
    }

    public List<ServiceRequestView> listRequests(UUID organizationId, String requestedBy, boolean includeAll) {
        List<ServiceAccessRequest> source = includeAll
                ? serviceAccessRequestRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId)
                : serviceAccessRequestRepository.findByOrganizationIdAndRequestedByOrderByCreatedAtDesc(organizationId, normalizeRequester(requestedBy));
        return source.stream().map(this::toView).toList();
    }

    @Transactional
    public ServiceRequestView createRequest(UUID organizationId, ServiceRequestCreateRequest request, String requestedBy) {
        if (request == null || request.serviceName() == null || request.serviceName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service name is required");
        }

        String normalizedServiceName = request.serviceName().trim().toLowerCase(Locale.ROOT);
        String requester = normalizeRequester(requestedBy != null && !requestedBy.isBlank() ? requestedBy : request.requestedBy());
        if (requester.isBlank()) {
            requester = "anonymous@local";
        }

        List<ServiceAccessRequest> pending = serviceAccessRequestRepository
                .findByOrganizationIdAndRequestedByAndServiceNameIgnoreCaseAndStatus(
                        organizationId,
                        requester,
                        normalizedServiceName,
                        ServiceAccessRequest.RequestStatus.PENDING);
        if (!pending.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A pending request for '" + normalizedServiceName + "' already exists");
        }

        ServiceAccessRequest stored = new ServiceAccessRequest();
        stored.setOrganizationId(organizationId);
        stored.setRequestedBy(requester);
        stored.setServiceName(normalizedServiceName);
        stored.setDescription(request.description() == null ? "" : request.description().trim());
        stored.setStatus(ServiceAccessRequest.RequestStatus.PENDING);
        stored.setCreatedAt(LocalDateTime.now());
        stored.setUpdatedAt(LocalDateTime.now());

        ServiceAccessRequest saved = serviceAccessRequestRepository.save(stored);

        return toView(saved);
    }

    @Transactional
    public ServiceRequestView approveRequest(UUID organizationId, String requestId, ServiceRequestCreateRequest request) {
        ServiceAccessRequest existing = requireRequest(organizationId, requestId);
        existing.setStatus(ServiceAccessRequest.RequestStatus.APPROVED);
        if (request != null && request.description() != null && !request.description().isBlank()) {
            existing.setDescription(request.description().trim());
        }
        existing.setUpdatedAt(LocalDateTime.now());

        ServiceAccessRequest saved = serviceAccessRequestRepository.save(existing);

        AppService appService = appServiceRepository.findByOrganizationIdAndNameIgnoreCase(organizationId, saved.getServiceName())
                .orElseGet(() -> {
                    AppService created = new AppService();
                    created.setName(saved.getServiceName());
                    created.setDescription(saved.getDescription());
                    created.setOrganizationId(organizationId);
                    created.setActive(true);
                    created.setCreatedAt(LocalDateTime.now());
                    created.setUpdatedAt(LocalDateTime.now());
                    return appServiceRepository.save(created);
                });
        appService.setActive(true);
        appService.setUpdatedAt(LocalDateTime.now());
        if (appService.getServiceSecret() == null || appService.getServiceSecret().isBlank()) {
            appService.setServiceSecret(generateSecret());
            appService.setSecretGeneratedAt(LocalDateTime.now());
        }
        if (appService.getApiKey() == null || appService.getApiKey().isBlank()) {
            appService.setApiKey(generateApiKey());
            appService.setApiKeyGeneratedAt(LocalDateTime.now());
        }
        appServiceRepository.save(appService);

        return toView(saved);
    }

    @Transactional
    public ServiceRequestView rejectRequest(UUID organizationId, String requestId) {
        ServiceAccessRequest existing = requireRequest(organizationId, requestId);
        existing.setStatus(ServiceAccessRequest.RequestStatus.REJECTED);
        existing.setUpdatedAt(LocalDateTime.now());
        ServiceAccessRequest saved = serviceAccessRequestRepository.save(existing);

        return toView(saved);
    }

    public ServiceRequestView getRequestById(UUID organizationId, String requestId) {
        return toView(requireRequest(organizationId, requestId));
    }

    public boolean isServiceApproved(UUID organizationId, String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return false;
        }
        return appServiceRepository.findByOrganizationIdAndNameIgnoreCase(organizationId, serviceName.trim())
                .map(AppService::isActive)
                .orElse(false);
    }

    private String normalizeRequester(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private ServiceRequestView toView(ServiceAccessRequest request) {
        return new ServiceRequestView(
                request.getId().toString(),
                request.getRequestedBy(),
                request.getServiceName(),
                request.getDescription(),
                request.getStatus().name(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }

    private ServiceAccessRequest requireRequest(UUID organizationId, String requestId) {
        try {
            ServiceAccessRequest request = serviceAccessRequestRepository.findById(java.util.UUID.fromString(requestId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
            if (!organizationId.equals(request.getOrganizationId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access to request in another organization is denied");
            }
            return request;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request id");
        }
    }

    public boolean isUserOwnerOrAdmin(UUID organizationId, java.util.UUID serviceId, String userEmail, String userRole) {
        Optional<AppUser> oUser = appUserRepository.findByEmailIgnoreCaseAndActiveTrue(userEmail == null ? "" : userEmail.trim());
        if (oUser.isEmpty()) {
            return false;
        }
        AppUser user = oUser.get();

        boolean isAdmin = userRoleMappingRepository.findByUser_IdAndOrganization_Id(user.getId(), organizationId).stream()
                .anyMatch(rm -> "ADMIN".equalsIgnoreCase(rm.getRole().getName()) || "OWNER".equalsIgnoreCase(rm.getRole().getName()));

        if (isAdmin) {
            return true;
        }

        AppService service = appServiceRepository.findById(serviceId).orElse(null);
        if (service == null || !organizationId.equals(service.getOrganizationId())) {
            return false;
        }

        return userServiceMappingRepository.existsByUser_IdAndService_Id(user.getId(), serviceId);
    }

    public ServiceSecretResponse getServiceSecret(UUID organizationId, java.util.UUID serviceId, String userEmail, String userRole) {
        AppService service = appServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));

        if (!organizationId.equals(service.getOrganizationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Service does not belong to this organization");
        }

        if (!isUserOwnerOrAdmin(organizationId, serviceId, userEmail, userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to view this secret");
        }

        if (service.getServiceSecret() == null || service.getServiceSecret().isBlank()) {
            service.setServiceSecret(generateSecret());
            service.setSecretGeneratedAt(LocalDateTime.now());
            appServiceRepository.save(service);
        }

        LocalDateTime generatedAt = service.getSecretGeneratedAt();
        if (generatedAt == null) {
            generatedAt = LocalDateTime.now();
            service.setSecretGeneratedAt(generatedAt);
            appServiceRepository.save(service);
        }

        return new ServiceSecretResponse(service.getServiceSecret(), false, null, generatedAt);
    }

    @Transactional
    public AppService regenerateServiceSecret(UUID organizationId, java.util.UUID serviceId, String userEmail, String userRole) {
        AppService service = appServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));

        if (!organizationId.equals(service.getOrganizationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Service does not belong to this organization");
        }

        if (!isUserOwnerOrAdmin(organizationId, serviceId, userEmail, userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to regenerate this secret");
        }

        service.setServiceSecret(generateSecret());
        service.setSecretGeneratedAt(LocalDateTime.now());
        service.setUpdatedAt(LocalDateTime.now());
        return appServiceRepository.save(service);
    }

    public ServiceApiKeyResponse getServiceApiKey(UUID organizationId, java.util.UUID serviceId, String userEmail, String userRole) {
        AppService service = appServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));

        if (!organizationId.equals(service.getOrganizationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Service does not belong to this organization");
        }

        if (!isUserOwnerOrAdmin(organizationId, serviceId, userEmail, userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to view this API key");
        }

        if (service.getApiKey() == null || service.getApiKey().isBlank()) {
            service.setApiKey(generateApiKey());
            service.setApiKeyGeneratedAt(LocalDateTime.now());
            appServiceRepository.save(service);
        }

        LocalDateTime generatedAt = service.getApiKeyGeneratedAt();
        if (generatedAt == null) {
            generatedAt = LocalDateTime.now();
            service.setApiKeyGeneratedAt(generatedAt);
            appServiceRepository.save(service);
        }

        return new ServiceApiKeyResponse(service.getApiKey(), generatedAt);
    }

    @Transactional
    public AppService regenerateServiceApiKey(UUID organizationId, java.util.UUID serviceId, String userEmail, String userRole) {
        AppService service = appServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));

        if (!organizationId.equals(service.getOrganizationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Service does not belong to this organization");
        }

        if (!isUserOwnerOrAdmin(organizationId, serviceId, userEmail, userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to regenerate this API key");
        }

        service.setApiKey(generateApiKey());
        service.setApiKeyGeneratedAt(LocalDateTime.now());
        service.setUpdatedAt(LocalDateTime.now());
        return appServiceRepository.save(service);
    }

    @Transactional
    public void setPrimaryOwner(String serviceName, String userId) {
        if (serviceName == null || serviceName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service name is required");
        }
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User ID is required");
        }

        AppService service = appServiceRepository.findByNameIgnoreCase(serviceName.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found: " + serviceName));

        List<UserServiceMapping> mappings = userServiceMappingRepository.findByService_Id(service.getId());
        boolean userFound = false;

        for (UserServiceMapping mapping : mappings) {
            if (mapping.getUser().getId().equals(userId.trim())) {
                mapping.setPrimary(true);
                userFound = true;
            } else {
                mapping.setPrimary(false);
            }
            mapping.setUpdatedAt(LocalDateTime.now());
        }

        if (!userFound) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not assigned to this service");
        }

        userServiceMappingRepository.saveAll(mappings);
    }

    public java.util.Map<String, Object> verifyServiceSecret(String serviceSecret) {
        return verifyServiceSecret(null, serviceSecret);
    }

    public java.util.Map<String, Object> verifyServiceSecret(String apiKey, String serviceSecret) {
        if (serviceSecret == null || serviceSecret.isBlank()) {
            LOGGER.warn("Service verification failed: invalid secret supplied");
            return java.util.Map.of("approved", false);
        }

        if (!serviceSecret.startsWith("sv_")) {
            LOGGER.warn("Service verification failed: invalid secret supplied");
            return java.util.Map.of("approved", false);
        }

        java.util.Optional<AppService> oService = appServiceRepository.findByServiceSecret(serviceSecret);
        if (oService.isEmpty()) {
            LOGGER.warn("Service verification failed: service not found for supplied secret");
            return java.util.Map.of("approved", false);
        }

        AppService service = oService.get();

        if (apiKey != null && !apiKey.equals(service.getApiKey())) {
            LOGGER.warn("Service verification failed: API key mismatch for service {}", service.getName());
            return java.util.Map.of("approved", false);
        }

        boolean hasRequests = serviceAccessRequestRepository.existsByOrganizationIdAndServiceNameIgnoreCase(service.getOrganizationId(), service.getName());
        boolean isApproved = !hasRequests || serviceAccessRequestRepository.existsByOrganizationIdAndServiceNameIgnoreCaseAndStatus(
                service.getOrganizationId(), service.getName(), ServiceAccessRequest.RequestStatus.APPROVED);

        if (!isApproved) {
            LOGGER.warn("Service verification failed: service {} is not approved", service.getName());
            return java.util.Map.of("approved", false);
        }

        if (!service.isActive()) {
            LOGGER.warn("Service verification failed: service {} is inactive", service.getName());
            return java.util.Map.of("approved", false);
        }

        LOGGER.info("Service verification success: service {} verified", service.getName());
        return java.util.Map.of(
            "approved", true,
            "serviceName", service.getName(),
            "organizationId", service.getOrganizationId().toString()
        );
    }
}
