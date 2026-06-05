package com.kovanlabs.servicemanagementservice.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.kovanlabs.servicemanagementservice.dto.ServiceOwnerView;
import com.kovanlabs.servicemanagementservice.dto.ServiceRequestCreateRequest;
import com.kovanlabs.servicemanagementservice.dto.ServiceRequestView;
import com.kovanlabs.servicemanagementservice.dto.ServiceSummaryView;

import com.kovanlabs.servicemanagementservice.model.AppService;
import com.kovanlabs.servicemanagementservice.model.ServiceAccessRequest;
import com.kovanlabs.servicemanagementservice.model.UserServiceMapping;
import com.kovanlabs.servicemanagementservice.repository.AppServiceRepository;
import com.kovanlabs.servicemanagementservice.repository.ServiceAccessRequestRepository;
import com.kovanlabs.servicemanagementservice.repository.UserServiceMappingRepository;

@Service
public class ServiceRequestWorkflowService {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ServiceRequestWorkflowService.class);

    private final ServiceAccessRequestRepository serviceAccessRequestRepository;
    private final AppServiceRepository appServiceRepository;
    private final UserServiceMappingRepository userServiceMappingRepository;

    public ServiceRequestWorkflowService(ServiceAccessRequestRepository serviceAccessRequestRepository,
                                         AppServiceRepository appServiceRepository,
                                         UserServiceMappingRepository userServiceMappingRepository) {
        this.serviceAccessRequestRepository = serviceAccessRequestRepository;
        this.appServiceRepository = appServiceRepository;
        this.userServiceMappingRepository = userServiceMappingRepository;
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @org.springframework.transaction.annotation.Transactional
    public void seedDefaultServices() {
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
                svc.setActive(true);
                svc.setCreatedAt(java.time.LocalDateTime.now());
                svc.setUpdatedAt(java.time.LocalDateTime.now());
                appServiceRepository.save(svc);
                LOGGER.info("Seeded default service: {}", name);
            }
        }
    }

    public List<ServiceSummaryView> listServices() {
        return appServiceRepository.findByActiveTrueOrderByNameAsc().stream()
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

    public List<ServiceRequestView> listRequests(String requestedBy, boolean includeAll) {
        List<ServiceAccessRequest> source = includeAll
                ? serviceAccessRequestRepository.findAllByOrderByCreatedAtDesc()
                : serviceAccessRequestRepository.findByRequestedByOrderByCreatedAtDesc(normalizeRequester(requestedBy));
        return source.stream().map(this::toView).toList();
    }

    @Transactional
    public ServiceRequestView createRequest(ServiceRequestCreateRequest request, String requestedBy) {
        if (request == null || request.serviceName() == null || request.serviceName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service name is required");
        }

        String normalizedServiceName = request.serviceName().trim().toLowerCase(Locale.ROOT);
        String requester = normalizeRequester(requestedBy != null && !requestedBy.isBlank() ? requestedBy : request.requestedBy());
        if (requester.isBlank()) {
            requester = "anonymous@local";
        }

        List<ServiceAccessRequest> pending = serviceAccessRequestRepository
                .findByRequestedByAndServiceNameIgnoreCaseAndStatus(
                        requester,
                        normalizedServiceName,
                        ServiceAccessRequest.RequestStatus.PENDING);
        if (!pending.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A pending request for '" + normalizedServiceName + "' already exists");
        }

        ServiceAccessRequest stored = new ServiceAccessRequest();
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
    public ServiceRequestView approveRequest(String requestId, ServiceRequestCreateRequest request) {
        ServiceAccessRequest existing = requireRequest(requestId);
        existing.setStatus(ServiceAccessRequest.RequestStatus.APPROVED);
        if (request != null && request.description() != null && !request.description().isBlank()) {
            existing.setDescription(request.description().trim());
        }
        existing.setUpdatedAt(LocalDateTime.now());

        ServiceAccessRequest saved = serviceAccessRequestRepository.save(existing);

        AppService appService = appServiceRepository.findByNameIgnoreCase(saved.getServiceName())
                .orElseGet(() -> {
                    AppService created = new AppService();
                    created.setName(saved.getServiceName());
                    created.setDescription(saved.getDescription());
                    created.setActive(true);
                    created.setCreatedAt(LocalDateTime.now());
                    created.setUpdatedAt(LocalDateTime.now());
                    return appServiceRepository.save(created);
                });
        appService.setActive(true);
        appService.setUpdatedAt(LocalDateTime.now());
        appServiceRepository.save(appService);

        return toView(saved);
    }

    @Transactional
    public ServiceRequestView rejectRequest(String requestId) {
        ServiceAccessRequest existing = requireRequest(requestId);
        existing.setStatus(ServiceAccessRequest.RequestStatus.REJECTED);
        existing.setUpdatedAt(LocalDateTime.now());
        ServiceAccessRequest saved = serviceAccessRequestRepository.save(existing);

        return toView(saved);
    }

    public ServiceRequestView getRequestById(String requestId) {
        return toView(requireRequest(requestId));
    }

    public boolean isServiceApproved(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return false;
        }
        return appServiceRepository.findByNameIgnoreCase(serviceName.trim())
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

    private ServiceAccessRequest requireRequest(String requestId) {
        try {
            return serviceAccessRequestRepository.findById(java.util.UUID.fromString(requestId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request id");
        }
    }
}
