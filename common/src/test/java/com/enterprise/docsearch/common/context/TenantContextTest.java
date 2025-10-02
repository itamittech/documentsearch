package com.enterprise.docsearch.common.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void testSetAndGetTenantId() {
        String tenantId = "tenant123";
        TenantContext.setTenantId(tenantId);

        assertEquals(tenantId, TenantContext.getTenantId());
    }

    @Test
    void testClearTenantContext() {
        TenantContext.setTenantId("tenant123");
        TenantContext.clear();

        assertNull(TenantContext.getTenantId());
    }

    @Test
    void testTenantContextIsolationBetweenThreads() throws InterruptedException {
        String mainTenantId = "main-tenant";
        String threadTenantId = "thread-tenant";

        TenantContext.setTenantId(mainTenantId);

        Thread testThread = new Thread(() -> {
            TenantContext.setTenantId(threadTenantId);
            assertEquals(threadTenantId, TenantContext.getTenantId());
            TenantContext.clear();
        });

        testThread.start();
        testThread.join();

        // Main thread context should be unaffected
        assertEquals(mainTenantId, TenantContext.getTenantId());
    }

    @Test
    void testGetTenantIdWhenNotSet() {
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void testMultipleSetOperations() {
        TenantContext.setTenantId("tenant1");
        assertEquals("tenant1", TenantContext.getTenantId());

        TenantContext.setTenantId("tenant2");
        assertEquals("tenant2", TenantContext.getTenantId());
    }
}