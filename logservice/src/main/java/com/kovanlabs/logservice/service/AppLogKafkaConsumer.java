package com.kovanlabs.logservice.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.kovanlabs.logservice.controller.LogController.LOGGER;

@Component
public class AppLogKafkaConsumer {

    private final LogProcessingService logProcessingService;

    public AppLogKafkaConsumer(LogProcessingService logProcessingService) {
        this.logProcessingService = logProcessingService;
    }

    @KafkaListener(
            topics = "app-logs",
            groupId = "logservice-group",
            concurrency = "${spring.kafka.listener.concurrency:4}"
    )
    public void consume(String payload) {
        logProcessingService.process(payload);
//        LOGGER.info("KAFKA RECEIVED -> {}", payload);
    }
}
