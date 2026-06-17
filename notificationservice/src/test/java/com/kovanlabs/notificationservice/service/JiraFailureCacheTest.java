package com.kovanlabs.notificationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class JiraFailureCacheTest {

    private JiraFailureCache failureCache;

    @BeforeEach
    void setUp() {
        failureCache = new JiraFailureCache();
    }

    @Test
    void recordAndIsActive_returnsCorrectStatus() {
        String configKey = "https://company.atlassian.net|arun@company.com|PAY";

        // Initial state
        assertThat(failureCache.isActive(configKey)).isFalse();
        assertThat(failureCache.getErrorMessage(configKey)).isNull();

        // Record a failure
        failureCache.record(configKey, "Invalid credentials or project key");

        // Cache should be active
        assertThat(failureCache.isActive(configKey)).isTrue();
        assertThat(failureCache.getErrorMessage(configKey)).isEqualTo("Invalid credentials or project key");

        // Clear cache
        failureCache.clear();
        assertThat(failureCache.isActive(configKey)).isFalse();
    }
}
