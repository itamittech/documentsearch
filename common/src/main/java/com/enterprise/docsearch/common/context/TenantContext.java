package com.enterprise.docsearch.common.context;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TenantContext {
    
    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();
    
    public static void setTenantId(String tenantId) {
        log.debug("Setting tenant context: {}", tenantId);
        currentTenant.set(tenantId);
    }
    
    public static String getTenantId() {
        return currentTenant.get();
    }
    
    public static void clear() {
        log.debug("Clearing tenant context");
        currentTenant.remove();
    }
    
    public static boolean hasTenant() {
        return currentTenant.get() != null;
    }
}
