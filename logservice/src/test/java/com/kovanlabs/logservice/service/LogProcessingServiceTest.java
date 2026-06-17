package com.kovanlabs.logservice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kovanlabs.logservice.model.LogEvent;
import com.kovanlabs.logservice.mongo.repository.MongoLogEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogProcessingServiceTest {

    @Mock private ElasticSearchService elasticSearchService;
    @Mock private MongoLogEventRepository mongoLogEventRepository;
    @Mock private ServiceApprovalClient serviceApprovalClient;
    @Mock private RedisLogService redisLogService;
    @Mock private WebSocketSessionTracker sessionTracker;
    @Mock private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    @Mock private NotificationServiceClient notificationServiceClient;
    @Mock private ErrorSuggestionService errorSuggestionService;
    @Mock private LogProcessingMetricsTracker metricsTracker;

    @InjectMocks private LogProcessingService service;

    @BeforeEach
    void setUp() {
        service = new LogProcessingService(elasticSearchService, mongoLogEventRepository, serviceApprovalClient, redisLogService, sessionTracker, messagingTemplate, notificationServiceClient, errorSuggestionService, metricsTracker);
        org.mockito.Mockito.lenient().when(serviceApprovalClient.isApproved(any())).thenReturn(true);
    }

    @Test
    void process_validJson_savesParsedEvent() {
        service.process("{\"service\":\"payment-service\",\"level\":\"INFO\",\"message\":\"ok\"}");

        verify(elasticSearchService).save(any(LogEvent.class));
    }

    @Test
    void process_blankJson_skipsSave() {
        service.process("   ");

        verify(elasticSearchService, never()).save(any());
    }

    @Test
    void processLogEvent_nullEvent_skipsSave() {
        service.processLogEvent(null);

        verify(elasticSearchService, never()).save(any());
    }

    @Test
    void processLogEvent_unapprovedService_discardsEvent() {
        LogEvent event = new LogEvent();
        event.setService("payment-service");

        org.mockito.Mockito.when(serviceApprovalClient.isApproved("payment-service")).thenReturn(false);

        service.processLogEvent(event);

        verify(mongoLogEventRepository, never()).save(any());
        verify(elasticSearchService, never()).save(any());
        verify(metricsTracker).incrementUnapprovedDiscarded(1);
    }

    @Test
    void processLogEvent_errorLog_triggersNotification() {
        LogEvent event = new LogEvent();
        event.setService("payment-service");
        event.setLevel("ERROR");
        event.setMessage("Database connection failed");

        org.mockito.Mockito.when(redisLogService.acquireErrorAnalysisLock(any(), any())).thenReturn(true);

        service.processLogEvent(event);

        verify(notificationServiceClient).sendAlert("payment-service", "Database connection failed", "ERROR");
    }

    @Test
    void processLogEvent_errorLog_triggersErrorSuggestion() {
        LogEvent event = new LogEvent();
        event.setService("payment-service");
        event.setLevel("ERROR");
        event.setMessage("NullPointerException occurred");

        org.mockito.Mockito.when(redisLogService.acquireErrorAnalysisLock(any(), any())).thenReturn(true);

        service.processLogEvent(event);

        verify(errorSuggestionService).attachSuggestion(event);
    }

    @Test
    void processLogEvent_duplicateLog_skipped() {
        LogEvent event = new LogEvent();
        event.setService("payment-service");
        event.setLevel("INFO");
        event.setMessage("Already seen");

        org.mockito.Mockito.when(redisLogService.isDuplicateAndSet(any(), any(Long.class))).thenReturn(true);

        service.processLogEvent(event);

        verify(elasticSearchService, never()).save(event);
        verify(metricsTracker).incrementDuplicatesSkipped(1);
    }

    @Test
    void processLogEvent_throttledErrorLog_skipsAiAnalysis() {
        LogEvent event = new LogEvent();
        event.setService("payment-service");
        event.setLevel("ERROR");
        event.setMessage("Repeated error");

        org.mockito.Mockito.when(redisLogService.acquireErrorAnalysisLock(any(), any())).thenReturn(false);

        service.processLogEvent(event);

        verify(errorSuggestionService, never()).attachSuggestion(event);
        verify(elasticSearchService).save(event); // still persisted
    }

    @Test
    void processBatch_validJsons_savesInBulk() {
        java.util.List<String> payloads = java.util.List.of(
            "{\"service\":\"payment-service\",\"level\":\"INFO\",\"message\":\"msg1\"}",
            "{\"service\":\"payment-service\",\"level\":\"ERROR\",\"message\":\"msg2\"}"
        );

        org.mockito.Mockito.when(redisLogService.acquireErrorAnalysisLock(any(), any())).thenReturn(true);

        service.processBatch(payloads);

        verify(mongoLogEventRepository).saveAll(any(java.util.List.class));
        verify(elasticSearchService).saveAll(any(java.util.List.class));
        verify(metricsTracker).incrementTotalLogs(2);
    }

    @Test
    void processLogEvent_loopProneSelfReferentialErrorLog_skipsAlertTrigger() {
        LogEvent event = new LogEvent();
        event.setService("notification-service");
        event.setLevel("ERROR");
        event.setMessage("Failed to create Jira Story for alertId 123");

        org.mockito.Mockito.when(redisLogService.acquireErrorAnalysisLock(any(), any())).thenReturn(true);

        service.processLogEvent(event);

        verify(notificationServiceClient, never()).sendAlert(any(), any(), any());
    }

    @Test
    void process_invalidJson_skipsParsingAndSave() {
        service.process("\"@metadata\"");

        verify(elasticSearchService, never()).save(any());
    }

    @Test
    void processBatch_containsInvalidJson_skipsInvalidButSavesValid() {
        java.util.List<String> payloads = java.util.List.of(
            "\"@metadata\"",
            "{\"service\":\"payment-service\",\"level\":\"INFO\",\"message\":\"msg1\"}"
        );

        service.processBatch(payloads);

        verify(mongoLogEventRepository).saveAll(any(java.util.List.class));
        verify(elasticSearchService).saveAll(any(java.util.List.class));
        verify(metricsTracker).incrementTotalLogs(2);
    }
}