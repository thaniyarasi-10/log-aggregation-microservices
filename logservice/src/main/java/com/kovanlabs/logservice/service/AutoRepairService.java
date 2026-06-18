package com.kovanlabs.logservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.logservice.model.ApplyMode;
import com.kovanlabs.logservice.model.AutoRepairApplyRequest;
import com.kovanlabs.logservice.model.AutoRepairApplyResponse;
import com.kovanlabs.logservice.model.AutoRepairResponse;
import com.kovanlabs.logservice.model.AutoRepairSuggestRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
public class AutoRepairService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoRepairService.class);

    private final SourceCodeService sourceCodeService;
    private final GeminiAnalysisService geminiAnalysisService;
    private final GitHubService gitHubService;
    private final ObjectMapper objectMapper;

    public AutoRepairService(SourceCodeService sourceCodeService,
                             GeminiAnalysisService geminiAnalysisService,
                             GitHubService gitHubService) {
        this.sourceCodeService = sourceCodeService;
        this.geminiAnalysisService = geminiAnalysisService;
        this.gitHubService = gitHubService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);
    }

    /**
     * Suggests a code repair by reading local source files, prompting Gemini AI, and returning the structured fix response.
     */
    public AutoRepairResponse suggestRepair(AutoRepairSuggestRequest request) throws Exception {
        LOGGER.info("Suggesting repair for service={} file={}", request.getService(), request.getFileName());

        // 1. Fetch file content using SourceCodeService
        Map<String, Object> sourceInfo = sourceCodeService.getSourceCode(
                request.getService(),
                request.getClassName(),
                request.getFileName(),
                request.getLineNumber()
        );

        if (sourceInfo == null) {
            throw new IllegalArgumentException("Could not retrieve source file details for " + request.getFileName());
        }

        String fileContent = (String) sourceInfo.get("fileContent");
        String filePath = (String) sourceInfo.get("filePath");
        Integer targetLine = (Integer) sourceInfo.get("targetLine");

        if (fileContent == null || fileContent.isBlank()) {
            throw new IllegalArgumentException("Source file content is empty or unreadable: " + request.getFileName());
        }

        // 2. Build detailed prompt for Gemini
        String prompt = String.format(
                "You are an expert software engineer specializing in backend systems troubleshooting and code repair. " +
                "Analyze the following error log event from service '%s' and repair the corresponding source code:\n\n" +
                "--- ERROR DETAILS ---\n" +
                "Error Message: %s\n" +
                "Stack Trace / Details: %s\n\n" +
                "--- SOURCE CODE FILE DETAILS ---\n" +
                "File Path: %s\n" +
                "Target Line Number: %d\n" +
                "Original File Content:\n" +
                "```java\n%s\n```\n\n" +
                "Your objective is to fix the bug at or near the target line. " +
                "You MUST return a JSON object conforming exactly to this schema:\n" +
                "{\n" +
                "  \"explanation\": \"A concise, clear markdown description of why the error occurred and how this fix resolves it.\",\n" +
                "  \"targetFile\": \"The relative file path (must match exactly: '%s')\",\n" +
                "  \"originalCode\": \"The exact contiguous block of code from the original file that will be replaced. This must match the original file content exactly, character-for-character including indentation and line endings.\",\n" +
                "  \"fixedCode\": \"The new code block that will replace the originalCode block.\",\n" +
                "  \"diff\": \"A unified diff showing the deleted lines (-) and added lines (+).\"\n" +
                "}\n\n" +
                "Crucial Instructions:\n" +
                "1. The 'originalCode' MUST match a portion of the original file content EXACTLY. Otherwise the string replacement will fail.\n" +
                "2. Ensure the replacement resolves the error safely (e.g. adding null guards, handling bounds checks, closing resources, wrapping in try-catch, etc.).\n" +
                "3. Return raw JSON only, do not wrap in markdown or backticks.",
                request.getService(),
                request.getMessage() != null ? request.getMessage() : "N/A",
                request.getErrorDetails() != null ? request.getErrorDetails() : "N/A",
                filePath,
                targetLine,
                fileContent,
                filePath
        );

        // 3. Call Gemini
        LOGGER.debug("Submitting repair request to Gemini Analysis Service...");
        Map<String, Object> responseSchema = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "explanation", Map.of("type", "STRING"),
                        "targetFile", Map.of("type", "STRING"),
                        "originalCode", Map.of("type", "STRING"),
                        "fixedCode", Map.of("type", "STRING"),
                        "diff", Map.of("type", "STRING")
                ),
                "required", java.util.List.of("explanation", "targetFile", "originalCode", "fixedCode", "diff")
        );
        String responseText = geminiAnalysisService.generateContent(prompt, true, responseSchema);

        if (responseText == null || responseText.isBlank()) {
            throw new IOException("Gemini AI failed to return a valid repair suggestion.");
        }

        // 4. Parse response
        JsonNode root = objectMapper.readTree(responseText);
        
        String explanation = root.path("explanation").asText();
        String targetFile = root.path("targetFile").asText();
        String originalCode = root.path("originalCode").asText();
        String fixedCode = root.path("fixedCode").asText();
        String diff = root.path("diff").asText();

        // Perform basic validations
        if (originalCode.isEmpty() || fixedCode.isEmpty()) {
            throw new IllegalArgumentException("AI generated incomplete suggestion. missing originalCode or fixedCode.");
        }

        // Check if the originalCode block exists inside the local file content
        String lineEnding = fileContent.contains("\r\n") ? "\r\n" : "\n";
        String normalizedOriginal = originalCode.replace("\r\n", "\n").replace("\n", lineEnding);
        if (!fileContent.contains(normalizedOriginal) && !fileContent.contains(originalCode)) {
            LOGGER.warn("Gemini originalCode mismatch. originalCode: '{}'", originalCode);
            // We still proceed but warn the developer that exact matches might fail during applying.
        }

        boolean githubConfigured = gitHubService.isConfigured();

        return new AutoRepairResponse(explanation, targetFile, originalCode, fixedCode, diff, githubConfigured);
    }

    /**
     * Executes the code repair either locally or via a GitHub commit.
     */
    public AutoRepairApplyResponse applyRepair(AutoRepairApplyRequest request) {
        LOGGER.info("Applying repair: mode={} path={}", request.getApplyMode(), request.getFilePath());

        try {
            if (request.getApplyMode() == ApplyMode.LOCAL) {
                // LOCAL MODE: Modify files directly in local workspace
                sourceCodeService.applyCodeFix(request.getFilePath(), request.getOriginalCode(), request.getFixedCode());
                
                return new AutoRepairApplyResponse(
                        "success",
                        "Code fix successfully applied to local file: " + request.getFilePath(),
                        null,
                        null
                );
            } else if (request.getApplyMode() == ApplyMode.GITHUB) {
                // GITHUB MODE: Fetch, patch, and push to GitHub API
                if (!gitHubService.isConfigured()) {
                    throw new IllegalStateException("GitHub integration is not configured. Please supply environment credentials.");
                }

                // 1. Fetch from GitHub
                GitHubService.GitHubFileDetails fileDetails = gitHubService.fetchFile(request.getFilePath());

                // 2. Perform patching in memory
                String fileContent = fileDetails.content;
                String lineEnding = fileContent.contains("\r\n") ? "\r\n" : "\n";
                String normalizedOriginal = request.getOriginalCode().replace("\r\n", "\n").replace("\n", lineEnding);
                String normalizedFixed = request.getFixedCode().replace("\r\n", "\n").replace("\n", lineEnding);

                if (!fileContent.contains(normalizedOriginal)) {
                    if (fileContent.contains(request.getOriginalCode())) {
                        normalizedOriginal = request.getOriginalCode();
                        normalizedFixed = request.getFixedCode();
                    } else {
                        throw new IllegalArgumentException("Original code block does not match the content currently on the remote GitHub repository.");
                    }
                }

                String updatedContent = fileContent.replace(normalizedOriginal, normalizedFixed);

                // 3. Push back to GitHub
                String commitMsg = "fix(autorepair): automatically applied code fix for " + request.getFilePath();
                Map<String, String> commitResult = gitHubService.updateFile(
                        request.getFilePath(),
                        updatedContent,
                        fileDetails.sha,
                        commitMsg
                );

                return new AutoRepairApplyResponse(
                        "success",
                        "Code fix committed to GitHub repository successfully.",
                        commitResult.get("commitUrl"),
                        commitResult.get("commitSha")
                );
            } else {
                throw new IllegalArgumentException("Unsupported applyMode: " + request.getApplyMode());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to apply code repair: {}", e.getMessage(), e);
            return new AutoRepairApplyResponse(
                    "error",
                    "Failed to apply repair plan: " + e.getMessage(),
                    null,
                    null
            );
        }
    }
}
