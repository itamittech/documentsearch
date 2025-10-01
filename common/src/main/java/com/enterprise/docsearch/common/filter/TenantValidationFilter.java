package com.enterprise.docsearch.common.filter;

import com.enterprise.docsearch.common.context.TenantContext;
import com.enterprise.docsearch.common.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
@RequiredArgsConstructor
@Slf4j
public class TenantValidationFilter extends OncePerRequestFilter {
    
    private final ObjectMapper objectMapper;
    
    private static final String API_KEY_HEADER = "X-API-Key";
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) 
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        
        // Skip authentication for health and actuator endpoints
        if (path.startsWith("/actuator") || path.equals("/health")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String apiKey = request.getHeader(API_KEY_HEADER);
        
        if (apiKey == null || apiKey.isEmpty()) {
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, 
                    "Missing API key");
            return;
        }
        
        // Extract tenant ID from API key
        // Format: sk_live_tenant123_randomstring or sk_test_tenant123_randomstring
        String tenantId = extractTenantId(apiKey);
        
        if (tenantId == null) {
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, 
                    "Invalid API key format");
            return;
        }
        
        // Validate API key (simplified - in production, check against database)
        if (!validateApiKey(apiKey, tenantId)) {
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, 
                    "Invalid API key");
            return;
        }
        
        // Set tenant context
        TenantContext.setTenantId(tenantId);
        log.debug("Tenant context set: {}", tenantId);
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
    
    private String extractTenantId(String apiKey) {
        try {
            // Expected format: sk_{env}_{tenant_id}_{random}
            String[] parts = apiKey.split("_");
            if (parts.length >= 3) {
                return parts[2]; // tenant_id
            }
        } catch (Exception e) {
            log.error("Error extracting tenant ID from API key", e);
        }
        return null;
    }
    
    private boolean validateApiKey(String apiKey, String tenantId) {
        // Simplified validation for prototype
        // In production: 
        // 1. Hash the API key
        // 2. Query database for matching hash
        // 3. Check if key is active and not expired
        // 4. Update last_used_at timestamp
        
        // For prototype, accept any key with valid format
        return apiKey.startsWith("sk_live_") || apiKey.startsWith("sk_test_");
    }
    
    private void sendErrorResponse(HttpServletResponse response, int status, String message) 
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        
        ApiResponse<Object> errorResponse = ApiResponse.error(message);
        String jsonResponse = objectMapper.writeValueAsString(errorResponse);
        
        response.getWriter().write(jsonResponse);
    }
}
