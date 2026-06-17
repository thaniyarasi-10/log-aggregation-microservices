package com.kovanlabs.notificationservice.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.notificationservice.dto.JiraConfigurationRequest;
import com.kovanlabs.notificationservice.dto.JiraUserDto;
import com.kovanlabs.notificationservice.model.JiraConfiguration;
import com.kovanlabs.notificationservice.repository.JiraConfigurationRepository;
import com.kovanlabs.notificationservice.service.JiraClient;
import com.kovanlabs.notificationservice.service.JiraFailureCache;

@ExtendWith(MockitoExtension.class)
class JiraConfigurationControllerTest {

    @Mock
    private JiraConfigurationRepository repository;

    @Mock
    private JiraClient jiraClient;

    @Mock
    private JiraFailureCache failureCache;

    @InjectMocks
    private JiraConfigurationController controller;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();
    private UUID configId;
    private JiraConfiguration config;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        configId = UUID.randomUUID();
        config = new JiraConfiguration();
        config.setId(configId);
        config.setJiraBaseUrl("https://company.atlassian.net");
        config.setJiraEmail("arun@company.com");
        config.setJiraApiToken("fake-token-1234");
        config.setJiraProjectKey("PAY");
        config.setActive(true);
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void getConfiguration_found_returnsOk() throws Exception {
        when(repository.findFirstByActiveTrue()).thenReturn(Optional.of(config));

        mockMvc.perform(get("/api/jira/configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jiraBaseUrl", is("https://company.atlassian.net")))
                .andExpect(jsonPath("$.jiraEmail", is("arun@company.com")))
                .andExpect(jsonPath("$.jiraApiToken", is("********1234")))
                .andExpect(jsonPath("$.jiraProjectKey", is("PAY")));
    }

    @Test
    void getConfiguration_empty_returnsNoContent() throws Exception {
        when(repository.findFirstByActiveTrue()).thenReturn(Optional.empty());
        when(repository.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/jira/configuration"))
                .andExpect(status().isNoContent());
    }

    @Test
    void createConfiguration_valid_returnsCreated() throws Exception {
        JiraConfigurationRequest req = new JiraConfigurationRequest(
                "https://company.atlassian.net", "arun@company.com", "fake-token-1234", "PAY", true
        );

        when(repository.save(any(JiraConfiguration.class))).thenReturn(config);

        mockMvc.perform(post("/api/jira/configuration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jiraBaseUrl", is("https://company.atlassian.net")))
                .andExpect(jsonPath("$.jiraEmail", is("arun@company.com")));
    }

    @Test
    void updateConfiguration_valid_returnsOk() throws Exception {
        JiraConfigurationRequest req = new JiraConfigurationRequest(
                "https://new-company.atlassian.net", null, null, null, null
        );

        when(repository.findFirstByActiveTrue()).thenReturn(Optional.of(config));
        when(repository.save(any(JiraConfiguration.class))).thenReturn(config);

        mockMvc.perform(put("/api/jira/configuration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void testConnection_success_returnsOk() throws Exception {
        JiraConfigurationRequest req = new JiraConfigurationRequest(
                "https://company.atlassian.net", "arun@company.com", "fake-token-1234", "PAY", true
        );

        doNothing().when(jiraClient).testConnection(anyString(), anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/jira/test-connection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Connection test succeeded. Configuration is valid.")));
    }

    @Test
    void testConnection_failure_returnsBadRequest() throws Exception {
        JiraConfigurationRequest req = new JiraConfigurationRequest(
                "https://company.atlassian.net", "arun@company.com", "fake-token-1234", "PAY", true
        );

        doThrow(new IllegalArgumentException("Project with key 'PAY' does not exist."))
                .when(jiraClient).testConnection(anyString(), anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/jira/test-connection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Project with key 'PAY' does not exist.")));
    }

    @Test
    void getJiraUsers_success_returnsList() throws Exception {
        when(repository.findFirstByActiveTrue()).thenReturn(Optional.of(config));
        when(jiraClient.searchAssignableUsers(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Collections.singletonList(new JiraUserDto("acc123", "Arun Kumar")));

        mockMvc.perform(get("/api/jira/users").param("query", "Arun"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].accountId", is("acc123")))
                .andExpect(jsonPath("$[0].displayName", is("Arun Kumar")));
    }

    @Test
    void createConfiguration_asDev_returnsForbidden() throws Exception {
        JiraConfigurationRequest req = new JiraConfigurationRequest(
                "https://company.atlassian.net", "arun@company.com", "fake-token-1234", "PAY", true
        );

        mockMvc.perform(post("/api/jira/configuration")
                        .header("X-User-Role", "DEV")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateConfiguration_asDev_returnsForbidden() throws Exception {
        JiraConfigurationRequest req = new JiraConfigurationRequest(
                "https://new-company.atlassian.net", null, null, null, null
        );

        mockMvc.perform(put("/api/jira/configuration")
                        .header("X-User-Role", "DEV")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testConnection_asDev_returnsForbidden() throws Exception {
        JiraConfigurationRequest req = new JiraConfigurationRequest(
                "https://company.atlassian.net", "arun@company.com", "fake-token-1234", "PAY", true
        );

        mockMvc.perform(post("/api/jira/test-connection")
                        .header("X-User-Role", "DEV")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
