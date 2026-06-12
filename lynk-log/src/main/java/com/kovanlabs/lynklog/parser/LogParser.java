package com.kovanlabs.lynklog.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.lynklog.model.CallerInfo;
import com.kovanlabs.lynklog.model.ParsedLogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogParser.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Regex to match: "timestamp", "level", "message", "service", "environment"
    private static final Pattern CSV_PATTERN = Pattern.compile("^\"([^\"]*)\",\\s*\"([^\"]*)\",\\s*\"(.*)\",\\s*\"([^\"]*)\",\\s*\"([^\"]*)\"$");

    public ParsedLogEvent parse(String rawLog) {
        if (rawLog == null || rawLog.isBlank()) {
            throw new IllegalArgumentException("Log line is empty");
        }

        String trimmed = rawLog.trim();

        // Check if it is JSON
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                Map<String, Object> map = objectMapper.readValue(trimmed, Map.class);
                String timestamp = getMapValue(map, "@timestamp", "timestamp");
                String level = getMapValue(map, "level");
                String message = getMapValue(map, "message");
                String service = getMapValue(map, "service");
                String environment = getMapValue(map, "environment");

                String callerClass = getMapValue(map, "caller_class_name");
                String callerMethod = getMapValue(map, "caller_method_name");
                String callerFile = getMapValue(map, "caller_file_name");
                String callerLineStr = getMapValue(map, "caller_line_number");

                Integer callerLine = null;
                if (callerLineStr != null) {
                    try {
                        Object lineObj = map.get("caller_line_number");
                        if (lineObj instanceof Number) {
                            callerLine = ((Number) lineObj).intValue();
                        } else {
                            callerLine = Integer.parseInt(callerLineStr);
                        }
                    } catch (NumberFormatException e) {
                        // ignore malformed line numbers
                    }
                }

                CallerInfo caller = null;
                if (callerClass != null || callerMethod != null || callerFile != null || callerLineStr != null) {
                    caller = new CallerInfo(callerClass, callerMethod, callerFile, callerLine);
                }

                if (timestamp == null || level == null || message == null || service == null || environment == null) {
                    throw new IllegalArgumentException("Missing required JSON fields");
                }
                return new ParsedLogEvent(timestamp, level, message, service, environment, caller);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to parse JSON log: " + e.getMessage(), e);
            }
        }

        // CSV-like parsing fallback
        Matcher matcher = CSV_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            String timestamp = matcher.group(1);
            String level = matcher.group(2);
            String message = matcher.group(3);
            String service = matcher.group(4);
            String environment = matcher.group(5);
            return new ParsedLogEvent(timestamp, level, message, service, environment, null);
        }

        throw new IllegalArgumentException("Log line format not recognized: " + rawLog);
    }

    private String getMapValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val != null) {
                return val.toString();
            }
        }
        return null;
    }
}
