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

    @Mock
    private com.kovanlabs.servicemanagementservice.repository.AppUserRepository appUserRepository;

    @Mock
    private com.kovanlabs.servicemanagementservice.repository.UserRoleMappingRepository userRoleMappingRepository;

    private ServiceRequestWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new ServiceRequestWorkflowService(
                serviceAccessRequestRepository,
                appServiceRepository,
                userServiceMappingRepository,
                appUserRepository,
                userRoleMappingRepository
        );
    }

    @Test
    void createRequest_createsPendingRequest() {
        UUID orgId = UUID.randomUUID();
        ServiceAccessRequest savedEntity = new ServiceAccessRequest();
        savedEntity.setId(UUID.randomUUID());
        savedEntity.setOrganizationId(orgId);
        savedEntity.setRequestedBy("dev@test.com");
        savedEntity.setServiceName("payment-service");
        savedEntity.setStatus(ServiceAccessRequest.RequestStatus.PENDING);
        savedEntity.setCreatedAt(LocalDateTime.now());
        savedEntity.setUpdatedAt(LocalDateTime.now());

        when(serviceAccessRequestRepository.save(any(ServiceAccessRequest.class))).thenReturn(savedEntity);

        var request = service.createRequest(
                orgId,
                new ServiceRequestCreateRequest("payment-service", "Payments API", "dev@test.com"),
                "dev@test.com");

        assertThat(request.serviceName()).isEqualTo("payment-service");
        assertThat(request.status()).isEqualTo("PENDING");
        assertThat(request.requestedBy()).isEqualTo("dev@test.com");
    }

    @Test
    void createRequest_missingServiceName_throwsBadRequest() {
        UUID orgId = UUID.randomUUID();
        assertThrows(ResponseStatusException.class, () ->
                service.createRequest(orgId, new ServiceRequestCreateRequest("", "desc", "dev@test.com"), "dev@test.com"));
    }

    @Test
    void approveAndReject_updateStatus() {
        UUID orgId = UUID.randomUUID();
        UUID reqId = UUID.randomUUID();
        ServiceAccessRequest pendingRequest = new ServiceAccessRequest();
        pendingRequest.setId(reqId);
        pendingRequest.setOrganizationId(orgId);
        pendingRequest.setServiceName("alerts");
        pendingRequest.setRequestedBy("dev@test.com");
        pendingRequest.setStatus(ServiceAccessRequest.RequestStatus.PENDING);

        when(serviceAccessRequestRepository.findById(reqId)).thenReturn(Optional.of(pendingRequest));
        when(serviceAccessRequestRepository.save(any(ServiceAccessRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        AppService mockAppService = new AppService();
        mockAppService.setName("alerts");
        mockAppService.setActive(true);
        mockAppService.setOrganizationId(orgId);
        when(appServiceRepository.findByOrganizationIdAndNameIgnoreCase(eq(orgId), eq("alerts"))).thenReturn(Optional.of(mockAppService));
        when(appServiceRepository.save(any(AppService.class))).thenAnswer(inv -> inv.getArgument(0));

        var approved = service.approveRequest(orgId, reqId.toString(), null);
        assertThat(approved.status()).isEqualTo("APPROVED");

        UUID reqId2 = UUID.randomUUID();
        ServiceAccessRequest pendingRequest2 = new ServiceAccessRequest();
        pendingRequest2.setId(reqId2);
        pendingRequest2.setOrganizationId(orgId);
        pendingRequest2.setServiceName("reporting");
        pendingRequest2.setRequestedBy("dev@test.com");
        pendingRequest2.setStatus(ServiceAccessRequest.RequestStatus.PENDING);

        when(serviceAccessRequestRepository.findById(reqId2)).thenReturn(Optional.of(pendingRequest2));
        var rejected = service.rejectRequest(orgId, reqId2.toString());
        assertThat(rejected.status()).isEqualTo("REJECTED");
    }

    @Test
    void verifyServiceSecret_validSecret_returnsSuccess() {
        UUID orgId = UUID.randomUUID();
        String secret = "sv_validsecret";
        AppService appSvc = new AppService();
        appSvc.setName("gateway-service");
        appSvc.setActive(true);
        appSvc.setServiceSecret(secret);
        appSvc.setOrganizationId(orgId);

        when(appServiceRepository.findByServiceSecret(secret)).thenReturn(Optional.of(appSvc));
        // No request exists, e.g. system seeded service
        when(serviceAccessRequestRepository.existsByOrganizationIdAndServiceNameIgnoreCase(eq(orgId), eq("gateway-service"))).thenReturn(false);

        var result = service.verifyServiceSecret(secret);
        assertThat(result.get("approved")).isEqualTo(true);
        assertThat(result.get("serviceName")).isEqualTo("gateway-service");
    }

    @Test
    void verifyServiceSecret_invalidFormat_returnsFailure() {
        var result = service.verifyServiceSecret("invalidformat");
        assertThat(result.get("approved")).isEqualTo(false);
    }

    @Test
    void verifyServiceSecret_notFound_returnsFailure() {
        String secret = "sv_notfound";
        when(appServiceRepository.findByServiceSecret(secret)).thenReturn(Optional.empty());

        var result = service.verifyServiceSecret(secret);
        assertThat(result.get("approved")).isEqualTo(false);
    }

    @Test
    void verifyServiceSecret_notApproved_returnsFailure() {
        UUID orgId = UUID.randomUUID();
        String secret = "sv_unapproved";
        AppService appSvc = new AppService();
        appSvc.setName("unapproved-service");
        appSvc.setActive(true);
        appSvc.setServiceSecret(secret);
        appSvc.setOrganizationId(orgId);

        when(appServiceRepository.findByServiceSecret(secret)).thenReturn(Optional.of(appSvc));
        // Request exists but it is not approved
        when(serviceAccessRequestRepository.existsByOrganizationIdAndServiceNameIgnoreCase(eq(orgId), eq("unapproved-service"))).thenReturn(true);
        when(serviceAccessRequestRepository.existsByOrganizationIdAndServiceNameIgnoreCaseAndStatus(eq(orgId), eq("unapproved-service"), eq(ServiceAccessRequest.RequestStatus.APPROVED))).thenReturn(false);

        var result = service.verifyServiceSecret(secret);
        assertThat(result.get("approved")).isEqualTo(false);
    }

    @Test
    void verifyServiceSecret_inactive_returnsFailure() {
        UUID orgId = UUID.randomUUID();
        String secret = "sv_inactive";
        AppService appSvc = new AppService();
        appSvc.setName("inactive-service");
        appSvc.setActive(false);
        appSvc.setServiceSecret(secret);
        appSvc.setOrganizationId(orgId);

        when(appServiceRepository.findByServiceSecret(secret)).thenReturn(Optional.of(appSvc));
        when(serviceAccessRequestRepository.existsByOrganizationIdAndServiceNameIgnoreCase(eq(orgId), eq("inactive-service"))).thenReturn(false);

        var result = service.verifyServiceSecret(secret);
        assertThat(result.get("approved")).isEqualTo(false);
    }

    @Test
    void getServiceSecret_authorizedOwner_returnsSecret() {
        UUID orgId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        AppService appSvc = new AppService();
        appSvc.setId(serviceId);
        appSvc.setName("test-service");
        appSvc.setActive(true);
        appSvc.setServiceSecret("sv_originalsecret");
        appSvc.setSecretGeneratedAt(LocalDateTime.now().minusDays(10)); // 10 days old
        appSvc.setOrganizationId(orgId);

        com.kovanlabs.servicemanagementservice.model.AppUser mockUser = new com.kovanlabs.servicemanagementservice.model.AppUser();
        mockUser.setId("user-123");
        mockUser.setEmail("owner@test.com");

        when(appServiceRepository.findById(serviceId)).thenReturn(Optional.of(appSvc));
        when(appUserRepository.findByEmailIgnoreCaseAndActiveTrue("owner@test.com")).thenReturn(Optional.of(mockUser));
        when(userServiceMappingRepository.existsByUser_IdAndService_Id("user-123", serviceId)).thenReturn(true);

        var result = service.getServiceSecret(orgId, serviceId, "owner@test.com", "dev");

        assertThat(result.serviceSecret()).isEqualTo("sv_originalsecret");
        assertThat(result.hidden()).isFalse();
        assertThat(result.secondsRemaining()).isNull(); // No expiration
    }

    @Test
    void getServiceSecret_unauthorized_throwsForbidden() {
        UUID orgId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        AppService appSvc = new AppService();
        appSvc.setId(serviceId);
        appSvc.setName("test-service");
        appSvc.setActive(true);
        appSvc.setOrganizationId(orgId);

        when(appServiceRepository.findById(serviceId)).thenReturn(Optional.of(appSvc));
        when(appUserRepository.findByEmailIgnoreCaseAndActiveTrue("other@test.com")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                service.getServiceSecret(orgId, serviceId, "other@test.com", "dev"));
    }

    @Test
    void regenerateServiceSecret_authorizedAdmin_regeneratesSecret() {
        UUID orgId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        AppService appSvc = new AppService();
        appSvc.setId(serviceId);
        appSvc.setName("test-service");
        appSvc.setActive(true);
        appSvc.setServiceSecret("sv_oldsecret");
        appSvc.setSecretGeneratedAt(LocalDateTime.now().minusDays(1));
        appSvc.setOrganizationId(orgId);

        com.kovanlabs.servicemanagementservice.model.AppUser mockUser = new com.kovanlabs.servicemanagementservice.model.AppUser();
        mockUser.setId("admin-123");
        mockUser.setEmail("admin@test.com");

        com.kovanlabs.servicemanagementservice.model.UserRoleMapping mapping = new com.kovanlabs.servicemanagementservice.model.UserRoleMapping();
        com.kovanlabs.servicemanagementservice.model.AppRole role = new com.kovanlabs.servicemanagementservice.model.AppRole();
        role.setName("ADMIN");
        mapping.setRole(role);

        when(appServiceRepository.findById(serviceId)).thenReturn(Optional.of(appSvc));
        when(appUserRepository.findByEmailIgnoreCaseAndActiveTrue("admin@test.com")).thenReturn(Optional.of(mockUser));
        when(userRoleMappingRepository.findByUser_IdAndOrganization_Id("admin-123", orgId)).thenReturn(java.util.List.of(mapping));
        when(appServiceRepository.save(any(AppService.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.regenerateServiceSecret(orgId, serviceId, "admin@test.com", "admin");

        assertThat(result.getServiceSecret()).startsWith("sv_");
        assertThat(result.getServiceSecret()).isNotEqualTo("sv_oldsecret");
    }

    @Test
    void verifyServiceSecret_validSecretAndApiKey_returnsSuccess() {
        UUID orgId = UUID.randomUUID();
        String secret = "sv_validsecret";
        String apiKey = "ak_validapikey";
        AppService appSvc = new AppService();
        appSvc.setName("gateway-service");
        appSvc.setActive(true);
        appSvc.setServiceSecret(secret);
        appSvc.setApiKey(apiKey);
        appSvc.setOrganizationId(orgId);

        when(appServiceRepository.findByServiceSecret(secret)).thenReturn(Optional.of(appSvc));
        when(serviceAccessRequestRepository.existsByOrganizationIdAndServiceNameIgnoreCase(eq(orgId), eq("gateway-service"))).thenReturn(false);

        var result = service.verifyServiceSecret(apiKey, secret);
        assertThat(result.get("approved")).isEqualTo(true);
        assertThat(result.get("serviceName")).isEqualTo("gateway-service");
    }

    @Test
    void verifyServiceSecret_apiKeyMismatch_returnsFailure() {
        UUID orgId = UUID.randomUUID();
        String secret = "sv_validsecret";
        String apiKey = "ak_mismatched";
        AppService appSvc = new AppService();
        appSvc.setName("gateway-service");
        appSvc.setActive(true);
        appSvc.setServiceSecret(secret);
        appSvc.setApiKey("ak_validapikey");
        appSvc.setOrganizationId(orgId);

        when(appServiceRepository.findByServiceSecret(secret)).thenReturn(Optional.of(appSvc));

        var result = service.verifyServiceSecret(apiKey, secret);
        assertThat(result.get("approved")).isEqualTo(false);
    }

    @Test
    void getServiceApiKey_authorizedOwner_returnsApiKey() {
        UUID orgId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        AppService appSvc = new AppService();
        appSvc.setId(serviceId);
        appSvc.setName("test-service");
        appSvc.setActive(true);
        appSvc.setApiKey("ak_originalkey");
        appSvc.setApiKeyGeneratedAt(LocalDateTime.now().minusDays(10));
        appSvc.setOrganizationId(orgId);

        com.kovanlabs.servicemanagementservice.model.AppUser mockUser = new com.kovanlabs.servicemanagementservice.model.AppUser();
        mockUser.setId("user-123");
        mockUser.setEmail("owner@test.com");

        when(appServiceRepository.findById(serviceId)).thenReturn(Optional.of(appSvc));
        when(appUserRepository.findByEmailIgnoreCaseAndActiveTrue("owner@test.com")).thenReturn(Optional.of(mockUser));
        when(userServiceMappingRepository.existsByUser_IdAndService_Id("user-123", serviceId)).thenReturn(true);

        var result = service.getServiceApiKey(orgId, serviceId, "owner@test.com", "dev");

        assertThat(result.apiKey()).isEqualTo("ak_originalkey");
    }

    @Test
    void regenerateServiceApiKey_authorizedAdmin_regeneratesApiKey() {
        UUID orgId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        AppService appSvc = new AppService();
        appSvc.setId(serviceId);
        appSvc.setName("test-service");
        appSvc.setActive(true);
        appSvc.setApiKey("ak_oldkey");
        appSvc.setApiKeyGeneratedAt(LocalDateTime.now().minusDays(1));
        appSvc.setOrganizationId(orgId);

        com.kovanlabs.servicemanagementservice.model.AppUser mockUser = new com.kovanlabs.servicemanagementservice.model.AppUser();
        mockUser.setId("admin-123");
        mockUser.setEmail("admin@test.com");

        com.kovanlabs.servicemanagementservice.model.UserRoleMapping mapping = new com.kovanlabs.servicemanagementservice.model.UserRoleMapping();
        com.kovanlabs.servicemanagementservice.model.AppRole role = new com.kovanlabs.servicemanagementservice.model.AppRole();
        role.setName("ADMIN");
        mapping.setRole(role);

        when(appServiceRepository.findById(serviceId)).thenReturn(Optional.of(appSvc));
        when(appUserRepository.findByEmailIgnoreCaseAndActiveTrue("admin@test.com")).thenReturn(Optional.of(mockUser));
        when(userRoleMappingRepository.findByUser_IdAndOrganization_Id("admin-123", orgId)).thenReturn(java.util.List.of(mapping));
        when(appServiceRepository.save(any(AppService.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.regenerateServiceApiKey(orgId, serviceId, "admin@test.com", "admin");

        assertThat(result.getApiKey()).startsWith("ak_");
        assertThat(result.getApiKey()).isNotEqualTo("ak_oldkey");
    }
}