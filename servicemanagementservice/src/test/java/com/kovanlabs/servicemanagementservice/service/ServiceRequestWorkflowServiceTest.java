package com.kovanlabs.servicemanagementservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.kovanlabs.servicemanagementservice.dto.ServiceRequestCreateRequest;
import com.kovanlabs.servicemanagementservice.model.AppService;
import com.kovanlabs.servicemanagementservice.model.ServiceAccessRequest;
import com.kovanlabs.servicemanagementservice.repository.AppServiceRepository;
import com.kovanlabs.servicemanagementservice.repository.ServiceAccessRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class ServiceRequestWorkflowServiceTest {

    @Mock
    private ServiceAccessRequestRepository serviceAccessRequestRepository;

    @Mock
    private AppServiceRepository appServiceRepository;

    @Mock
    private com.kovanlabs.servicemanagementservice.repository.UserServiceMappingRepository userServiceMappingRepository;

    private ServiceRequestWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new ServiceRequestWorkflowService(serviceAccessRequestRepository, appServiceRepository, userServiceMappingRepository);
    }

    @Test
    void createRequest_createsPendingRequest() {
        ServiceAccessRequest savedEntity = new ServiceAccessRequest();
        savedEntity.setId(UUID.randomUUID());
        savedEntity.setRequestedBy("dev@test.com");
        savedEntity.setServiceName("payment-service");
        savedEntity.setStatus(ServiceAccessRequest.RequestStatus.PENDING);
        savedEntity.setCreatedAt(LocalDateTime.now());
        savedEntity.setUpdatedAt(LocalDateTime.now());

        when(serviceAccessRequestRepository.save(any(ServiceAccessRequest.class))).thenReturn(savedEntity);

        var request = service.createRequest(
                new ServiceRequestCreateRequest("payment-service", "Payments API", "dev@test.com"),
                "dev@test.com");

        assertThat(request.serviceName()).isEqualTo("payment-service");
        assertThat(request.status()).isEqualTo("PENDING");
        assertThat(request.requestedBy()).isEqualTo("dev@test.com");
    }

    @Test
    void createRequest_missingServiceName_throwsBadRequest() {
        assertThrows(ResponseStatusException.class, () ->
                service.createRequest(new ServiceRequestCreateRequest("", "desc", "dev@test.com"), "dev@test.com"));
    }

    @Test
    void approveAndReject_updateStatus() {
        UUID reqId = UUID.randomUUID();
        ServiceAccessRequest pendingRequest = new ServiceAccessRequest();
        pendingRequest.setId(reqId);
        pendingRequest.setServiceName("alerts");
        pendingRequest.setRequestedBy("dev@test.com");
        pendingRequest.setStatus(ServiceAccessRequest.RequestStatus.PENDING);

        when(serviceAccessRequestRepository.findById(reqId)).thenReturn(Optional.of(pendingRequest));
        when(serviceAccessRequestRepository.save(any(ServiceAccessRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        AppService mockAppService = new AppService();
        mockAppService.setName("alerts");
        mockAppService.setActive(true);
        when(appServiceRepository.findByNameIgnoreCase("alerts")).thenReturn(Optional.of(mockAppService));
        when(appServiceRepository.save(any(AppService.class))).thenAnswer(inv -> inv.getArgument(0));

        var approved = service.approveRequest(reqId.toString(), null);
        assertThat(approved.status()).isEqualTo("APPROVED");

        UUID reqId2 = UUID.randomUUID();
        ServiceAccessRequest pendingRequest2 = new ServiceAccessRequest();
        pendingRequest2.setId(reqId2);
        pendingRequest2.setServiceName("reporting");
        pendingRequest2.setRequestedBy("dev@test.com");
        pendingRequest2.setStatus(ServiceAccessRequest.RequestStatus.PENDING);

        when(serviceAccessRequestRepository.findById(reqId2)).thenReturn(Optional.of(pendingRequest2));
        var rejected = service.rejectRequest(reqId2.toString());
        assertThat(rejected.status()).isEqualTo("REJECTED");
    }
}