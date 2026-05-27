package com.kovanlabs.logservice.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AppLogKafkaConsumer {

    private final LogProcessingService logProcessingService;

    public AppLogKafkaConsumer(LogProcessingService logProcessingService) {
        this.logProcessingService = logProcessingService;
    }

    @KafkaListener(topics = "app-logs", groupId = "logservice-group")
    public void consume(String payload) {
        logProcessingService.process(payload);
    }
}