package com.kovanlabs.lynklog.worker;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.kovanlabs.lynklog.model.ParsedLogEvent;
import com.kovanlabs.lynklog.parser.LogParser;
import com.kovanlabs.lynklog.producer.LynkKafkaProducer;
import com.kovanlabs.lynklog.queue.LynkLogQueue;

@ExtendWith(MockitoExtension.class)
class KafkaPublisherWorkerTest {

    private LynkLogQueue queue;
    
    @Mock
    private LynkKafkaProducer producer;

    private LogParser logParser;
    private KafkaPublisherWorker worker;

    @BeforeEach
    void setUp() {
        queue = new LynkLogQueue();
        logParser = new LogParser();
        worker = new KafkaPublisherWorker(queue, producer, logParser);
    }

    @AfterEach
    void tearDown() {
        if (worker != null) {
            worker.destroy();
        }
    }

    @Test
    void worker_processesQueueItemsAndContinuesOnError() throws Exception {
        // Offer some logs to the queue in correct format
        queue.offer("\"2026-06-10T19:08:06.449+05:30\", \"INFO\", \"log line 1\", \"logservice\", \"development\"");
        
        // Verify that the producer is invoked with parsed event
        verify(producer, timeout(1000).times(1)).publish(argThat(event -> 
            "log line 1".equals(event.getMessage()) &&
            "INFO".equals(event.getLevel()) &&
            "2026-06-10T19:08:06.449+05:30".equals(event.getTimestamp()) &&
            "logservice".equals(event.getService()) &&
            "development".equals(event.getEnvironment())
        ));

        // Now setup producer to throw exception for next log
        doThrow(new RuntimeException("Kafka error")).when(producer).publish(any(ParsedLogEvent.class));

        // Offer failing log and a subsequent successful log
        queue.offer("\"2026-06-10T19:08:06.449+05:30\", \"INFO\", \"log line 2\", \"logservice\", \"development\"");
        queue.offer("\"2026-06-10T19:08:06.449+05:30\", \"INFO\", \"log line 3\", \"logservice\", \"development\"");

        // Verify that log 2 was tried and log 3 is processed despite log 2 failure
        verify(producer, timeout(1000).times(3)).publish(any(ParsedLogEvent.class));
    }

    @Test
    void worker_ignoresInvalidLogLinesAndDoesNotCrash() {
        // Offer invalid log line
        queue.offer("invalid raw log line");
        
        // Offer a valid one next
        queue.offer("\"2026-06-10T19:08:06.449+05:30\", \"INFO\", \"valid line\", \"logservice\", \"development\"");

        // Verify that the invalid log line is skipped (not sent to producer) and the valid one is processed
        verify(producer, timeout(1000).times(1)).publish(argThat(event -> "valid line".equals(event.getMessage())));
    }
}
