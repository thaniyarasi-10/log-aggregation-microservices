package com.kovanlabs.logservice.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.logservice.model.LogDto;
import com.kovanlabs.logservice.model.LogEvent;
import com.kovanlabs.logservice.repository.ElasticRepository;
import com.kovanlabs.logservice.service.LogProcessingService;
import com.kovanlabs.logservice.service.RedisLogService;
import com.kovanlabs.logservice.service.SourceCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class LogControllerTest {

    @Mock private LogProcessingService processingService;
    @Mock private RedisLogService redisLogService;
    @Mock private ElasticRepository elasticRepository;
    @Mock private SourceCodeService sourceCodeService;

    @InjectMocks private LogController controller;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void ingest_returnsSuccess() throws Exception {
        mockMvc.perform(post("/api/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(logEvent("payment-service", "ERROR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Log received"));

        verify(processingService).processLogEvent(any(LogEvent.class));
    }

    @Test
    void search_returnsEmptyList() throws Exception {
        when(elasticRepository.searchMulti(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private LogEvent logEvent(String service, String level) {
        LogEvent event = new LogEvent();
        event.setService(service);
        event.setLevel(level);
        event.setMessage("test message");
        event.setTimestamp("2026-05-01T10:00:00Z");
        return event;
    }

    @Test
    void getLatestErrors_returnsErrorsList() throws Exception {
        java.util.List<LogEvent> errors = java.util.List.of(logEvent("payment-service", "ERROR"));
        when(elasticRepository.searchMulti(
                any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any()
        )).thenReturn(errors);

        mockMvc.perform(get("/api/logs/latest-errors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].service").value("payment-service"))
                .andExpect(jsonPath("$[0].level").value("ERROR"));

        verify(elasticRepository).searchMulti(
                any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any()
        );
    }

    @Test
    void getSourceCode_returnsNotFound() throws Exception {
        when(sourceCodeService.getSourceCode(any(), any(), any(), any()))
                .thenReturn(null);

        mockMvc.perform(get("/api/logs/source-code")
                        .param("service", "logservice")
                        .param("file", "LogController.java"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Source file not found"));
    }

    @Test
    void getSourceCode_returnsFileContent() throws Exception {
        java.util.Map<String, Object> mockResponse = java.util.Map.of(
                "service", "logservice",
                "fileName", "LogController.java",
                "filePath", "logservice/src/main/java/com/kovanlabs/logservice/controller/LogController.java",
                "lineNumber", 10,
                "fileContent", "public class LogController {}"
        );
        when(sourceCodeService.getSourceCode(any(), any(), any(), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/logs/source-code")
                        .param("service", "logservice")
                        .param("file", "LogController.java")
                        .param("line", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("logservice"))
                .andExpect(jsonPath("$.fileContent").value("public class LogController {}"));
    }
}