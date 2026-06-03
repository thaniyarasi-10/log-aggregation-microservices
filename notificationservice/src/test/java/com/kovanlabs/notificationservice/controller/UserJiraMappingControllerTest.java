package com.kovanlabs.notificationservice.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.kovanlabs.notificationservice.dto.UserJiraMappingRequest;
import com.kovanlabs.notificationservice.model.UserJiraMapping;
import com.kovanlabs.notificationservice.repository.UserJiraMappingRepository;

@ExtendWith(MockitoExtension.class)
class UserJiraMappingControllerTest {

    @Mock
    private UserJiraMappingRepository repository;

    @InjectMocks
    private UserJiraMappingController controller;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();
    private UUID mappingId;
    private UserJiraMapping mapping;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        mappingId = UUID.randomUUID();
        mapping = new UserJiraMapping();
        mapping.setId(mappingId);
        mapping.setUserId("Arun");
        mapping.setJiraAccountId("abc123");
        mapping.setJiraDisplayName("Arun Kumar");
        mapping.setActive(true);
        mapping.setCreatedAt(LocalDateTime.now());
        mapping.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void getUserMappings_returnsList() throws Exception {
        Object[] row = new Object[] { "Arun", "Arun", "payment-service", mappingId, "abc123", "Arun Kumar", true };
        when(repository.findAllUserMappingsWithServices()).thenReturn(Collections.singletonList(row));

        mockMvc.perform(get("/api/jira/user-mappings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId", is("Arun")))
                .andExpect(jsonPath("$[0].username", is("Arun")))
                .andExpect(jsonPath("$[0].ownedServices", is("payment-service")))
                .andExpect(jsonPath("$[0].jiraAccountId", is("abc123")));
    }

    @Test
    void createMapping_valid_returnsCreated() throws Exception {
        UserJiraMappingRequest req = new UserJiraMappingRequest(
                "Arun", "abc123", "Arun Kumar", true
        );

        when(repository.findByUserId("Arun")).thenReturn(Optional.empty());
        when(repository.save(any(UserJiraMapping.class))).thenReturn(mapping);

        mockMvc.perform(post("/api/jira/user-mappings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId", is("Arun")))
                .andExpect(jsonPath("$.jiraAccountId", is("abc123")));
    }

    @Test
    void createMapping_duplicate_returnsConflict() throws Exception {
        UserJiraMappingRequest req = new UserJiraMappingRequest(
                "Arun", "abc123", "Arun Kumar", true
        );

        when(repository.findByUserId("Arun")).thenReturn(Optional.of(mapping));

        mockMvc.perform(post("/api/jira/user-mappings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteMapping_exists_returns204() throws Exception {
        when(repository.existsById(mappingId)).thenReturn(true);

        mockMvc.perform(delete("/api/jira/user-mappings/{id}", mappingId))
                .andExpect(status().isNoContent());
    }
}
