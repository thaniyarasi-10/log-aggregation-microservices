package com.kovanlabs.lynklog.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import com.kovanlabs.lynklog.model.ParsedLogEvent;
import com.kovanlabs.lynklog.parser.LogParser;
import com.kovanlabs.lynklog.producer.LynkKafkaProducer;
import com.kovanlabs.lynklog.queue.LynkLogQueue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KafkaPublisherWorker implements DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaPublisherWorker.class);

    private final LynkLogQueue queue;
    private final LynkKafkaProducer producer;
    private final LogParser logParser;
    private final ExecutorService executorService;
    private volatile boolean running = true;

    public KafkaPublisherWorker(LynkLogQueue queue, LynkKafkaProducer producer, LogParser logParser) {
        this.queue = queue;
        this.producer = producer;
        this.logParser = logParser;
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "lynk-kafka-worker");
            thread.setDaemon(true);
            return thread;
        });
        this.executorService.submit(this::processQueue);
    }

    private void processQueue() {
        while (running) {
            try {
                String rawLog = queue.take();
                try {
                    ParsedLogEvent parsed = logParser.parse(rawLog);
                    try {
                        producer.publish(parsed);
                    } catch (Exception prodEx) {
                        LOGGER.warn("[LYNK] Failed to publish log to Kafka");
                    }
                } catch (IllegalArgumentException parseEx) {
                    LOGGER.warn("[LYNK] Failed to parse log line: {}", rawLog);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.error("[LYNK] Error in KafkaPublisherWorker processing: {}", e.getMessage());
            }
        }
    }

    @Override
    public void destroy() {
        running = false;
        executorService.shutdownNow();
    }
}
