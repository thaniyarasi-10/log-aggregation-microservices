package com.kovanlabs.logservice.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class LogProcessingMetricsTracker {

    private final Counter totalLogsCounter;
    private final Counter duplicateLogsCounter;
    private final Counter unapprovedLogsCounter;
    private final Counter logsSentToAiCounter;
    private final Timer processingTimer;

    public LogProcessingMetricsTracker(MeterRegistry registry) {
        this.totalLogsCounter = Counter.builder("log.processing.received.total")
                .description("Total number of logs received")
                .register(registry);

        this.duplicateLogsCounter = Counter.builder("log.processing.duplicate.skipped")
                .description("Number of duplicate logs skipped")
                .register(registry);

        this.unapprovedLogsCounter = Counter.builder("log.processing.unapproved.discarded")
                .description("Number of logs from unapproved services discarded")
                .register(registry);

        this.logsSentToAiCounter = Counter.builder("log.processing.ai.sent")
                .description("Number of logs sent to AI Troubleshooting Assistant")
                .register(registry);

        this.processingTimer = Timer.builder("log.processing.time")
                .description("Time taken to process log batches")
                .register(registry);
    }

    public void incrementTotalLogs(double count) {
        totalLogsCounter.increment(count);
    }

    public void incrementDuplicatesSkipped(double count) {
        duplicateLogsCounter.increment(count);
    }

    public void incrementUnapprovedDiscarded(double count) {
        unapprovedLogsCounter.increment(count);
    }

    public void incrementLogsSentToAi(double count) {
        logsSentToAiCounter.increment(count);
    }

    public void recordProcessingTime(long durationMs) {
        processingTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }
}
