package com.kovanlabs.notificationservice.service;

import java.util.regex.Pattern;

public class ErrorFingerprinter {
    private static final Pattern UUID_PATTERN = Pattern.compile("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");
    private static final Pattern HEX_PATTERN = Pattern.compile("\\b0x[0-9a-fA-F]+\\b");
    private static final Pattern NUM_PATTERN = Pattern.compile("\\b\\d+\\b");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}([T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?)?\\b");

    public static String getSignature(String message) {
        if (message == null || message.isBlank()) {
            return "EMPTY_MESSAGE";
        }
        String normalized = message.trim();
        normalized = UUID_PATTERN.matcher(normalized).replaceAll("[UUID]");
        normalized = DATE_PATTERN.matcher(normalized).replaceAll("[DATE]");
        normalized = HEX_PATTERN.matcher(normalized).replaceAll("[HEX]");
        normalized = NUM_PATTERN.matcher(normalized).replaceAll("[NUM]");
        normalized = normalized.replaceAll("\\s+", " ").toLowerCase();
        return normalized;
    }
}
