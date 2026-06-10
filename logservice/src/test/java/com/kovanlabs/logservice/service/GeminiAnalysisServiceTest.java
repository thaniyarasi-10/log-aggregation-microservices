package com.kovanlabs.logservice.service;

import com.kovanlabs.logservice.model.GeminiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class GeminiAnalysisServiceTest {

    @InjectMocks
    private GeminiAnalysisService service;

    @Mock
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(service, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(service, "modelName", "gemini-1.5-flash");
    }

    @Test
    void analyze_successfulResponse_returnsParsedResponse() {
        String responseBody = "{\n" +
                "  \"candidates\": [\n" +
                "    {\n" +
                "      \"content\": {\n" +
                "        \"parts\": [\n" +
                "          {\n" +
                "            \"text\": \"{\\n  \\\"errorType\\\": \\\"TestException\\\",\\n  \\\"severity\\\": \\\"HIGH\\\",\\n  \\\"rootCause\\\": \\\"Test root cause\\\",\\n  \\\"possibleCauses\\\": [\\\"cause1\\\"],\\n  \\\"suggestedFixes\\\": [\\\"fix1\\\"],\\n  \\\"confidence\\\": 90\\n}\"\n" +
                "          }\n" +
                "        ]\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        GeminiResponse result = service.analyze("Test message", null, "test-service", "ERROR", "2026-06-09T10:00:00Z");

        assertNotNull(result);
        assertEquals("TestException", result.getErrorType());
        assertEquals("HIGH", result.getSeverity());
        assertEquals("Test root cause", result.getRootCause());
        assertEquals(List.of("cause1"), result.getPossibleCauses());
        assertEquals(List.of("fix1"), result.getSuggestedFixes());
        assertEquals(90, result.getConfidence());
    }

    @Test
    void analyze_noApiKey_returnsNull() {
        ReflectionTestUtils.setField(service, "apiKey", "");
        GeminiResponse result = service.analyze("Test message", null, "test-service", "ERROR", "2026-06-09T10:00:00Z");
        assertNull(result);
    }
}
