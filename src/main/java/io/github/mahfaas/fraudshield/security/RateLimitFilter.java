package io.github.mahfaas.fraudshield.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A Servlet Filter that implements a Redis-backed sliding window rate limiter.
 *
 * <p>Uses a Lua script to ensure atomicity of the sliding window evaluation.
 * Limits are applied based on the client's IP address.
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final StringRedisTemplate redisTemplate;

    // Lua script for atomic sliding window rate limiting.
    // Removes elements older than (now - window), checks count, and adds new element if under limit.
    private static final String LUA_SCRIPT =
            "local key = KEYS[1]\n" +
            "local now = tonumber(ARGV[1])\n" +
            "local window = tonumber(ARGV[2])\n" +
            "local limit = tonumber(ARGV[3])\n" +
            "local member = ARGV[4]\n" +
            "redis.call('ZREMRANGEBYSCORE', key, 0, now - window)\n" +
            "local count = redis.call('ZCARD', key)\n" +
            "if count < limit then\n" +
            "    redis.call('ZADD', key, now, member)\n" +
            "    redis.call('PEXPIRE', key, window)\n" +
            "    return count + 1\n" +
            "else\n" +
            "    return -1\n" +
            "end";

    private static final DefaultRedisScript<Long> REDIS_SCRIPT = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!properties.isEnabled() || request.getRequestURI().startsWith("/actuator") || request.getRequestURI().startsWith("/v3/api-docs") || request.getRequestURI().startsWith("/swagger-ui")) {
            // Skip rate limiting for actuator, swagger, and if disabled
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        String key = "rate_limit:" + clientIp;
        long now = Instant.now().toEpochMilli();
        long windowMillis = properties.getWindowSeconds() * 1000L;
        int limit = properties.getMaxRequests();
        String member = now + "-" + UUID.randomUUID().toString();

        try {
            Long currentRequests = redisTemplate.execute(
                    REDIS_SCRIPT,
                    List.of(key),
                    String.valueOf(now),
                    String.valueOf(windowMillis),
                    String.valueOf(limit),
                    member
            );

            if (currentRequests != null && currentRequests == -1L) {
                log.warn("Rate limit exceeded for IP: {}. Limit: {}/{}s", clientIp, limit, properties.getWindowSeconds());
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader("Retry-After", String.valueOf(properties.getWindowSeconds()));
                response.setContentType("application/json");
                response.getWriter().write(String.format("{\"error\": \"Too many requests. Please try again in %d seconds.\"}", properties.getWindowSeconds()));
                return;
            }
            
            // Add current rate limit info to headers (optional but good practice)
            if (currentRequests != null) {
                response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
                response.setHeader("X-RateLimit-Remaining", String.valueOf(limit - currentRequests));
            }

        } catch (Exception e) {
            // If Redis fails, log the error but allow the request to proceed (fail-open)
            log.error("Failed to evaluate rate limit via Redis for IP: {}", clientIp, e);
        }

        filterChain.doFilter(request, response);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty() || !xfHeader.contains(request.getRemoteAddr())) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
