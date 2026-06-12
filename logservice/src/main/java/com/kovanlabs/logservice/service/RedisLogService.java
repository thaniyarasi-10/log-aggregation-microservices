package com.kovanlabs.logservice.service;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.kovanlabs.logservice.model.LogDto;

@Service
public class RedisLogService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisLogService.class);
    private static final String REDIS_KEY_LATEST_ERRORS = "logs:errors:latest";
    private static final int MAX_ERRORS_LIMIT = 20;

    private final RedisTemplate<String, LogDto> redisTemplate;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private final java.util.concurrent.atomic.AtomicLong nextErrorLogTimeMs = new java.util.concurrent.atomic.AtomicLong(0);
    private static final long ERROR_LOG_THROTTLE_MS = 30_000L;

    public RedisLogService(RedisTemplate<String, LogDto> redisTemplate,
                           org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private void logErrorThrottled(String action, Exception e) {
        long now = System.currentTimeMillis();
        long nextLog = nextErrorLogTimeMs.get();
        if (now >= nextLog) {
            if (nextErrorLogTimeMs.compareAndSet(nextLog, now + ERROR_LOG_THROTTLE_MS)) {
                LOGGER.error("Redis connection/operation failure during '{}' (throttled — next log in {}ms): {}", 
                        action, ERROR_LOG_THROTTLE_MS, e.getMessage());
            }
        }
    }

    public boolean isDuplicateAndSet(String fingerprint, long ttlMinutes) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return false;
        }
        String key = "logs:fingerprint:" + fingerprint;
        try {
            Boolean set = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", java.time.Duration.ofMinutes(ttlMinutes));
            return set == null || !set;
        } catch (Exception e) {
            logErrorThrottled("duplicate check", e);
            return false; // Fallback to process to avoid losing logs
        }
    }

    public boolean acquireErrorAnalysisLock(String errorFingerprint, java.time.Duration duration) {
        if (errorFingerprint == null || errorFingerprint.isBlank()) {
            return true; // If no fingerprint, allow analysis to be safe
        }
        String key = "logs:error-rate-limit:" + errorFingerprint;
        try {
            Boolean set = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", duration);
            return set != null && set;
        } catch (Exception e) {
            logErrorThrottled("error rate limit check", e);
            return true; // Proceed with analysis if Redis fails
        }
    }


    public void saveLatestError(LogDto log) {
        if (log == null) {
            LOGGER.warn("Attempt to save null log to Redis");
            return;
        }
        try {
//            LOGGER.debug("Saving error log to Redis for service: {}", log.getService());
            redisTemplate.opsForList().leftPush(REDIS_KEY_LATEST_ERRORS, log);
            redisTemplate.opsForList().trim(REDIS_KEY_LATEST_ERRORS, 0, MAX_ERRORS_LIMIT - 1);
        } catch (Exception e) {
            logErrorThrottled("save latest error", e);
        }
    }


    public List<LogDto> getLatestErrors() {
        try {
            List<LogDto> logs = redisTemplate.opsForList().range(REDIS_KEY_LATEST_ERRORS, 0, -1);
            return logs != null ? logs : Collections.emptyList();
        } catch (Exception e) {
            logErrorThrottled("get latest errors", e);
            return Collections.emptyList();
        }
    }
}
