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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        filter = new RateLimitFilter(redisTemplate, objectMapper);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testHealthEndpointBypassesRateLimiting() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/health");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(valueOperations);
    }

    @Test
    void testActuatorEndpointBypassesRateLimiting() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/actuator/metrics");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(valueOperations);
    }

    @Test
    void testRequestPassesWhenBelowRateLimit() throws ServletException, IOException {
        TenantContext.setTenantId("tenant123");
        when(request.getRequestURI()).thenReturn("/api/v1/documents");
        when(valueOperations.increment(anyString())).thenReturn(1L);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(redisTemplate.opsForValue()).increment(anyString());
        verify(redisTemplate).expire(anyString(), eq(Duration.ofMinutes(2)));

        TenantContext.clear();
    }

    @Test
    void testRequestBlockedWhenExceedingRateLimit() throws ServletException, IOException {
        TenantContext.setTenantId("tenant123");
        when(request.getRequestURI()).thenReturn("/api/v1/documents");
        when(valueOperations.increment(anyString())).thenReturn(1001L);

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(429);
        verify(filterChain, never()).doFilter(request, response);
        verify(response).setHeader("X-RateLimit-Limit", "1000");
        verify(response).setHeader("X-RateLimit-Remaining", "0");

        TenantContext.clear();
    }

    @Test
    void testRedisExpirationSetOnFirstRequest() throws ServletException, IOException {
        TenantContext.setTenantId("tenant123");
        when(request.getRequestURI()).thenReturn("/api/v1/documents");
        when(valueOperations.increment(anyString())).thenReturn(1L);

        filter.doFilterInternal(request, response, filterChain);

        verify(redisTemplate).expire(anyString(), eq(Duration.ofMinutes(2)));

        TenantContext.clear();
    }

    @Test
    void testNoRedisExpirationOnSubsequentRequests() throws ServletException, IOException {
        TenantContext.setTenantId("tenant123");
        when(request.getRequestURI()).thenReturn("/api/v1/documents");
        when(valueOperations.increment(anyString())).thenReturn(500L);

        filter.doFilterInternal(request, response, filterChain);

        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));

        TenantContext.clear();
    }

    @Test
    void testFilterPassesWhenTenantContextNotSet() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/documents");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(valueOperations);
    }

    @Test
    void testFailOpenWhenRedisIsDown() throws ServletException, IOException {
        TenantContext.setTenantId("tenant123");
        when(request.getRequestURI()).thenReturn("/api/v1/documents");
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("Redis connection failed"));

        filter.doFilterInternal(request, response, filterChain);

        // Should fail open and allow request
        verify(filterChain).doFilter(request, response);

        TenantContext.clear();
    }

    @Test
    void testRateLimitKeyFormat() throws ServletException, IOException {
        TenantContext.setTenantId("tenant123");
        when(request.getRequestURI()).thenReturn("/api/v1/documents");
        when(valueOperations.increment(anyString())).thenReturn(1L);

        filter.doFilterInternal(request, response, filterChain);

        long currentMinute = System.currentTimeMillis() / 60000;
        String expectedKey = "ratelimit:tenant123:" + currentMinute;
        verify(valueOperations).increment(expectedKey);

        TenantContext.clear();
    }
}