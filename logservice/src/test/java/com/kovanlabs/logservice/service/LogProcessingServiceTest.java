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

    @InjectMocks private LogProcessingService service;

    @BeforeEach
    void setUp() {
        service = new LogProcessingService(elasticSearchService, mongoLogEventRepository, serviceApprovalClient, redisLogService, sessionTracker, messagingTemplate, notificationServiceClient);
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
    }

    @Test
    void processLogEvent_errorLog_triggersNotification() {
        LogEvent event = new LogEvent();
        event.setService("payment-service");
        event.setLevel("ERROR");
        event.setMessage("Database connection failed");

        service.processLogEvent(event);

        verify(notificationServiceClient).sendAlert("payment-service", "Database connection failed", "ERROR");
    }
}