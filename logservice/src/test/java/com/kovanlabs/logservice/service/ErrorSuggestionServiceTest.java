package com.kovanlabs.logservice.service;

import com.kovanlabs.logservice.model.ErrorSuggestion;
import com.kovanlabs.logservice.model.LogEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import com.kovanlabs.logservice.repository.ElasticRepository;

class ErrorSuggestionServiceTest {

    private ErrorSuggestionService service;

    @BeforeEach
    void setUp() {
        service = new ErrorSuggestionService();
        service.init();
    }

    @Test
    void init_loadsMappingsSuccessfully() {
        assertNotNull(service.getMappings());
        assertFalse(service.getMappings().isEmpty());
    }

    @Test
    void getSuggestionForLog_exactMatch_returnsCorrectSuggestion() {
        ErrorSuggestion suggestion = service.getSuggestionForLog("NullPointerException was thrown on line 45", null);
        assertNotNull(suggestion);
        assertEquals("NullPointerException", suggestion.getErrorType());
        assertEquals("MEDIUM", suggestion.getSeverity());
        assertTrue(suggestion.getPossibleCauses().stream()
                .anyMatch(cause -> cause.contains("null object reference")));
    }

    @Test
    void getSuggestionForLog_regexOrSubstringMatch_detectsOOM() {
        ErrorSuggestion suggestion = service.getSuggestionForLog("Fatal error: java.lang.OutOfMemoryError: Java heap space", null);
        assertNotNull(suggestion);
        assertEquals("OutOfMemoryError", suggestion.getErrorType());
        assertEquals("CRITICAL", suggestion.getSeverity());
    }

    @Test
    void getSuggestionForLog_priorityCheck_resolvesNoSuchBeanBeforeBeanCreation() {
        // Both BeanCreationException and NoSuchBeanDefinitionException patterns might be matched,
        // but NoSuchBeanDefinitionException has priority 15 (higher than 10) so it must match first.
        ErrorSuggestion suggestion = service.getSuggestionForLog("Error creating bean: NoSuchBeanDefinitionException details", null);
        assertNotNull(suggestion);
        assertEquals("NoSuchBeanDefinitionException", suggestion.getErrorType());
    }

    @Test
    void getSuggestionForLog_noMatch_returnsGenericSuggestion() {
        ErrorSuggestion suggestion = service.getSuggestionForLog("Some random error message that matches nothing", null);
        assertNotNull(suggestion);
        assertEquals("UnknownException", suggestion.getErrorType());
        assertEquals("HIGH", suggestion.getSeverity());
        assertFalse(suggestion.getPossibleCauses().isEmpty());
    }

    @Test
    void attachSuggestion_updatesLogEventSuccessfully() {
        LogEvent event = new LogEvent();
        event.setLevel("ERROR");
        event.setMessage("Failed to get connection: SQLTimeoutException occurred");

        service.attachSuggestion(event);

        assertEquals("SQLTimeoutException", event.getErrorType());
        assertNotNull(event.getPossibleCauses());
        assertNotNull(event.getSuggestedFixes());
        assertEquals("HIGH", event.getSeverity());
        assertNotNull(event.getSuggestionGeneratedAt());
        assertEquals("RULE_ENGINE", event.getSuggestionSource());
        assertEquals(100, event.getConfidence());
    }

    @Test
    void attachSuggestion_knowledgeBaseHit_servesFromKB() {
        ElasticRepository mockRepo = org.mockito.Mockito.mock(ElasticRepository.class);
        GeminiAnalysisService mockGemini = org.mockito.Mockito.mock(GeminiAnalysisService.class);
        
        ErrorSuggestionService customService = new ErrorSuggestionService(mockRepo, mockGemini);
        customService.init();

        LogEvent event = new LogEvent();
        event.setLevel("ERROR");
        event.setMessage("Unknown exception message");

        com.kovanlabs.logservice.model.ErrorKnowledgeBaseEntry entry = new com.kovanlabs.logservice.model.ErrorKnowledgeBaseEntry(
                "Unknown exception message",
                "CustomException",
                "Custom root cause",
                List.of("cause"),
                List.of("fix"),
                "MEDIUM",
                85,
                "GEMINI",
                "2026-06-09T10:00:00Z"
        );

        org.mockito.Mockito.when(mockRepo.findSimilarKnowledgeBaseEntry("Unknown exception message", null))
                .thenReturn(java.util.Optional.of(entry));

        customService.attachSuggestion(event);

        assertEquals("CustomException", event.getErrorType());
        assertEquals("KNOWLEDGE_BASE", event.getSuggestionSource());
        assertEquals("Custom root cause", event.getRootCause());
        assertEquals(85, event.getConfidence());
    }

    @Test
    void attachSuggestion_geminiHit_callsGeminiAndSavesToKB() {
        ElasticRepository mockRepo = org.mockito.Mockito.mock(ElasticRepository.class);
        GeminiAnalysisService mockGemini = org.mockito.Mockito.mock(GeminiAnalysisService.class);
        
        ErrorSuggestionService customService = new ErrorSuggestionService(mockRepo, mockGemini);
        customService.init();

        LogEvent event = new LogEvent();
        event.setLevel("ERROR");
        event.setMessage("Unknown exception message");
        event.setService("my-service");

        com.kovanlabs.logservice.model.GeminiResponse response = new com.kovanlabs.logservice.model.GeminiResponse(
                "GeminiException",
                "CRITICAL",
                "Gemini root cause",
                List.of("g-cause"),
                List.of("g-fix"),
                95
        );

        org.mockito.Mockito.when(mockRepo.findSimilarKnowledgeBaseEntry(anyString(), any()))
                .thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(mockGemini.analyze(anyString(), any(), any(), any(), any()))
                .thenReturn(response);

        customService.attachSuggestion(event);

        assertEquals("GeminiException", event.getErrorType());
        assertEquals("GEMINI", event.getSuggestionSource());
        assertEquals("Gemini root cause", event.getRootCause());
        assertEquals(95, event.getConfidence());
        org.mockito.Mockito.verify(mockRepo).saveKnowledgeBaseEntry(any());
    }
}
