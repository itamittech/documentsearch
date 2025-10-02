package com.enterprise.docsearch.common.filter;

import com.enterprise.docsearch.common.context.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantValidationFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private TenantValidationFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        filter = new TenantValidationFilter(objectMapper);
    }

    @Test
    void testHealthEndpointBypassesAuthentication() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/health");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(response);
    }

    @Test
    void testActuatorEndpointBypassesAuthentication() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/actuator/health");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testMissingApiKeyReturnsUnauthorized() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/documents");
        when(request.getHeader("X-API-Key")).thenReturn(null);

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void testInvalidApiKeyFormatReturnsUnauthorized() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/documents");
        when(request.getHeader("X-API-Key")).thenReturn("invalid_key");

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void testValidApiKeySetsTenantContext() throws ServletException, IOException {
        String apiKey = "sk_live_tenant123_randomstring";
        when(request.getRequestURI()).thenReturn("/api/v1/documents");
        when(request.getHeader("X-API-Key")).thenReturn(apiKey);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // Tenant context should be cleared after filter execution
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void testTestApiKeyIsAccepted() throws ServletException, IOException {
        String apiKey = "sk_test_tenant456_randomstring";
        when(request.getRequestURI()).thenReturn("/api/v1/documents");
        when(request.getHeader("X-API-Key")).thenReturn(apiKey);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testEmptyApiKeyReturnsUnauthorized() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/documents");
        when(request.getHeader("X-API-Key")).thenReturn("");

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void testTenantContextClearedAfterFilterChain() throws ServletException, IOException {
        String apiKey = "sk_live_tenant789_randomstring";
        when(request.getRequestURI()).thenReturn("/api/v1/documents");
        when(request.getHeader("X-API-Key")).thenReturn(apiKey);

        doAnswer(invocation -> {
            // During filter chain execution, tenant context should be set
            assertEquals("tenant789", TenantContext.getTenantId());
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        // After filter execution, tenant context should be cleared
        assertNull(TenantContext.getTenantId());
    }
}