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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final int MAX_REQUESTS_PER_MINUTE = 1000;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip rate limiting for health checks
        if (path.startsWith("/actuator") || path.equals("/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        String tenantId = TenantContext.getTenantId();

        if (tenantId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!checkRateLimit(tenantId)) {
            sendRateLimitError(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean checkRateLimit(String tenantId) {
        long currentMinute = System.currentTimeMillis() / 60000;
        String key = "ratelimit:" + tenantId + ":" + currentMinute;

        try {
            Long requests = redisTemplate.opsForValue().increment(key);

            if (requests == null) {
                requests = 1L;
            }

            if (requests == 1) {
                // Set expiration on first request
                redisTemplate.expire(key, Duration.ofMinutes(2));
            }

            if (requests > MAX_REQUESTS_PER_MINUTE) {
                log.warn("Rate limit exceeded for tenant: {} ({} requests)",
                        tenantId, requests);
                return false;
            }

            log.debug("Rate limit check for tenant {}: {}/{}",
                    tenantId, requests, MAX_REQUESTS_PER_MINUTE);

            return true;

        } catch (Exception e) {
            log.error("Error checking rate limit", e);
            // Fail open - allow request if Redis is down
            return true;
        }
    }

    private void sendRateLimitError(HttpServletResponse response) throws IOException {
        response.setStatus(429); // Too Many Requests
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-RateLimit-Limit", String.valueOf(MAX_REQUESTS_PER_MINUTE));
        response.setHeader("X-RateLimit-Remaining", "0");

        ApiResponse<Object> errorResponse = ApiResponse.error(
                "Rate limit exceeded. Maximum " + MAX_REQUESTS_PER_MINUTE +
                        " requests per minute allowed.");

        String jsonResponse = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(jsonResponse);
    }
}