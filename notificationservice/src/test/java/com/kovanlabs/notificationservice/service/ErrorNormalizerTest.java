package com.kovanlabs.notificationservice.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ErrorNormalizerTest {

    @Test
    void normalize_variousPatterns_returnsNormalizedString() {
        // Test UUID normalization
        String uuidText = "Error in user session 3f8c8d8b-4b2a-4638-9e5c-02720d2d3a3d";
        assertThat(ErrorNormalizer.normalize(uuidText))
                .isEqualTo("Error in user session [UUID]");

        // Test Date/Time normalization
        String dateText = "Timestamp: 2026-06-15T12:00:00Z for exception";
        assertThat(ErrorNormalizer.normalize(dateText))
                .isEqualTo("Timestamp: [DATE] for exception");

        // Test correlation ID / request ID normalization
        String correlationText = "Request [abcdef12-1002] failed";
        assertThat(ErrorNormalizer.normalize(correlationText))
                .isEqualTo("Request [REQUEST_ID] failed");

        // Test Hex normalization
        String hexText = "Memory address 0x7fff4a2b is locked";
        assertThat(ErrorNormalizer.normalize(hexText))
                .isEqualTo("Memory address [HEX] is locked");

        // Test User ID normalization
        String userIdText = "Enterprise user KL10006 accessed forbidden resource";
        assertThat(ErrorNormalizer.normalize(userIdText))
                .isEqualTo("Enterprise user [USER_ID] accessed forbidden resource");

        // Test Number normalization
        String numText = "Retry attempt 3 of 5 failed after 12000 milliseconds";
        assertThat(ErrorNormalizer.normalize(numText))
                .isEqualTo("Retry attempt [NUM] of [NUM] failed after [NUM] milliseconds");
    }

    @Test
    void hashSignature_returnsConsistentlyUniqueHashes() {
        String text1 = "NPE at user session 3f8c8d8b-4b2a-4638-9e5c-02720d2d3a3d on 2026-06-15T12:00:00Z";
        String text2 = "NPE at user session 7f9d8a8b-1b2a-3638-8e5c-12720d2d4a3e on 2026-06-16T13:45:00Z";

        String hash1 = ErrorNormalizer.hashSignature(text1);
        String hash2 = ErrorNormalizer.hashSignature(text2);

        // Hashes should be identical because UUID and dates are normalized
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 hex length
    }
}
