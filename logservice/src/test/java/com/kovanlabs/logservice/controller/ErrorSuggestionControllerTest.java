package com.kovanlabs.logservice.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kovanlabs.logservice.model.LogEvent;
import com.kovanlabs.logservice.repository.ElasticRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class ErrorSuggestionControllerTest {

    @Mock
    private ElasticRepository elasticRepository;

    @InjectMocks
    private ErrorSuggestionController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private LogEvent errorLog(String service, String errorType) {
        LogEvent event = new LogEvent();
        event.setService(service);
        event.setLevel("ERROR");
        event.setMessage("An error occurred");
        event.setErrorType(errorType);
        event.setSeverity("HIGH");
        event.setTimestamp("2026-06-09T10:00:00Z");
        event.setPossibleCauses(List.of("cause 1"));
        event.setSuggestedFixes(List.of("fix 1"));
        return event;
    }

    @Test
    void getErrors_returnsList() throws Exception {
        when(elasticRepository.searchErrors(any(), any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(List.of(errorLog("auth-service", "NullPointerException")));

        mockMvc.perform(get("/api/logs/errors")
                        .param("service", "auth-service")
                        .param("errorType", "NullPointerException")
                        .header("X-User-Email", "test@test.com")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].service").value("auth-service"))
                .andExpect(jsonPath("$[0].errorType").value("NullPointerException"));
    }

    @Test
    void getErrorById_found_returnsLog() throws Exception {
        LogEvent event = errorLog("auth-service", "NullPointerException");
        when(elasticRepository.findById(eq("some-id"), any()))
                .thenReturn(Optional.of(event));

        mockMvc.perform(get("/api/logs/errors/some-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("auth-service"))
                .andExpect(jsonPath("$.errorType").value("NullPointerException"));
    }

    @Test
    void getErrorById_notFound_returns404() throws Exception {
        when(elasticRepository.findById(eq("some-id"), any()))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/logs/errors/some-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTopPatterns_returnsList() throws Exception {
        Map<String, Object> mockStats = Map.of(
                "topRecurringErrors", List.of(
                        Map.of("errorType", "NullPointerException", "count", 10L),
                        Map.of("errorType", "OutOfMemoryError", "count", 5L)
                )
        );
        when(elasticRepository.getErrorStats(any()))
                .thenReturn(mockStats);

        mockMvc.perform(get("/api/logs/errors/top-patterns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].pattern").value("NullPointerException"))
                .andExpect(jsonPath("$[0].count").value(10))
                .andExpect(jsonPath("$[1].pattern").value("OutOfMemoryError"))
                .andExpect(jsonPath("$[1].count").value(5));
    }

    @Test
    void getErrorsByErrorType_returnsFilteredList() throws Exception {
        when(elasticRepository.searchErrors(any(), eq("NullPointerException"), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(List.of(errorLog("auth-service", "NullPointerException")));

        mockMvc.perform(get("/api/logs/errors/by-error-type/NullPointerException"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].errorType").value("NullPointerException"));
    }

    @Test
    void getStats_returnsConsolidatedStats() throws Exception {
        Map<String, Object> mockStats = Map.of(
                "occurrencesPerErrorType", Map.of("NullPointerException", 10L),
                "topRecurringErrors", List.of(Map.of("errorType", "NullPointerException", "count", 10L)),
                "mostAffectedServices", Map.of("auth-service", 10L)
        );
        when(elasticRepository.getErrorStats(any()))
                .thenReturn(mockStats);

        mockMvc.perform(get("/api/logs/errors/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occurrencesPerErrorType.NullPointerException").value(10))
                .andExpect(jsonPath("$.topRecurringErrors", hasSize(1)))
                .andExpect(jsonPath("$.mostAffectedServices.auth-service").value(10));
    }

    @Test
    void getAiStats_returnsAiStats() throws Exception {
        Map<String, Object> mockAiStats = Map.of(
                "mostCommonAiErrors", Map.of("NullPointerException", 5L),
                "mostReusedKnowledgeBaseEntries", Map.of("OutOfMemoryError", 3L),
                "geminiCallsSaved", 15L,
                "topRootCauses", Map.of("Null reference", 5L)
        );
        when(elasticRepository.getAiStats(any()))
                .thenReturn(mockAiStats);

        mockMvc.perform(get("/api/logs/errors/ai-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.geminiCallsSaved").value(15))
                .andExpect(jsonPath("$.mostCommonAiErrors.NullPointerException").value(5))
                .andExpect(jsonPath("$.mostReusedKnowledgeBaseEntries.OutOfMemoryError").value(3))
                .andExpect(jsonPath("$.topRootCauses.['Null reference']").value(5));
    }
}
