package com.kovanlabs.notificationservice.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.notificationservice.dto.AlertNotificationRequest;
import com.kovanlabs.notificationservice.dto.NotificationPreferenceRequest;
import com.kovanlabs.notificationservice.model.NotificationPreference;
import com.kovanlabs.notificationservice.model.Alert;
import com.kovanlabs.notificationservice.repository.AlertRepository;
import com.kovanlabs.notificationservice.service.AlertNotificationService;
import com.kovanlabs.notificationservice.service.NotificationPreferenceService;
import com.kovanlabs.notificationservice.service.JiraStoryService;
import com.kovanlabs.notificationservice.security.TenantSecurityService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock private NotificationPreferenceService preferenceService;
    @Mock private AlertNotificationService alertNotificationService;
    @Mock private AlertRepository alertRepository;
    @Mock private JiraStoryService jiraStoryService;
    @Mock private TenantSecurityService tenantSecurityService;

    @InjectMocks private NotificationController controller;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();
    private UUID orgId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        orgId = UUID.randomUUID();

        org.mockito.Mockito.lenient().when(tenantSecurityService.validateMembership(any(), any()))
                .thenReturn(orgId);
        org.mockito.Mockito.lenient().when(tenantSecurityService.validateMembershipAndRole(any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));
    }

    @Test
    void getPreferences_returnsView() throws Exception {
        when(preferenceService.getOrCreatePreference("user-123")).thenReturn(preference(true));

        mockMvc.perform(get("/api/notifications/preferences").header("X-User-Id", "user-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled", equalTo(true)));
    }

    @Test
    void updatePreferences_returnsUpdatedView() throws Exception {
        when(preferenceService.updatePreference("user-123", false)).thenReturn(preference(false));

        mockMvc.perform(put("/api/notifications/preferences")
                        .header("X-User-Id", "user-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new NotificationPreferenceRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled", equalTo(false)));
    }

    @Test
    void sendAlert_returnsAcceptedStatusWhenSent() throws Exception {
        when(alertNotificationService.sendAlert(any(AlertNotificationRequest.class))).thenReturn(true);

        mockMvc.perform(post("/api/notifications/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AlertNotificationRequest(
                                "dev@test.com", "Dev User", "payment-service", "CRITICAL", "DB timeout", 4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("accepted")));
    }

    @Test
    void getAlerts_returnsGroupedAlerts() throws Exception {
        Alert alert1 = new Alert();
        alert1.setService("payment-service");
        alert1.setMessage("DB timeout");
        alert1.setCount(4);
        alert1.setSeverity("CRITICAL");
        alert1.setTimestamp(LocalDateTime.now());
        alert1.setOrganizationId(orgId);

        when(alertRepository.findByOrganizationId(orgId)).thenReturn(List.of(alert1));

        mockMvc.perform(get("/api/notifications/alerts")
                        .header("X-User-Id", "user-123")
                        .header("X-Organization-Id", orgId.toString())
                        .header("X-User-Services", "payment-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['payment-service'][0].message", equalTo("DB timeout")))
                .andExpect(jsonPath("$.['payment-service'][0].count", equalTo(4)));
    }

    @Test
    void getAlerts_withDevRoleAndAllowedService_returnsFilteredAlerts() throws Exception {
        Alert alert1 = new Alert();
        alert1.setService("payment-service");
        alert1.setMessage("DB timeout");
        alert1.setCount(4);
        alert1.setSeverity("CRITICAL");
        alert1.setTimestamp(LocalDateTime.now());
        alert1.setOrganizationId(orgId);

        Alert alert2 = new Alert();
        alert2.setService("auth-service");
        alert2.setMessage("Unauthorized attempt");
        alert2.setCount(2);
        alert2.setSeverity("WARNING");
        alert2.setTimestamp(LocalDateTime.now());
        alert2.setOrganizationId(orgId);

        when(alertRepository.findByOrganizationId(orgId)).thenReturn(List.of(alert1, alert2));

        mockMvc.perform(get("/api/notifications/alerts")
                        .header("X-User-Id", "user-123")
                        .header("X-Organization-Id", orgId.toString())
                        .header("X-User-Role", "DEV")
                        .header("X-User-Services", "payment-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['payment-service'][0].message", equalTo("DB timeout")))
                .andExpect(jsonPath("$.['auth-service']").doesNotExist());
    }

    @Test
    void getAlerts_withDevRoleAndDisallowedService_filtersOut() throws Exception {
        Alert alert1 = new Alert();
        alert1.setService("payment-service");
        alert1.setMessage("DB timeout");
        alert1.setCount(4);
        alert1.setSeverity("CRITICAL");
        alert1.setTimestamp(LocalDateTime.now());
        alert1.setOrganizationId(orgId);

        when(alertRepository.findByOrganizationId(orgId)).thenReturn(List.of(alert1));

        mockMvc.perform(get("/api/notifications/alerts")
                        .header("X-User-Id", "user-123")
                        .header("X-Organization-Id", orgId.toString())
                        .header("X-User-Role", "DEV")
                        .header("X-User-Services", "auth-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['payment-service']").doesNotExist());
    }

    @Test
    void getAlerts_withAdminRole_returnsAllAlerts() throws Exception {
        Alert alert1 = new Alert();
        alert1.setService("payment-service");
        alert1.setMessage("DB timeout");
        alert1.setCount(4);
        alert1.setSeverity("CRITICAL");
        alert1.setTimestamp(LocalDateTime.now());
        alert1.setOrganizationId(orgId);

        Alert alert2 = new Alert();
        alert2.setService("auth-service");
        alert2.setMessage("Unauthorized attempt");
        alert2.setCount(2);
        alert2.setSeverity("WARNING");
        alert2.setTimestamp(LocalDateTime.now());
        alert2.setOrganizationId(orgId);

        when(alertRepository.findByOrganizationId(orgId)).thenReturn(List.of(alert1, alert2));
        doReturn(orgId).when(tenantSecurityService).validateMembershipAndRole(any(), any(), eq("ADMIN"));

        mockMvc.perform(get("/api/notifications/alerts")
                        .header("X-User-Id", "user-123")
                        .header("X-Organization-Id", orgId.toString())
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Services", "payment-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['payment-service'][0].message", equalTo("DB timeout")))
                .andExpect(jsonPath("$.['auth-service'][0].message", equalTo("Unauthorized attempt")));
    }

    @Test
    void createJiraStoryPost_returnsDetails() throws Exception {
        UUID alertId = UUID.randomUUID();
        Alert alert = new Alert();
        alert.setId(alertId);
        alert.setOrganizationId(orgId);

        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));

        com.kovanlabs.notificationservice.dto.JiraStoryResponse response = new com.kovanlabs.notificationservice.dto.JiraStoryResponse("CREATED", "Created successfully", "PAY-12", "http://jira/PAY-12");
        when(jiraStoryService.createJiraStoryForAlert(alertId.toString())).thenReturn(response);

        mockMvc.perform(post("/api/notifications/alerts/" + alertId + "/jira")
                        .header("X-User-Id", "user-123")
                        .header("X-Organization-Id", orgId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("CREATED")))
                .andExpect(jsonPath("$.jiraIssueKey", equalTo("PAY-12")));
    }

    @Test
    void createJiraStoryGetRedirect_successful_redirectsToJira() throws Exception {
        UUID alertId = UUID.randomUUID();
        Alert alert = new Alert();
        alert.setId(alertId);
        alert.setOrganizationId(orgId);

        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));

        com.kovanlabs.notificationservice.dto.JiraStoryResponse response = new com.kovanlabs.notificationservice.dto.JiraStoryResponse("SUCCESS", "Jira story already exists", "PAY-12", "http://jira/PAY-12");
        when(jiraStoryService.createJiraStoryForAlert(alertId.toString())).thenReturn(response);

        mockMvc.perform(get("/api/notifications/alerts/" + alertId + "/jira")
                        .header("X-User-Id", "user-123")
                        .header("X-Organization-Id", orgId.toString()))
                .andExpect(status().isFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Location", "http://jira/PAY-12"));
    }

    private NotificationPreference preference(boolean enabled) {
        NotificationPreference preference = new NotificationPreference();
        preference.setUserId("user-123");
        preference.setEmailEnabled(enabled);
        preference.setCreatedAt(LocalDateTime.now());
        preference.setUpdatedAt(LocalDateTime.now());
        return preference;
    }
}