package com.kovanlabs.lynklog.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.lynklog.config.LynkLogProperties;
import com.kovanlabs.lynklog.context.ServiceContext;
import com.kovanlabs.lynklog.model.CallerInfo;
import com.kovanlabs.lynklog.model.ParsedLogEvent;

import java.util.List;
import java.util.stream.Collectors;

@ExtendWith(MockitoExtension.class)
class LynkKafkaProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private ServiceContext serviceContext;
    private LynkLogProperties properties;
    private LynkKafkaProducer producer;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        properties = new LynkLogProperties();
        properties.setKafkaTopic("test-topic");
        serviceContext = new ServiceContext();
        serviceContext.setServiceName("test-service");
        producer = new LynkKafkaProducer(kafkaTemplate, serviceContext, properties);

        logger = (Logger) LoggerFactory.getLogger(LynkKafkaProducer.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        if (logger != null && listAppender != null) {
            logger.detachAppender(listAppender);
        }
    }

    @Test
    void publish_sendsJsonMessageToKafkaTopic() throws Exception {
        ParsedLogEvent logEvent = new ParsedLogEvent("2026-06-10T19:08:06.449+05:30", "INFO", "hello world", "test-service", "dev");
        producer.publish(logEvent);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaTemplate, times(1)).send(topicCaptor.capture(), messageCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("test-topic");

        ObjectMapper mapper = new ObjectMapper();
        ParsedLogEvent publishedEvent = mapper.readValue(messageCaptor.getValue(), ParsedLogEvent.class);

        assertThat(publishedEvent.getMessage()).isEqualTo("hello world");
        assertThat(publishedEvent.getLevel()).isEqualTo("INFO");
        assertThat(publishedEvent.getTimestamp()).isEqualTo("2026-06-10T19:08:06.449+05:30");
        assertThat(publishedEvent.getService()).isEqualTo("test-service");
        assertThat(publishedEvent.getEnvironment()).isEqualTo("dev");
        assertThat(publishedEvent.getCaller()).isNull();
    }

    @Test
    void publish_sendsJsonMessageWithCallerToKafkaTopic() throws Exception {
        CallerInfo caller = new CallerInfo(
            "com.kovanlabs.logservice.service.NotificationServiceClient",
            "sendAlert",
            "NotificationServiceClient.java",
            43
        );
        ParsedLogEvent logEvent = new ParsedLogEvent(
            "2026-06-11T08:45:21.910Z",
            "ERROR",
            "Failed to send alert trigger...",
            "logservice",
            "development",
            caller
        );
        producer.publish(logEvent);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaTemplate, times(1)).send(topicCaptor.capture(), messageCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("test-topic");

        ObjectMapper mapper = new ObjectMapper();
        ParsedLogEvent publishedEvent = mapper.readValue(messageCaptor.getValue(), ParsedLogEvent.class);

        assertThat(publishedEvent.getMessage()).isEqualTo("Failed to send alert trigger...");
        assertThat(publishedEvent.getLevel()).isEqualTo("ERROR");
        assertThat(publishedEvent.getTimestamp()).isEqualTo("2026-06-11T08:45:21.910Z");
        assertThat(publishedEvent.getService()).isEqualTo("logservice");
        assertThat(publishedEvent.getEnvironment()).isEqualTo("development");
        
        assertThat(publishedEvent.getCaller()).isNotNull();
        assertThat(publishedEvent.getCaller().getClazz()).isEqualTo("com.kovanlabs.logservice.service.NotificationServiceClient");
        assertThat(publishedEvent.getCaller().getMethod()).isEqualTo("sendAlert");
        assertThat(publishedEvent.getCaller().getFile()).isEqualTo("NotificationServiceClient.java");
        assertThat(publishedEvent.getCaller().getLine()).isEqualTo(43);

        // Also assert raw JSON string structure matches expected class field name serialization
        String rawJson = messageCaptor.getValue();
        assertThat(rawJson).contains("\"class\":\"com.kovanlabs.logservice.service.NotificationServiceClient\"");
        assertThat(rawJson).contains("\"@timestamp\":\"2026-06-11T08:45:21.910Z\"");
    }

    @Test
    void publish_whenTopicNotConfigured_skipsPublishing() {
        properties.setKafkaTopic(null);
        ParsedLogEvent logEvent = new ParsedLogEvent("2026-06-10T19:08:06.449+05:30", "INFO", "hello world", "test-service", "dev");

        producer.publish(logEvent);

        verifyNoInteractions(kafkaTemplate);

        List<String> warnings = listAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(Collectors.toList());
        assertThat(warnings).anyMatch(msg -> msg.contains("[LYNK] Kafka topic not configured"));
    }

    @Test
    void publish_whenKafkaFails_logsWarningAndDoesNotThrow() {
        when(kafkaTemplate.send(anyString(), anyString())).thenThrow(new RuntimeException("Kafka Down"));
        ParsedLogEvent logEvent = new ParsedLogEvent("2026-06-10T19:08:06.449+05:30", "INFO", "hello world", "test-service", "dev");

        producer.publish(logEvent);

        List<String> warnings = listAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(Collectors.toList());

        assertThat(warnings).contains("[LYNK] Failed to publish log to Kafka");
    }
}
