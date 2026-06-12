package com.kovanlabs.lynklog.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.kovanlabs.lynklog.model.ParsedLogEvent;

class LogParserTest {

    private LogParser logParser;

    @BeforeEach
    void setUp() {
        logParser = new LogParser();
    }

    @Test
    void parse_validQuotedCsvFormat_returnsParsedLogEvent() {
        String logLine = "\"2026-06-10T19:08:06.449+05:30\", \"INFO\", \"Initializing Elasticsearch index template...\", \"logservice\", \"development\"";
        ParsedLogEvent event = logParser.parse(logLine);

        assertThat(event).isNotNull();
        assertThat(event.getTimestamp()).isEqualTo("2026-06-10T19:08:06.449+05:30");
        assertThat(event.getLevel()).isEqualTo("INFO");
        assertThat(event.getMessage()).isEqualTo("Initializing Elasticsearch index template...");
        assertThat(event.getService()).isEqualTo("logservice");
        assertThat(event.getEnvironment()).isEqualTo("development");
        assertThat(event.getCaller()).isNull();
    }

    @Test
    void parse_validJsonFormat_returnsParsedLogEvent() {
        String logLine = "{\"timestamp\":\"2026-06-10T19:08:06.449+05:30\",\"level\":\"INFO\",\"message\":\"Initializing index\",\"service\":\"logservice\",\"environment\":\"development\"}";
        ParsedLogEvent event = logParser.parse(logLine);

        assertThat(event).isNotNull();
        assertThat(event.getTimestamp()).isEqualTo("2026-06-10T19:08:06.449+05:30");
        assertThat(event.getLevel()).isEqualTo("INFO");
        assertThat(event.getMessage()).isEqualTo("Initializing index");
        assertThat(event.getService()).isEqualTo("logservice");
        assertThat(event.getEnvironment()).isEqualTo("development");
        assertThat(event.getCaller()).isNull();
    }

    @Test
    void parse_validJsonWithLogstashTimestamp_returnsParsedLogEvent() {
        String logLine = "{\"@timestamp\":\"2026-06-10T19:08:06.449+05:30\",\"level\":\"INFO\",\"message\":\"Logstash format\",\"service\":\"logservice\",\"environment\":\"development\"}";
        ParsedLogEvent event = logParser.parse(logLine);

        assertThat(event).isNotNull();
        assertThat(event.getTimestamp()).isEqualTo("2026-06-10T19:08:06.449+05:30");
        assertThat(event.getLevel()).isEqualTo("INFO");
        assertThat(event.getMessage()).isEqualTo("Logstash format");
        assertThat(event.getService()).isEqualTo("logservice");
        assertThat(event.getEnvironment()).isEqualTo("development");
        assertThat(event.getCaller()).isNull();
    }

    @Test
    void parse_validJsonWithCallerInfo_returnsParsedLogEventWithCaller() {
        String logLine = "{" +
            "\"@timestamp\":\"2026-06-11T08:45:21.910Z\"," +
            "\"level\":\"ERROR\"," +
            "\"service\":\"logservice\"," +
            "\"environment\":\"development\"," +
            "\"message\":\"Failed to send alert trigger...\"," +
            "\"caller_class_name\":\"com.kovanlabs.logservice.service.NotificationServiceClient\"," +
            "\"caller_method_name\":\"sendAlert\"," +
            "\"caller_file_name\":\"NotificationServiceClient.java\"," +
            "\"caller_line_number\":43" +
            "}";
        ParsedLogEvent event = logParser.parse(logLine);

        assertThat(event).isNotNull();
        assertThat(event.getTimestamp()).isEqualTo("2026-06-11T08:45:21.910Z");
        assertThat(event.getLevel()).isEqualTo("ERROR");
        assertThat(event.getMessage()).isEqualTo("Failed to send alert trigger...");
        assertThat(event.getService()).isEqualTo("logservice");
        assertThat(event.getEnvironment()).isEqualTo("development");
        assertThat(event.getCaller()).isNotNull();
        assertThat(event.getCaller().getClazz()).isEqualTo("com.kovanlabs.logservice.service.NotificationServiceClient");
        assertThat(event.getCaller().getMethod()).isEqualTo("sendAlert");
        assertThat(event.getCaller().getFile()).isEqualTo("NotificationServiceClient.java");
        assertThat(event.getCaller().getLine()).isEqualTo(43);
    }

    @Test
    void parse_validJsonWithCallerInfoLineAsString_returnsParsedLogEventWithCaller() {
        String logLine = "{" +
            "\"@timestamp\":\"2026-06-11T08:45:21.910Z\"," +
            "\"level\":\"ERROR\"," +
            "\"service\":\"logservice\"," +
            "\"environment\":\"development\"," +
            "\"message\":\"Failed to send alert trigger...\"," +
            "\"caller_class_name\":\"com.kovanlabs.logservice.service.NotificationServiceClient\"," +
            "\"caller_method_name\":\"sendAlert\"," +
            "\"caller_file_name\":\"NotificationServiceClient.java\"," +
            "\"caller_line_number\":\"43\"" +
            "}";
        ParsedLogEvent event = logParser.parse(logLine);

        assertThat(event).isNotNull();
        assertThat(event.getCaller()).isNotNull();
        assertThat(event.getCaller().getClazz()).isEqualTo("com.kovanlabs.logservice.service.NotificationServiceClient");
        assertThat(event.getCaller().getMethod()).isEqualTo("sendAlert");
        assertThat(event.getCaller().getFile()).isEqualTo("NotificationServiceClient.java");
        assertThat(event.getCaller().getLine()).isEqualTo(43);
    }

    @Test
    void parse_invalidLogLines_throwsException() {
        // Missing fields or malformed CSV
        assertThatThrownBy(() -> logParser.parse("invalid line format"))
            .isInstanceOf(IllegalArgumentException.class);

        // Blank line
        assertThatThrownBy(() -> logParser.parse("   "))
            .isInstanceOf(IllegalArgumentException.class);

        // Null line
        assertThatThrownBy(() -> logParser.parse(null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
