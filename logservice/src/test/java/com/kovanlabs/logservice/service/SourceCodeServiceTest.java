package com.kovanlabs.logservice.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

class SourceCodeServiceTest {

    @Test
    void testLocateFile() throws Exception {
        SourceCodeService service = new SourceCodeService();
        
        // Test notification-service mapping
        Map<String, Object> notificationResult = service.getSourceCode(
                "notification-service",
                "com.kovanlabs.notificationservice.service.JiraStoryService",
                "JiraStoryService.java",
                1
        );
        assertNotNull(notificationResult, "Should locate JiraStoryService.java in notificationservice service");
        assertEquals("JiraStoryService.java", notificationResult.get("fileName"));
        assertTrue(notificationResult.get("filePath").toString().contains("notificationservice"));
        assertTrue(notificationResult.get("fileContent").toString().contains("class JiraStoryService"));

        // Test logservice mapping
        Map<String, Object> logserviceResult = service.getSourceCode(
                "logservice",
                "com.kovanlabs.logservice.service.SourceCodeService",
                "SourceCodeService.java",
                1
        );
        assertNotNull(logserviceResult, "Should locate SourceCodeService.java in logservice service");
        assertEquals("SourceCodeService.java", logserviceResult.get("fileName"));
        assertTrue(logserviceResult.get("filePath").toString().contains("logservice"));
        assertTrue(logserviceResult.get("fileContent").toString().contains("class SourceCodeService"));
    }
}
