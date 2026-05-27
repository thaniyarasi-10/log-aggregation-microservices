package com.kovanlabs.logservice.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.logservice.model.LogEvent;
import com.kovanlabs.logservice.service.LogProcessingService;
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
                .andExpect(content().string("{\"status\":\"success\",\"message\":\"Log received\"}"));

        verify(processingService).processLogEvent(any(LogEvent.class));
    }

    @Test
    void search_returnsEmptyList() throws Exception {
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
}