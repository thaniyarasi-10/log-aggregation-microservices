package com.kovanlabs.lynklog.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.lynklog.config.LynkLogProperties;
import com.kovanlabs.lynklog.context.ServiceContext;
import com.kovanlabs.lynklog.model.ParsedLogEvent;

public class LynkKafkaProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(LynkKafkaProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ServiceContext serviceContext;
    private final LynkLogProperties properties;
    private final ObjectMapper objectMapper;

    public LynkKafkaProducer(KafkaTemplate<String, String> kafkaTemplate,
                             ServiceContext serviceContext,
                             LynkLogProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.serviceContext = serviceContext;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    public void publish(ParsedLogEvent logEvent) {
        try {
            String topic = properties.getKafkaTopic();
            if (topic == null || topic.isBlank()) {
                LOGGER.warn("[LYNK] Kafka topic not configured. Skipping publish.");
                return;
            }

            if (logEvent.getOrganizationId() == null) {
                logEvent.setOrganizationId(serviceContext.getOrganizationId());
            }

            String jsonMessage = objectMapper.writeValueAsString(logEvent);
            kafkaTemplate.send(topic, jsonMessage);
        } catch (Exception e) {
            LOGGER.warn("[LYNK] Failed to publish log to Kafka");
        }
    }
}
