package com.kovanlabs.notificationservice.service;

import java.util.regex.Pattern;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * Normalizer utility to strip dynamic components (Correlation/Request IDs,
 * UUIDs, dates, hex codes, numbers, and custom enterprise User ID formats)
 * from raw log messages to produce consistent signature hashes.
 */
public class ErrorNormalizer {

    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("\\[[a-fA-F0-9]{8}-\\d+\\]");
    private static final Pattern UUID_PATTERN = Pattern.compile("\\b[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}\\b");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}([T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?)?\\b");
    private static final Pattern HEX_PATTERN = Pattern.compile("\\b0x[0-9a-fA-F]+\\b");
    private static final Pattern USER_ID_PATTERN = Pattern.compile("\\bKL\\d+\\b");
    private static final Pattern NUM_PATTERN = Pattern.compile("\\b\\d+\\b");

    public static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.trim();
        normalized = REQUEST_ID_PATTERN.matcher(normalized).replaceAll("[REQUEST_ID]");
        normalized = UUID_PATTERN.matcher(normalized).replaceAll("[UUID]");
        normalized = DATE_PATTERN.matcher(normalized).replaceAll("[DATE]");
        normalized = HEX_PATTERN.matcher(normalized).replaceAll("[HEX]");
        normalized = USER_ID_PATTERN.matcher(normalized).replaceAll("[USER_ID]");
        normalized = NUM_PATTERN.matcher(normalized).replaceAll("[NUM]");
        return normalized.replaceAll("\\s+", " ");
    }

    public static String hashSignature(String text) {
        String normalized = normalize(text);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            return String.valueOf(normalized.hashCode());
        }
    }
}
