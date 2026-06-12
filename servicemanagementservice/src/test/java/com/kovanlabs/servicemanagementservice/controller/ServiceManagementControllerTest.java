package com.kovanlabs.servicemanagementservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.servicemanagementservice.dto.ServiceVerifyRequest;
import com.kovanlabs.servicemanagementservice.model.AppService;
import com.kovanlabs.servicemanagementservice.service.ServiceRequestWorkflowService;

@SpringBootTest
@AutoConfigureMockMvc
class ServiceManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private ServiceRequestWorkflowService workflowService;

    @MockBean
    private com.kovanlabs.servicemanagementservice.service.ServiceHealthService healthService;

    @Test
    void verifyService_publiclyAccessible_returnsVerificationResult() throws Exception {
        ServiceVerifyRequest request = new ServiceVerifyRequest("ak_testapikey123", "sv_testsecret123");
        when(workflowService.verifyServiceSecret("ak_testapikey123", "sv_testsecret123"))
                .thenReturn(Map.of("approved", true, "serviceName", "test-service"));

        mockMvc.perform(post("/api/services/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(true))
                .andExpect(jsonPath("$.serviceName").value("test-service"));
    }

    @Test
    @WithMockUser
    void getServiceSecret_authorized_returnsSecret() throws Exception {
        UUID serviceId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        when(workflowService.getServiceSecret(eq(orgId), eq(serviceId), eq("owner@test.com"), eq("dev")))
                .thenReturn(new com.kovanlabs.servicemanagementservice.dto.ServiceSecretResponse("sv_testsecret123", false, null, LocalDateTime.now()));

        mockMvc.perform(get("/api/services/" + serviceId + "/secret")
                        .header("X-Organization-Id", orgId.toString())
                        .header("X-User-Email", "owner@test.com")
                        .header("X-User-Role", "dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceSecret").value("sv_testsecret123"))
                .andExpect(jsonPath("$.hidden").value(false))
                .andExpect(jsonPath("$.secondsRemaining").value(org.hamcrest.CoreMatchers.nullValue()));
    }

    @Test
    @WithMockUser
    void regenerateSecret_authorized_returnsNewSecret() throws Exception {
        UUID serviceId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        AppService updatedService = new AppService();
        updatedService.setId(serviceId);
        updatedService.setName("my-service");
        updatedService.setServiceSecret("sv_newsecret456");
        updatedService.setSecretGeneratedAt(LocalDateTime.now());

        when(workflowService.regenerateServiceSecret(eq(orgId), eq(serviceId), eq("owner@test.com"), eq("dev")))
                .thenReturn(updatedService);

        mockMvc.perform(post("/api/services/" + serviceId + "/regenerate-secret")
                        .header("X-Organization-Id", orgId.toString())
                        .header("X-User-Email", "owner@test.com")
                        .header("X-User-Role", "dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value(serviceId.toString()))
                .andExpect(jsonPath("$.serviceName").value("my-service"))
                .andExpect(jsonPath("$.serviceSecret").value("sv_newsecret456"));
    }

    @Test
    @WithMockUser
    void getServiceApiKey_authorized_returnsApiKey() throws Exception {
        UUID serviceId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        when(workflowService.getServiceApiKey(eq(orgId), eq(serviceId), eq("owner@test.com"), eq("dev")))
                .thenReturn(new com.kovanlabs.servicemanagementservice.dto.ServiceApiKeyResponse("ak_testapikey123", LocalDateTime.now()));

        mockMvc.perform(get("/api/services/" + serviceId + "/api-key")
                        .header("X-Organization-Id", orgId.toString())
                        .header("X-User-Email", "owner@test.com")
                        .header("X-User-Role", "dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").value("ak_testapikey123"));
    }

    @Test
    @WithMockUser
    void regenerateApiKey_authorized_returnsNewApiKey() throws Exception {
        UUID serviceId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        AppService updatedService = new AppService();
        updatedService.setId(serviceId);
        updatedService.setName("my-service");
        updatedService.setApiKey("ak_newkey456");
        updatedService.setApiKeyGeneratedAt(LocalDateTime.now());

        when(workflowService.regenerateServiceApiKey(eq(orgId), eq(serviceId), eq("owner@test.com"), eq("dev")))
                .thenReturn(updatedService);

        mockMvc.perform(post("/api/services/" + serviceId + "/regenerate-api-key")
                        .header("X-Organization-Id", orgId.toString())
                        .header("X-User-Email", "owner@test.com")
                        .header("X-User-Role", "dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value(serviceId.toString()))
                .andExpect(jsonPath("$.serviceName").value("my-service"))
                .andExpect(jsonPath("$.apiKey").value("ak_newkey456"));
    }

    @Test
    @WithMockUser
    void getServicesHealth_filtersForDeveloper() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(healthService.getServicesHealth(eq(orgId), eq("dev@test.com"), eq("dev")))
                .thenReturn(java.util.List.of(new com.kovanlabs.servicemanagementservice.dto.ServiceHealthView("mapped-service", "OK", null)));

        mockMvc.perform(get("/api/services/health")
                        .header("X-Organization-Id", orgId.toString())
                        .header("X-User-Email", "dev@test.com")
                        .header("X-User-Role", "dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].service").value("mapped-service"))
                .andExpect(jsonPath("$[0].status").value("OK"));
    }
}
