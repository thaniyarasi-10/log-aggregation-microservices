package com.kovanlabs.notificationservice.service;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache to record Jira configuration integration failures.
 * Prevents spamming Jira when a project key or credential is misconfigured.
 */
@Component
public class JiraFailureCache {

    private static class FailureInfo {
        final long timestamp;
        final String errorMessage;

        FailureInfo(long timestamp, String errorMessage) {
            this.timestamp = timestamp;
            this.errorMessage = errorMessage;
        }
    }

    private final Map<String, FailureInfo> cache = new ConcurrentHashMap<>();
    private final long cooldownMs = 300_000L; // 5 minutes

    public void record(String configHash, String errorMessage) {
        if (configHash != null) {
            cache.put(configHash, new FailureInfo(System.currentTimeMillis(), errorMessage));
        }
    }

    public boolean isActive(String configHash) {
        if (configHash == null) {
            return false;
        }
        FailureInfo info = cache.get(configHash);
        if (info == null) {
            return false;
        }
        if (System.currentTimeMillis() - info.timestamp > cooldownMs) {
            cache.remove(configHash);
            return false;
        }
        return true;
    }

    public String getErrorMessage(String configHash) {
        if (configHash == null) {
            return null;
        }
        FailureInfo info = cache.get(configHash);
        return info != null ? info.errorMessage : null;
    }

    public void clear() {
        cache.clear();
    }
}
