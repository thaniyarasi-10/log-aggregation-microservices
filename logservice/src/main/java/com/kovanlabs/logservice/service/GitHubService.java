package com.kovanlabs.logservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.logservice.config.GitHubProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class GitHubService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitHubService.class);

    private final GitHubProperties gitHubProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GitHubService(GitHubProperties gitHubProperties) {
        this.gitHubProperties = gitHubProperties;
        
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Checks whether GitHub integration has been configured with required properties.
     */
    public boolean isConfigured() {
        return gitHubProperties.getToken() != null && !gitHubProperties.getToken().isBlank()
                && gitHubProperties.getRepoOwner() != null && !gitHubProperties.getRepoOwner().isBlank()
                && gitHubProperties.getRepoName() != null && !gitHubProperties.getRepoName().isBlank();
    }

    /**
     * Resolves the active Git branch dynamically by reading the local .git/HEAD file.
     * Falls back to the configured branch from properties if git is not available or HEAD is detached.
     */
    public String resolveActiveBranch() {
        try {
            File dir = new File(".").getAbsoluteFile();
            for (int i = 0; i < 8; i++) {
                if (dir == null) break;
                
                // Check direct .git directory
                File gitDir = new File(dir, ".git");
                if (gitDir.isDirectory()) {
                    File headFile = new File(gitDir, "HEAD");
                    if (headFile.isFile()) {
                        String content = Files.readString(headFile.toPath(), StandardCharsets.UTF_8).trim();
                        if (content.startsWith("ref: refs/heads/")) {
                            String branch = content.substring("ref: refs/heads/".length()).trim();
                            if (!branch.isEmpty()) {
                                LOGGER.info("Dynamically resolved active Git branch: {}", branch);
                                return branch;
                            }
                        }
                    }
                }
                
                // Check nested log-aggregation-microservices directory (e.g. in development monorepo workspace)
                File nestedRoot = new File(dir, "log-aggregation-microservices");
                if (nestedRoot.isDirectory()) {
                    File nestedGit = new File(nestedRoot, ".git");
                    if (nestedGit.isDirectory()) {
                        File headFile = new File(nestedGit, "HEAD");
                        if (headFile.isFile()) {
                            String content = Files.readString(headFile.toPath(), StandardCharsets.UTF_8).trim();
                            if (content.startsWith("ref: refs/heads/")) {
                                String branch = content.substring("ref: refs/heads/".length()).trim();
                                if (!branch.isEmpty()) {
                                    LOGGER.info("Dynamically resolved active Git branch from subfolder: {}", branch);
                                    return branch;
                                }
                            }
                        }
                    }
                }
                
                dir = dir.getParentFile();
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to dynamically resolve Git branch, falling back to configured: {}", e.getMessage());
        }

        // Fallback to configured branch name
        return gitHubProperties.getBranch() != null ? gitHubProperties.getBranch().trim() : "main";
    }

    /**
     * Helper class to hold fetched file details from GitHub.
     */
    public static class GitHubFileDetails {
        public String content;
        public String sha;
    }

    /**
     * Fetches file details (decoded content and commit sha) from GitHub.
     */
    public GitHubFileDetails fetchFile(String relativePath) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("GitHub integration is not fully configured.");
        }

        String activeBranch = resolveActiveBranch();
        String url = String.format("https://api.github.com/repos/%s/%s/contents/%s?ref=%s",
                gitHubProperties.getRepoOwner().trim(),
                gitHubProperties.getRepoName().trim(),
                relativePath.trim(),
                activeBranch
        );

        HttpHeaders headers = createHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        LOGGER.info("Fetching file from GitHub: repos/{}/{}/contents/{} on branch {}",
                gitHubProperties.getRepoOwner(), gitHubProperties.getRepoName(), relativePath, activeBranch);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new java.io.IOException("GitHub API returned unsuccessful response code when fetching: " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        String sha = root.path("sha").asText();
        String encodedContent = root.path("content").asText().replaceAll("\\s", ""); // Remove newlines from base64
        byte[] decodedBytes = Base64.getDecoder().decode(encodedContent);
        String content = new String(decodedBytes, StandardCharsets.UTF_8);

        GitHubFileDetails details = new GitHubFileDetails();
        details.content = content;
        details.sha = sha;
        return details;
    }

    /**
     * Updates/Commits file content on GitHub.
     *
     * @param relativePath relative file path
     * @param newContent   fully modified file content
     * @param sha          original file blob SHA
     * @param commitMsg    commit message
     * @return Map containing "commitSha" and "commitUrl"
     */
    public Map<String, String> updateFile(String relativePath, String newContent, String sha, String commitMsg) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("GitHub integration is not fully configured.");
        }

        String url = String.format("https://api.github.com/repos/%s/%s/contents/%s",
                gitHubProperties.getRepoOwner().trim(),
                gitHubProperties.getRepoName().trim(),
                relativePath.trim()
        );

        String encodedContent = Base64.getEncoder().encodeToString(newContent.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("message", commitMsg);
        requestBody.put("content", encodedContent);
        requestBody.put("sha", sha);
        requestBody.put("branch", resolveActiveBranch());

        HttpHeaders headers = createHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        LOGGER.info("Pushing updated file commit to GitHub: repos/{}/{}/contents/{}",
                gitHubProperties.getRepoOwner(), gitHubProperties.getRepoName(), relativePath);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new java.io.IOException("GitHub API returned unsuccessful response code when committing: " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        String commitSha = root.path("commit").path("sha").asText();
        String commitUrl = root.path("commit").path("html_url").asText();

        Map<String, String> result = new HashMap<>();
        result.put("commitSha", commitSha);
        result.put("commitUrl", commitUrl);
        return result;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(gitHubProperties.getToken().trim());
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.set("User-Agent", "LogAggregationAutoRepair");
        return headers;
    }
}
