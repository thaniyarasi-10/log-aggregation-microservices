package com.kovanlabs.notificationservice.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.notificationservice.dto.AlertNotificationRequest;
import com.kovanlabs.notificationservice.dto.NotificationPreferenceRequest;
import com.kovanlabs.notificationservice.model.NotificationPreference;
import com.kovanlabs.notificationservice.model.Alert;
import com.kovanlabs.notificationservice.repository.AlertRepository;
import com.kovanlabs.notificationservice.service.AlertNotificationService;
import com.kovanlabs.notificationservice.service.NotificationPreferenceService;
import com.kovanlabs.notificationservice.service.JiraStoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock private NotificationPreferenceService preferenceService;
    @Mock private AlertNotificationService alertNotificationService;
    @Mock private AlertRepository alertRepository;
    @Mock private JiraStoryService jiraStoryService;

    @InjectMocks private NotificationController controller;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
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
        org.mockito.Mockito.lenient().when(alertNotificationService.sendAlert(any(AlertNotificationRequest.class))).thenReturn(true);

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

        when(alertRepository.findAll()).thenReturn(List.of(alert1));

        mockMvc.perform(get("/api/notifications/alerts"))
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

        Alert alert2 = new Alert();
        alert2.setService("auth-service");
        alert2.setMessage("Unauthorized attempt");
        alert2.setCount(2);
        alert2.setSeverity("WARNING");
        alert2.setTimestamp(LocalDateTime.now());

        when(alertRepository.findAll()).thenReturn(List.of(alert1, alert2));

        mockMvc.perform(get("/api/notifications/alerts")
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

        when(alertRepository.findAll()).thenReturn(List.of(alert1));

        mockMvc.perform(get("/api/notifications/alerts")
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

        Alert alert2 = new Alert();
        alert2.setService("auth-service");
        alert2.setMessage("Unauthorized attempt");
        alert2.setCount(2);
        alert2.setSeverity("WARNING");
        alert2.setTimestamp(LocalDateTime.now());

        when(alertRepository.findAll()).thenReturn(List.of(alert1, alert2));

        mockMvc.perform(get("/api/notifications/alerts")
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Services", "payment-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['payment-service'][0].message", equalTo("DB timeout")))
                .andExpect(jsonPath("$.['auth-service'][0].message", equalTo("Unauthorized attempt")));
    }

    @Test
    void createJiraStoryPost_returnsDetails() throws Exception {
        com.kovanlabs.notificationservice.dto.JiraStoryResponse response = new com.kovanlabs.notificationservice.dto.JiraStoryResponse("CREATED", "Created successfully", "PAY-12", "http://jira/PAY-12");
        when(jiraStoryService.createJiraStoryForAlert("alert-123")).thenReturn(response);

        mockMvc.perform(post("/api/notifications/alerts/alert-123/jira"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("CREATED")))
                .andExpect(jsonPath("$.jiraIssueKey", equalTo("PAY-12")));
    }

    @Test
    void createJiraStoryGetRedirect_successful_redirectsToJira() throws Exception {
        com.kovanlabs.notificationservice.dto.JiraStoryResponse response = new com.kovanlabs.notificationservice.dto.JiraStoryResponse("SUCCESS", "Jira story already exists", "PAY-12", "http://jira/PAY-12");
        when(jiraStoryService.createJiraStoryForAlert("alert-123")).thenReturn(response);

        mockMvc.perform(get("/api/notifications/alerts/alert-123/jira"))
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