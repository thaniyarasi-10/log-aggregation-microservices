package com.kovanlabs.logservice;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.logservice.auth.AuthenticatedUserContext;
import com.kovanlabs.logservice.auth.UserRole;
import com.kovanlabs.logservice.model.LogDto;
import com.kovanlabs.logservice.model.LogEvent;
import com.kovanlabs.logservice.repository.ElasticRepository;
import com.kovanlabs.logservice.service.RedisLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public class LogServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ElasticRepository elasticRepository;

    @MockBean
    private RedisLogService redisLogService;

    @Autowired
    private ObjectMapper objectMapper;

    private LogEvent testLog;

    @BeforeEach
    void setUp() {
        testLog = new LogEvent();
        testLog.setService("payment-service");
        testLog.setLevel("error");
        testLog.setMessage("TIME_RANGE_TEST");
        testLog.setTimestamp("2026-06-02T07:00:00Z");
    }

    @Test
    void testSearchLogs_TimeRangeAndRBAC() throws Exception {
        when(elasticRepository.searchMulti(
                any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any()
        )).thenReturn(List.of(testLog));

        mockMvc.perform(get("/api/logs")
                        .param("from", "2026-06-02T06:00:00Z")
                        .param("to", "2026-06-02T08:00:00Z")
                        .param("services", "payment-service")
                        .param("message", "TIME_RANGE_TEST")
                        .header("X-User-Email", "dev@kovanlabs.com")
                        .header("X-User-Role", "DEV")
                        .header("X-User-Services", "payment-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].message").value("TIME_RANGE_TEST"))
                .andExpect(jsonPath("$[0].service").value("payment-service"));

        ArgumentCaptor<List<String>> serviceCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> fromCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> toCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AuthenticatedUserContext> contextCaptor = ArgumentCaptor.forClass(AuthenticatedUserContext.class);

        verify(elasticRepository).searchMulti(
                serviceCaptor.capture(),
                any(),
                any(),
                any(),
                any(),
                fromCaptor.capture(),
                toCaptor.capture(),
                anyInt(),
                anyInt(),
                contextCaptor.capture()
        );

        AuthenticatedUserContext capturedContext = contextCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertNotNull(capturedContext);
        org.junit.jupiter.api.Assertions.assertEquals(UserRole.DEV, capturedContext.role());
        org.junit.jupiter.api.Assertions.assertTrue(capturedContext.allowedServices().contains("payment-service"));
        org.junit.jupiter.api.Assertions.assertEquals("2026-06-02T06:00:00Z", fromCaptor.getValue());
        org.junit.jupiter.api.Assertions.assertEquals("2026-06-02T08:00:00Z", toCaptor.getValue());
    }

    @Test
    void testMetricsCalculations() throws Exception {
        Map<String, Object> mockMetrics = Map.of(
                "totalLogs", 100L,
                "errorCount", 5L,
                "errorRate", 5.0,
                "avgResponseTime", 120.0
        );

        when(elasticRepository.getMetrics(
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(mockMetrics);

        mockMvc.perform(get("/api/logs/metrics")
                        .param("services", "payment-service")
                        .param("timePreset", "24h")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLogs").value(100))
                .andExpect(jsonPath("$.errorCount").value(5))
                .andExpect(jsonPath("$.errorRate").value(5.0));

        verify(elasticRepository).getMetrics(
                eq("payment-service"),
                isNull(),
                isNull(),
                isNull(),
                any(),
                any(),
                eq("24h"),
                any()
        );
    }

    @Test
    void testServiceDiscovery() throws Exception {
        when(elasticRepository.getDistinctServices(any(), any(), anyInt(), any()))
                .thenReturn(List.of("payment-service", "auth-service"));

        mockMvc.perform(get("/api/logs/services")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0]").value("payment-service"))
                .andExpect(jsonPath("$[1]").value("auth-service"));

        verify(elasticRepository).getDistinctServices(isNull(), isNull(), eq(100), any());
    }

    @Test
    void testLatestErrorsRedisFallbackToElasticsearch() throws Exception {
        when(redisLogService.getLatestErrors()).thenThrow(new RuntimeException("Redis connection refused"));
        
        when(elasticRepository.searchMulti(
                any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any()
        )).thenReturn(List.of(testLog));

        mockMvc.perform(get("/api/logs/latest-errors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].service").value("payment-service"))
                .andExpect(jsonPath("$[0].message").value("TIME_RANGE_TEST"));

        reset(redisLogService);
        when(redisLogService.getLatestErrors()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/logs/latest-errors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        reset(redisLogService);
        LogDto infoLog = new LogDto();
        infoLog.setService("payment-service");
        infoLog.setLevel("INFO");
        when(redisLogService.getLatestErrors()).thenReturn(List.of(infoLog));

        mockMvc.perform(get("/api/logs/latest-errors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].level").value("error"));
    }

    @Test
    void testAdminAccessRestrictions() throws Exception {
        mockMvc.perform(get("/api/logs")
                        .header("X-User-Email", "admin@kovanlabs.com")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());

        ArgumentCaptor<AuthenticatedUserContext> contextCaptor = ArgumentCaptor.forClass(AuthenticatedUserContext.class);
        verify(elasticRepository, atLeastOnce()).searchMulti(
                any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), contextCaptor.capture()
        );

        AuthenticatedUserContext capturedContext = contextCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertNotNull(capturedContext);
        org.junit.jupiter.api.Assertions.assertEquals(UserRole.ADMIN, capturedContext.role());
    }

    @Test
    void testDeveloperAccessRestrictions() throws Exception {
        mockMvc.perform(get("/api/logs")
                        .header("X-User-Email", "dev@kovanlabs.com")
                        .header("X-User-Role", "DEV")
                        .header("X-User-Services", "auth-service"))
                .andExpect(status().isOk());

        ArgumentCaptor<AuthenticatedUserContext> contextCaptor = ArgumentCaptor.forClass(AuthenticatedUserContext.class);
        verify(elasticRepository, atLeastOnce()).searchMulti(
                any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), contextCaptor.capture()
        );

        AuthenticatedUserContext capturedContext = contextCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertNotNull(capturedContext);
        org.junit.jupiter.api.Assertions.assertEquals(UserRole.DEV, capturedContext.role());
        org.junit.jupiter.api.Assertions.assertEquals(List.of("auth-service"), capturedContext.allowedServices());
    }
}
