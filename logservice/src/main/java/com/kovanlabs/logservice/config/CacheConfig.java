package com.kovanlabs.logservice.config;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kovanlabs.logservice.service.ServiceApprovalClient;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheLoader<Object, Object> cacheLoader(@Lazy ServiceApprovalClient serviceApprovalClient) {
        return new CacheLoader<Object, Object>() {
            @Override
            public Object load(Object key) {
                if (key instanceof String serviceName) {
                    return serviceApprovalClient.fetchApprovedFromApi(serviceName);
                }
                return false;
            }
        };
    }

    @Bean
    public CacheManager cacheManager(CacheLoader<Object, Object> cacheLoader) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheLoader(cacheLoader);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(24, TimeUnit.HOURS)
                .refreshAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(1000));
        cacheManager.setCacheNames(java.util.List.of("serviceApprovals"));
        return cacheManager;
    }
}
