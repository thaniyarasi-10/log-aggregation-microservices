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

<<<<<<< HEAD
    @KafkaListener(topics = "app-logs", groupId = "logservice-group")
    public void consume(String payload) {
        logProcessingService.process(payload);
//        LOGGER.info("KAFKA RECEIVED -> {}", payload);
=======
    @KafkaListener(
            topics = "app-logs",
            groupId = "logservice-group",
            concurrency = "${spring.kafka.listener.concurrency:4}"
    )
    public void consume(java.util.List<String> payloads) {
        logProcessingService.processBatch(payloads);
>>>>>>> 6a01b900be15a6a689e602f89925f0c54101ef47
    }
}
