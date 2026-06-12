package com.kovanlabs.notificationservice.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.notificationservice.dto.UserJiraMappingRequest;
import com.kovanlabs.notificationservice.model.UserJiraMapping;
import com.kovanlabs.notificationservice.repository.UserJiraMappingRepository;
import com.kovanlabs.notificationservice.security.TenantSecurityService;

@ExtendWith(MockitoExtension.class)
class UserJiraMappingControllerTest {

    @Mock
    private UserJiraMappingRepository repository;

    @Mock
    private TenantSecurityService tenantSecurityService;

    @InjectMocks
    private UserJiraMappingController controller;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();
    private UUID mappingId;
    private UserJiraMapping mapping;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        mappingId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        mapping = new UserJiraMapping();
        mapping.setId(mappingId);
        mapping.setUserId("Arun");
        mapping.setJiraAccountId("abc123");
        mapping.setJiraDisplayName("Arun Kumar");
        mapping.setActive(true);
        mapping.setCreatedAt(LocalDateTime.now());
        mapping.setUpdatedAt(LocalDateTime.now());

        org.mockito.Mockito.lenient().when(tenantSecurityService.validateMembership(any(), any()))
                .thenReturn(orgId);
        org.mockito.Mockito.lenient().when(tenantSecurityService.validateMembershipAndRole(any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));
    }

    @Test
    void getUserMappings_returnsList() throws Exception {
        Object[] row = new Object[] { "Arun", "Arun", "payment-service", mappingId, "abc123", "Arun Kumar", true };
        when(repository.findAllUserMappingsWithServices()).thenReturn(Collections.singletonList(row));

        mockMvc.perform(get("/api/jira/user-mappings")
                        .header("X-User-Id", "Arun")
                        .header("X-Organization-Id", orgId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId", is("Arun")))
                .andExpect(jsonPath("$[0].username", is("Arun")))
                .andExpect(jsonPath("$[0].ownedServices", is("payment-service")))
                .andExpect(jsonPath("$[0].jiraAccountId", is("abc123")));
    }

    @Test
    void getUserMappings_asDev_filtersList() throws Exception {
        Object[] row = new Object[] { "Arun", "Arun", "payment-service", mappingId, "abc123", "Arun Kumar", true };
        when(repository.findAllUserMappingsWithServices()).thenReturn(Collections.singletonList(row));

        // When requesting as Arun (dev), it matches and returns
        mockMvc.perform(get("/api/jira/user-mappings")
                        .header("X-User-Id", "Arun")
<<<<<<< HEAD
                        .header("X-Organization-Id", orgId.toString())
=======
>>>>>>> 6a01b900be15a6a689e602f89925f0c54101ef47
                        .header("X-User-Role", "DEV"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId", is("Arun")));

        // When requesting as another user (dev), it filters it out. Since no service mapping, it checks database
        when(repository.findUsernameByUserId("Bob")).thenReturn(Optional.of("Bob"));
        when(repository.findByUserId("Bob")).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/jira/user-mappings")
                        .header("X-User-Id", "Bob")
<<<<<<< HEAD
                        .header("X-Organization-Id", orgId.toString())
=======
>>>>>>> 6a01b900be15a6a689e602f89925f0c54101ef47
                        .header("X-User-Role", "DEV"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId", is("Bob")))
                .andExpect(jsonPath("$[0].id").value(is((Object) null)));
    }

    @Test
    void createMapping_valid_returnsCreated() throws Exception {
        UserJiraMappingRequest req = new UserJiraMappingRequest(
                "Arun", "abc123", "Arun Kumar", true
        );

        when(repository.findByUserId("Arun")).thenReturn(Optional.empty());
        when(repository.save(any(UserJiraMapping.class))).thenReturn(mapping);

        mockMvc.perform(post("/api/jira/user-mappings")
                        .header("X-User-Id", "Arun")
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId", is("Arun")))
                .andExpect(jsonPath("$.jiraAccountId", is("abc123")));
    }

    @Test
    void createMapping_asDev_valid_returnsCreated() throws Exception {
        UserJiraMappingRequest req = new UserJiraMappingRequest(
                "Arun", "abc123", "Arun Kumar", true
        );

        when(repository.findByUserId("Arun")).thenReturn(Optional.empty());
        when(repository.save(any(UserJiraMapping.class))).thenReturn(mapping);

        mockMvc.perform(post("/api/jira/user-mappings")
                        .header("X-User-Id", "Arun")
<<<<<<< HEAD
                        .header("X-Organization-Id", orgId.toString())
=======
>>>>>>> 6a01b900be15a6a689e602f89925f0c54101ef47
                        .header("X-User-Role", "DEV")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void createMapping_asDev_invalidUser_returnsForbidden() throws Exception {
        UserJiraMappingRequest req = new UserJiraMappingRequest(
                "Bob", "abc123", "Arun Kumar", true
        );

        mockMvc.perform(post("/api/jira/user-mappings")
                        .header("X-User-Id", "Arun")
<<<<<<< HEAD
                        .header("X-Organization-Id", orgId.toString())
=======
>>>>>>> 6a01b900be15a6a689e602f89925f0c54101ef47
                        .header("X-User-Role", "DEV")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createMapping_duplicate_returnsConflict() throws Exception {
        UserJiraMappingRequest req = new UserJiraMappingRequest(
                "Arun", "abc123", "Arun Kumar", true
        );

        when(repository.findByUserId("Arun")).thenReturn(Optional.of(mapping));

        mockMvc.perform(post("/api/jira/user-mappings")
                        .header("X-User-Id", "Arun")
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateMapping_asDev_valid_returnsOk() throws Exception {
        UserJiraMappingRequest req = new UserJiraMappingRequest(
                "Arun", "abc123_updated", "Arun Kumar Updated", true
        );
<<<<<<< HEAD
=======

        when(repository.findById(mappingId)).thenReturn(Optional.of(mapping));
        when(repository.save(any(UserJiraMapping.class))).thenReturn(mapping);

        mockMvc.perform(put("/api/jira/user-mappings/{id}", mappingId)
                        .header("X-User-Id", "Arun")
                        .header("X-User-Role", "DEV")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void updateMapping_asDev_forOther_returnsForbidden() throws Exception {
        UserJiraMappingRequest req = new UserJiraMappingRequest(
                "Bob", "abc123_updated", "Arun Kumar Updated", true
        );

        when(repository.findById(mappingId)).thenReturn(Optional.of(mapping));

        // Arun (dev) trying to modify a mapping belonging to Bob, or change mapping owner to Bob
        mockMvc.perform(put("/api/jira/user-mappings/{id}", mappingId)
                        .header("X-User-Id", "Bob") // mapping belongs to Arun, logged in user is Bob
                        .header("X-User-Role", "DEV")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteMapping_exists_returns204() throws Exception {
        when(repository.findById(mappingId)).thenReturn(Optional.of(mapping));
>>>>>>> 6a01b900be15a6a689e602f89925f0c54101ef47

        when(repository.findById(mappingId)).thenReturn(Optional.of(mapping));
        when(repository.save(any(UserJiraMapping.class))).thenReturn(mapping);

        mockMvc.perform(put("/api/jira/user-mappings/{id}", mappingId)
                        .header("X-User-Id", "Arun")
                        .header("X-Organization-Id", orgId.toString())
                        .header("X-User-Role", "DEV")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void updateMapping_asDev_forOther_returnsForbidden() throws Exception {
        UserJiraMappingRequest req = new UserJiraMappingRequest(
                "Bob", "abc123_updated", "Arun Kumar Updated", true
        );

        when(repository.findById(mappingId)).thenReturn(Optional.of(mapping));

        // Arun (dev) trying to modify a mapping belonging to Bob, or change mapping owner to Bob
        mockMvc.perform(put("/api/jira/user-mappings/{id}", mappingId)
                        .header("X-User-Id", "Bob") // mapping belongs to Arun, logged in user is Bob
                        .header("X-Organization-Id", orgId.toString())
                        .header("X-User-Role", "DEV")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteMapping_exists_returns204() throws Exception {
        when(repository.findById(mappingId)).thenReturn(Optional.of(mapping));

        mockMvc.perform(delete("/api/jira/user-mappings/{id}", mappingId)
                        .header("X-User-Id", "Arun")
                        .header("X-Organization-Id", orgId.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteMapping_asDev_returnsForbiddenForOther() throws Exception {
        when(repository.findById(mappingId)).thenReturn(Optional.of(mapping));

        mockMvc.perform(delete("/api/jira/user-mappings/{id}", mappingId)
                        .header("X-User-Id", "Bob")
<<<<<<< HEAD
                        .header("X-Organization-Id", orgId.toString())
=======
>>>>>>> 6a01b900be15a6a689e602f89925f0c54101ef47
                        .header("X-User-Role", "DEV"))
                .andExpect(status().isForbidden());
    }
}
