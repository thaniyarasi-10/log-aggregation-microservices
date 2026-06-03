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

    public RedisLogService(RedisTemplate<String, LogDto> redisTemplate) {
        this.redisTemplate = redisTemplate;
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
            LOGGER.error("Failed to save error log to Redis: {}", e.getMessage(), e);
        }
    }


    public List<LogDto> getLatestErrors() {
        try {
            List<LogDto> logs = redisTemplate.opsForList().range(REDIS_KEY_LATEST_ERRORS, 0, -1);
            return logs != null ? logs : Collections.emptyList();
        } catch (Exception e) {
            LOGGER.error("Failed to fetch latest error logs from Redis: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}
