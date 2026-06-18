package com.kovanlabs.logservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.logservice.model.GeminiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to execute calls to the Gemini REST API for root cause analysis.
 */
@Service
public class GeminiAnalysisService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiAnalysisService.class);

    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.model:gemini-2.5-flash}")
    private String modelName;

    public GeminiAnalysisService(io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10000);
        requestFactory.setReadTimeout(30000); // 30 seconds to support potential latency
        this.restTemplate = new RestTemplate(requestFactory);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);
        this.meterRegistry = meterRegistry;
    }

    /**
     * Sends the log context to Gemini for structured analysis.
     *
     * @param message      the log message
     * @param errorDetails the stack trace / error details
     * @param service      the microservice name
     * @param level        the log level
     * @param timestamp    the log timestamp
     * @return the parsed GeminiResponse, or null if analysis failed
     */
    public GeminiResponse analyze(String message, String errorDetails, String service, String level, String timestamp) {
        if (apiKey == null || apiKey.isBlank()) {
            LOGGER.warn("Gemini API key is not configured. Skipping AI analysis.");
            return null;
        }

        int maxRetries = 3;
        int delayMs = 1000;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;

                String truncatedDetails = errorDetails;
                if (truncatedDetails != null && truncatedDetails.length() > 4000) {
                    truncatedDetails = truncatedDetails.substring(0, 4000) + "... [truncated]";
                }

                String prompt = String.format(
                        "You are an expert backend system troubleshooting assistant. " +
                        "Analyze the following error log event from a microservices system:\n\n" +
                        "Service Name: %s\n" +
                        "Log Level: %s\n" +
                        "Timestamp: %s\n" +
                        "Message: %s\n" +
                        "Stack Trace / Details: %s\n\n" +
                        "Return a JSON object conforming exactly to this schema:\n" +
                        "{\n" +
                        "  \"errorType\": \"A short classification of the error (e.g., DatabaseConnectionTimeoutException)\",\n" +
                        "  \"severity\": \"LOW, MEDIUM, HIGH, or CRITICAL\",\n" +
                        "  \"rootCause\": \"A clear, one-sentence description of the root cause\",\n" +
                        "  \"possibleCauses\": [\"cause 1\", \"cause 2\", ...],\n" +
                        "  \"suggestedFixes\": [\"fix 1\", \"fix 2\", ...],\n" +
                        "  \"confidence\": 95\n" +
                        "}\n" +
                        "Determine the confidence score (integer between 0 and 100) based on how identifiable the root cause is.\n" +
                        "Do NOT wrap the response in markdown blocks or backticks, return raw JSON only.",
                        service, level, timestamp, message, truncatedDetails != null ? truncatedDetails : ""
                );

                Map<String, Object> requestBody = new HashMap<>();
                Map<String, Object> part = new HashMap<>();
                part.put("text", prompt);
                Map<String, Object> content = new HashMap<>();
                content.put("parts", List.of(part));
                requestBody.put("contents", List.of(content));

                Map<String, Object> generationConfig = new HashMap<>();
                generationConfig.put("responseMimeType", "application/json");

                Map<String, Object> analyzeSchema = Map.of(
                        "type", "OBJECT",
                        "properties", Map.of(
                                "errorType", Map.of("type", "STRING"),
                                "severity", Map.of("type", "STRING"),
                                "rootCause", Map.of("type", "STRING"),
                                "possibleCauses", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")),
                                "suggestedFixes", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")),
                                "confidence", Map.of("type", "INTEGER")
                        ),
                        "required", List.of("errorType", "severity", "rootCause", "possibleCauses", "suggestedFixes", "confidence")
                );
                generationConfig.put("responseSchema", analyzeSchema);
                
                // Disable thinking/reasoning budget to speed up responses and save tokens
                Map<String, Object> thinkingConfig = new HashMap<>();
                thinkingConfig.put("thinkingBudget", 0);
                generationConfig.put("thinkingConfig", thinkingConfig);
                
                requestBody.put("generationConfig", generationConfig);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                LOGGER.info("Calling Gemini API to analyze log from service: {} (Attempt {} of {})", service, attempt, maxRetries);
                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode candidates = root.path("candidates");
                    if (candidates.isArray() && !candidates.isEmpty()) {
                        JsonNode firstCandidate = candidates.get(0);
                        JsonNode contentNode = firstCandidate.path("content");
                        JsonNode parts = contentNode.path("parts");
                        if (parts.isArray() && !parts.isEmpty()) {
                            String jsonText = parts.get(0).path("text").asText().trim();
                            if (jsonText.startsWith("```")) {
                                jsonText = jsonText.replaceAll("```json", "").replaceAll("```", "").trim();
                            }
                            return objectMapper.readValue(jsonText, GeminiResponse.class);
                        }
                    }
                } else {
                    LOGGER.error("Gemini API call returned non-2xx status: {}", response.getStatusCode());
                    throw new java.io.IOException("Gemini API call returned non-2xx status: " + response.getStatusCode());
                }

            } catch (Exception e) {
                lastException = e;
                LOGGER.warn("Gemini API call failed in analyze on attempt {}: {}", attempt, e.getMessage());

                boolean isReadTimeout = false;
                if (e instanceof org.springframework.web.client.ResourceAccessException) {
                    Throwable cause = e.getCause();
                    if (cause instanceof java.net.SocketTimeoutException && 
                        cause.getMessage() != null && 
                        cause.getMessage().contains("Read timed out")) {
                        isReadTimeout = true;
                    }
                }

                // Track timeout metrics under gemini.api.timeouts
                if (e instanceof org.springframework.web.client.ResourceAccessException || 
                    (e.getCause() != null && e.getCause() instanceof java.net.SocketTimeoutException) ||
                    e.getMessage().contains("timed out") || e.getMessage().contains("Timeout")) {
                    if (meterRegistry != null) {
                        try {
                            meterRegistry.counter("gemini.api.timeouts", "service", service, "error", e.getClass().getSimpleName()).increment();
                        } catch (Exception me) {
                            LOGGER.warn("Failed to increment Gemini timeout metric: {}", me.getMessage());
                        }
                    }
                }

                if (e instanceof org.springframework.web.client.HttpStatusCodeException se) {
                    org.springframework.http.HttpStatusCode status = se.getStatusCode();
                    if (status.value() == 429 || status.is4xxClientError()) {
                        LOGGER.error("Gemini API returned non-retryable status {} in analyze. Aborting retries.", status);
                        break;
                    }
                }
                if (isReadTimeout) {
                    LOGGER.warn("Gemini API call read timed out in analyze. Skipping further retries.");
                    break;
                }

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(delayMs * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        LOGGER.error("Gemini API analysis exhausted all {} retries. Last error: {}", maxRetries, lastException != null ? lastException.getMessage() : "unknown");
        return null;
    }

    /**
     * Sends a generic prompt to the Gemini API, optionally requiring structured JSON response.
     * Includes transient error retries and timeout protection.
     *
     * @param prompt    the text prompt to send
     * @param jsonMode  true if the response should be formatted as application/json
     * @return the raw text or JSON response string, or null if generation failed
     */
    public String generateContent(String prompt, boolean jsonMode) {
        return generateContent(prompt, jsonMode, null);
    }

    /**
     * Sends a generic prompt to the Gemini API, optionally requiring structured JSON response with a schema.
     * Includes transient error retries and timeout protection.
     *
     * @param prompt          the text prompt to send
     * @param jsonMode        true if the response should be formatted as application/json
     * @param responseSchema  optional structured JSON schema to enforce on the response
     * @return the raw text or JSON response string, or null if generation failed
     */
    public String generateContent(String prompt, boolean jsonMode, Map<String, Object> responseSchema) {
        if (apiKey == null || apiKey.isBlank()) {
            LOGGER.warn("Gemini API key is not configured. Skipping content generation.");
            return null;
        }

        int maxRetries = 3;
        int delayMs = 1000;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;

                Map<String, Object> requestBody = new HashMap<>();
                Map<String, Object> part = new HashMap<>();
                part.put("text", prompt);
                Map<String, Object> content = new HashMap<>();
                content.put("parts", List.of(part));
                requestBody.put("contents", List.of(content));

                Map<String, Object> generationConfig = new HashMap<>();
                if (jsonMode) {
                    generationConfig.put("responseMimeType", "application/json");
                    if (responseSchema != null) {
                        generationConfig.put("responseSchema", responseSchema);
                    }
                }
                
                Map<String, Object> thinkingConfig = new HashMap<>();
                thinkingConfig.put("thinkingBudget", 0);
                generationConfig.put("thinkingConfig", thinkingConfig);
                
                requestBody.put("generationConfig", generationConfig);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                LOGGER.info("Calling Gemini API via generateContent. Attempt {} of {}", attempt, maxRetries);
                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode candidates = root.path("candidates");
                    if (candidates.isArray() && !candidates.isEmpty()) {
                        JsonNode firstCandidate = candidates.get(0);
                        JsonNode contentNode = firstCandidate.path("content");
                        JsonNode parts = contentNode.path("parts");
                        if (parts.isArray() && !parts.isEmpty()) {
                            String jsonText = parts.get(0).path("text").asText().trim();
                            if (jsonText.startsWith("```")) {
                                jsonText = jsonText.replaceAll("```json", "").replaceAll("```", "").trim();
                            }
                            return jsonText;
                        }
                    }
                }
                throw new java.io.IOException("Gemini returned unsuccessful status: " + response.getStatusCode());
            } catch (Exception e) {
                lastException = e;
                LOGGER.warn("Gemini API call failed in generateContent on attempt {}: {}", attempt, e.getMessage());

                boolean isReadTimeout = false;
                if (e instanceof org.springframework.web.client.ResourceAccessException) {
                    Throwable cause = e.getCause();
                    if (cause instanceof java.net.SocketTimeoutException && 
                        cause.getMessage() != null && 
                        cause.getMessage().contains("Read timed out")) {
                        isReadTimeout = true;
                    }
                }

                if (e instanceof org.springframework.web.client.HttpStatusCodeException se) {
                    org.springframework.http.HttpStatusCode status = se.getStatusCode();
                    if (status.value() == 429 || status.is4xxClientError()) {
                        LOGGER.error("Gemini API returned non-retryable status {} in generateContent. Aborting retries.", status);
                        break;
                    }
                }
                if (isReadTimeout) {
                    LOGGER.warn("Gemini API call read timed out in generateContent. Skipping further retries.");
                    break;
                }

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(delayMs * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        LOGGER.error("Gemini API content generation exhausted all {} retries. Last error: {}", maxRetries, lastException != null ? lastException.getMessage() : "unknown");
        return null;
    }
}
