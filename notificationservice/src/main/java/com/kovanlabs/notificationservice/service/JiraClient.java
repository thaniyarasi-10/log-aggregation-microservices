package com.kovanlabs.notificationservice.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.kovanlabs.notificationservice.dto.JiraUserDto;

@Component
public class JiraClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(JiraClient.class);

    public JiraCreateIssueResponse createStory(
            String baseUrl,
            String apiToken,
            String email,
            String projectKey,
            String summary,
            String description,
            String assigneeAccountId,
            String dueDate) {

        String cleanBaseUrl = baseUrl.trim();
        if (cleanBaseUrl.endsWith("/")) {
            cleanBaseUrl = cleanBaseUrl.substring(0, cleanBaseUrl.length() - 1);
        }

        // Construct Auth Header
        String authStr = email.trim() + ":" + apiToken.trim();
        String base64Auth = Base64.getEncoder().encodeToString(authStr.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + base64Auth;

        // Convert description string to ADF (Atlassian Document Format) for v3 API
        Map<String, Object> adfDescription = convertToAdf(description);

        // Construct Request Body
        Map<String, Object> fields = new HashMap<>();
        fields.put("project", Map.of("key", projectKey.trim()));
        fields.put("summary", summary);
        fields.put("description", adfDescription);
        fields.put("issuetype", Map.of("name", "Story"));

        if (assigneeAccountId != null && !assigneeAccountId.isBlank()) {
            fields.put("assignee", Map.of("accountId", assigneeAccountId.trim()));
        }

        if (dueDate != null && !dueDate.isBlank()) {
            fields.put("duedate", dueDate.trim());
        }

        Map<String, Object> requestBody = Map.of("fields", fields);

        LOGGER.info("Sending issue creation request to Jira v3 API: {}/rest/api/3/issue", cleanBaseUrl);

        RestClient restClient = getRestClient(cleanBaseUrl);

        return restClient.post()
                .uri("/rest/api/3/issue")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(JiraCreateIssueResponse.class);
    }

    public void testConnection(String baseUrl, String apiToken, String email, String projectKey) {
        String cleanBaseUrl = baseUrl.trim();
        if (cleanBaseUrl.endsWith("/")) {
            cleanBaseUrl = cleanBaseUrl.substring(0, cleanBaseUrl.length() - 1);
        }

        String authStr = email.trim() + ":" + apiToken.trim();
        String base64Auth = Base64.getEncoder().encodeToString(authStr.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + base64Auth;

        LOGGER.info("Testing connection to Jira project '{}' at: {}", projectKey, cleanBaseUrl);

        RestClient restClient = getRestClient(cleanBaseUrl);

        try {
            // Attempt to retrieve project details to validate credentials and project existence
            restClient.get()
                    .uri("/rest/api/3/project/{projectKey}", projectKey.trim())
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                throw new IllegalArgumentException("Invalid credentials. Please verify Jira Email and API Token.");
            } else if (ex.getStatusCode().value() == 404) {
                throw new IllegalArgumentException("Project with key '" + projectKey + "' does not exist.");
            } else {
                throw new IllegalArgumentException("Jira returned error status " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString());
            }
        } catch (ResourceAccessException ex) {
            throw new IllegalArgumentException("Jira is unreachable at the provided URL. Please check host connectivity.");
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to test connection: " + ex.getMessage(), ex);
        }
    }

    public List<JiraUserDto> searchAssignableUsers(String baseUrl, String apiToken, String email, String projectKey, String query) {
        String cleanBaseUrl = baseUrl.trim();
        if (cleanBaseUrl.endsWith("/")) {
            cleanBaseUrl = cleanBaseUrl.substring(0, cleanBaseUrl.length() - 1);
        }

        String authStr = email.trim() + ":" + apiToken.trim();
        String base64Auth = Base64.getEncoder().encodeToString(authStr.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + base64Auth;

        RestClient restClient = getRestClient(cleanBaseUrl);

        try {
            ParameterizedTypeReference<List<Map<String, Object>>> typeRef = new ParameterizedTypeReference<>() {};
            List<Map<String, Object>> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/rest/api/3/user/assignable/search")
                            .queryParam("project", projectKey.trim())
                            .queryParam("query", query != null ? query.trim() : "")
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .retrieve()
                    .body(typeRef);

            List<JiraUserDto> users = new ArrayList<>();
            if (response != null) {
                for (Map<String, Object> item : response) {
                    String accountId = (String) item.get("accountId");
                    String displayName = (String) item.get("displayName");
                    if (accountId != null && displayName != null) {
                        users.add(new JiraUserDto(accountId, displayName));
                    }
                }
            }
            return users;
        } catch (Exception ex) {
            LOGGER.error("Failed to fetch assignable Jira users: {}", ex.getMessage(), ex);
            throw new IllegalArgumentException("Failed to fetch assignable Jira users: " + ex.getMessage(), ex);
        }
    }

    private Map<String, Object> convertToAdf(String description) {
        if (description == null || description.isBlank()) {
            return Map.of(
                    "type", "doc",
                    "version", 1,
                    "content", List.of()
            );
        }

        List<Map<String, Object>> contentList = new ArrayList<>();
        String[] lines = description.split("\r?\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                contentList.add(Map.of(
                        "type", "heading",
                        "attrs", Map.of("level", 1),
                        "content", List.of(Map.of("type", "text", "text", trimmed.substring(2)))
                ));
            } else if (!trimmed.isEmpty()) {
                contentList.add(Map.of(
                        "type", "paragraph",
                        "content", List.of(Map.of("type", "text", "text", line))
                ));
            }
        }

        return Map.of(
                "type", "doc",
                "version", 1,
                "content", contentList
        );
    }

    private RestClient getRestClient(String cleanBaseUrl) {
        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(java.time.Duration.ofMillis(1000));
        requestFactory.setReadTimeout(java.time.Duration.ofMillis(3000));

        return RestClient.builder()
                .baseUrl(cleanBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public record JiraCreateIssueResponse(
            String id,
            String key,
            String self
    ) {}
}
