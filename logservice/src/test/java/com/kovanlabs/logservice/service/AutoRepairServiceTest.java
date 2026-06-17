package com.kovanlabs.logservice.service;

import com.kovanlabs.logservice.config.GitHubProperties;
import com.kovanlabs.logservice.model.ApplyMode;
import com.kovanlabs.logservice.model.AutoRepairApplyRequest;
import com.kovanlabs.logservice.model.AutoRepairApplyResponse;
import com.kovanlabs.logservice.model.AutoRepairResponse;
import com.kovanlabs.logservice.model.AutoRepairSuggestRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AutoRepairServiceTest {

    @InjectMocks
    private AutoRepairService autoRepairService;

    @Mock
    private SourceCodeService sourceCodeService;

    @Mock
    private GeminiAnalysisService geminiAnalysisService;

    @Mock
    private GitHubService gitHubService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void suggestRepair_successful_returnsSuggestion() throws Exception {
        AutoRepairSuggestRequest request = new AutoRepairSuggestRequest(
                "logservice",
                "com.kovanlabs.logservice.util.Test",
                "Test.java",
                10,
                "NullPointerException",
                "stacktrace"
        );

        Map<String, Object> sourceInfo = new HashMap<>();
        sourceInfo.put("fileContent", "public class Test {\n  private String val;\n}");
        sourceInfo.put("filePath", "logservice/src/main/java/com/kovanlabs/logservice/util/Test.java");
        sourceInfo.put("targetLine", 10);

        when(sourceCodeService.getSourceCode(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(sourceInfo);

        String mockGeminiResponse = "{\n" +
                "  \"explanation\": \"Fixed NP by checking null\",\n" +
                "  \"targetFile\": \"logservice/src/main/java/com/kovanlabs/logservice/util/Test.java\",\n" +
                "  \"originalCode\": \"private String val;\",\n" +
                "  \"fixedCode\": \"private String val = \\\"\\\";\",\n" +
                "  \"diff\": \"- private String val;\\n+ private String val = \\\"\\\";\"\n" +
                "}";

        when(geminiAnalysisService.generateContent(anyString(), anyBoolean()))
                .thenReturn(mockGeminiResponse);

        when(gitHubService.isConfigured()).thenReturn(true);

        AutoRepairResponse response = autoRepairService.suggestRepair(request);

        assertNotNull(response);
        assertEquals("Fixed NP by checking null", response.getExplanation());
        assertEquals("logservice/src/main/java/com/kovanlabs/logservice/util/Test.java", response.getTargetFile());
        assertEquals("private String val;", response.getOriginalCode());
        assertEquals("private String val = \"\";", response.getFixedCode());
        assertTrue(response.isGithubConfigured());
    }

    @Test
    void applyRepair_localMode_delegatesToSourceCodeService() throws IOException {
        AutoRepairApplyRequest request = new AutoRepairApplyRequest(
                "logservice/src/main/java/com/kovanlabs/logservice/util/Test.java",
                "private String val;",
                "private String val = \"\";",
                ApplyMode.LOCAL
        );

        doNothing().when(sourceCodeService).applyCodeFix(anyString(), anyString(), anyString());

        AutoRepairApplyResponse response = autoRepairService.applyRepair(request);

        assertNotNull(response);
        assertEquals("success", response.getStatus());
        assertTrue(response.getMessage().contains("successfully applied to local file"));
        verify(sourceCodeService).applyCodeFix(
                eq("logservice/src/main/java/com/kovanlabs/logservice/util/Test.java"),
                eq("private String val;"),
                eq("private String val = \"\";")
        );
    }

    @Test
    void applyRepair_gitHubMode_commitsSuccessfully() throws Exception {
        AutoRepairApplyRequest request = new AutoRepairApplyRequest(
                "logservice/src/main/java/com/kovanlabs/logservice/util/Test.java",
                "private String val;",
                "private String val = \"\";",
                ApplyMode.GITHUB
        );

        when(gitHubService.isConfigured()).thenReturn(true);

        GitHubService.GitHubFileDetails fileDetails = new GitHubService.GitHubFileDetails();
        fileDetails.content = "public class Test {\n  private String val;\n}";
        fileDetails.sha = "blob-sha-12345";
        when(gitHubService.fetchFile(anyString())).thenReturn(fileDetails);

        Map<String, String> commitResult = new HashMap<>();
        commitResult.put("commitSha", "sha-abc-123");
        commitResult.put("commitUrl", "https://github.com/test/commit-details");
        when(gitHubService.updateFile(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(commitResult);

        AutoRepairApplyResponse response = autoRepairService.applyRepair(request);

        assertNotNull(response);
        assertEquals("success", response.getStatus());
        assertEquals("sha-abc-123", response.getCommitSha());
        assertEquals("https://github.com/test/commit-details", response.getCommitUrl());
    }

    @Test
    void applyRepair_gitHubMode_notConfigured_returnsError() {
        AutoRepairApplyRequest request = new AutoRepairApplyRequest(
                "logservice/src/main/java/com/kovanlabs/logservice/util/Test.java",
                "private String val;",
                "private String val = \"\";",
                ApplyMode.GITHUB
        );

        when(gitHubService.isConfigured()).thenReturn(false);

        AutoRepairApplyResponse response = autoRepairService.applyRepair(request);

        assertNotNull(response);
        assertEquals("error", response.getStatus());
        assertTrue(response.getMessage().contains("GitHub integration is not configured"));
    }

    @Test
    void sourceCodeService_pathTraversalSafety() throws IOException {
        SourceCodeService localSourceCodeService = new SourceCodeService();
        File rootFile = tempDir.toFile();
        ReflectionTestUtils.setField(localSourceCodeService, "projectRoot", rootFile);

        // 1. Valid test
        File validFile = tempDir.resolve("Test.java").toFile();
        Files.writeString(validFile.toPath(), "public class Test {}");

        assertDoesNotThrow(() -> {
            // Read should succeed
            Map<String, Object> code = localSourceCodeService.getSourceCode(null, null, "Test.java", 1);
            assertNotNull(code);
        });

        // 2. Traversal path rejection
        assertThrows(SecurityException.class, () -> {
            localSourceCodeService.applyCodeFix("../escape.java", "code", "fixed");
        });
    }

    @Test
    void sourceCodeService_applyCodeFix_backupsAndRestores() throws IOException {
        SourceCodeService localSourceCodeService = new SourceCodeService();
        File rootFile = tempDir.toFile();
        ReflectionTestUtils.setField(localSourceCodeService, "projectRoot", rootFile);

        Path testFile = tempDir.resolve("ReplaceTest.java");
        Files.writeString(testFile, "public class ReplaceTest {\n  int count = 0;\n}");

        // Exact match replace test
        assertDoesNotThrow(() -> {
            localSourceCodeService.applyCodeFix(
                    "ReplaceTest.java",
                    "int count = 0;",
                    "int count = 100;"
            );
        });

        String updatedContent = Files.readString(testFile);
        assertTrue(updatedContent.contains("int count = 100;"));
        assertFalse(updatedContent.contains("int count = 0;"));

        // Match fail test
        assertThrows(IllegalArgumentException.class, () -> {
            localSourceCodeService.applyCodeFix(
                    "ReplaceTest.java",
                    "non-existent-code-block",
                    "newCode"
            );
        });
    }
}
