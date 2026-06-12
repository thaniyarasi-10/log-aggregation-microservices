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

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-1.5-flash}")
    private String modelName;

    public GeminiAnalysisService() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(requestFactory);
        this.objectMapper = new ObjectMapper();
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
            requestBody.put("generationConfig", generationConfig);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            LOGGER.info("Calling Gemini API to analyze log from service: {}", service);
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
            }

        } catch (Exception e) {
            LOGGER.error("Failed to perform Gemini AI analysis: {}", e.getMessage());
        }

        return null;
    }
}
