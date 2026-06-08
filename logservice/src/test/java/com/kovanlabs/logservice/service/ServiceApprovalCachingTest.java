package com.kovanlabs.logservice.service;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.CacheManager;

@SpringBootTest
public class ServiceApprovalCachingTest {

    @SpyBean
    private ServiceApprovalClient serviceApprovalClient;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private com.github.benmanes.caffeine.cache.CacheLoader<Object, Object> cacheLoader;

    @BeforeEach
    void setUp() {
        // Clear the cache before each test to ensure a clean state
        var cache = cacheManager.getCache("serviceApprovals");
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    public void testCachingBehavior() {
        // Stub the fetchApprovedFromApi method
        doReturn(true).when(serviceApprovalClient).fetchApprovedFromApi("payment-service");
        doReturn(false).when(serviceApprovalClient).fetchApprovedFromApi("unapproved-service");

        // 1. First call - Cache Miss
        boolean result1 = serviceApprovalClient.isApproved("payment-service");
        assertTrue(result1);
        verify(serviceApprovalClient, times(1)).fetchApprovedFromApi("payment-service");

        // 2. Second call - Cache Hit
        boolean result2 = serviceApprovalClient.isApproved("payment-service");
        assertTrue(result2);
        // fetchApprovedFromApi should still have been called only once
        verify(serviceApprovalClient, times(1)).fetchApprovedFromApi("payment-service");

        // 3. Case insensitivity check
        boolean result3 = serviceApprovalClient.isApproved("PAYMENT-SERVICE");
        assertTrue(result3);
        verify(serviceApprovalClient, times(1)).fetchApprovedFromApi("payment-service");

        // 4. Test caching of unapproved results (false)
        boolean unapprovedResult1 = serviceApprovalClient.isApproved("unapproved-service");
        assertFalse(unapprovedResult1);
        verify(serviceApprovalClient, times(1)).fetchApprovedFromApi("unapproved-service");

        boolean unapprovedResult2 = serviceApprovalClient.isApproved("unapproved-service");
        assertFalse(unapprovedResult2);
        verify(serviceApprovalClient, times(1)).fetchApprovedFromApi("unapproved-service");
    }

    @Test
    public void testCacheLoaderRefreshesFromApi() throws Exception {
        doReturn(true).when(serviceApprovalClient).fetchApprovedFromApi("payment-service");

        // Directly call the loader to verify it delegates correctly
        Object result = cacheLoader.load("payment-service");
        assertEquals(true, result);

        verify(serviceApprovalClient, times(1)).fetchApprovedFromApi("payment-service");
    }
}
