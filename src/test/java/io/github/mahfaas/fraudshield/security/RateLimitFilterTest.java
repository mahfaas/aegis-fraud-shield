package io.github.mahfaas.fraudshield.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RateLimitFilter}.
 *
 * <h3>Testing approach</h3>
 * <ul>
 *   <li>Mocking {@link StringRedisTemplate} to simulate Lua script execution results.</li>
 *   <li>Using Spring's {@link MockHttpServletRequest} and {@link MockHttpServletResponse}
 *       to simulate the HTTP context without bringing up a web server.</li>
 *   <li>Verifying fail-open behavior if Redis is unavailable.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private FilterChain filterChain;

    private RateLimitProperties properties;
    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setMaxRequests(5);
        properties.setWindowSeconds(60);

        rateLimitFilter = new RateLimitFilter(properties, redisTemplate);
    }

    @Test
    @DisplayName("Allows request when under rate limit (script returns count)")
    void allowsRequestWhenUnderLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cases");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Simulate Lua script returning 3 (which means 3 requests in the window)
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(3L);

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Chain continues
        verify(filterChain).doFilter(request, response);
        // Headers are set
        assertEquals("5", response.getHeader("X-RateLimit-Limit"));
        assertEquals("2", response.getHeader("X-RateLimit-Remaining"));
    }

    @Test
    @DisplayName("Blocks request with 429 when over rate limit (script returns -1)")
    void blocksRequestWhenOverLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cases");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Simulate Lua script returning -1 (limit exceeded)
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(-1L);

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Chain is stopped
        verify(filterChain, never()).doFilter(request, response);
        // Status 429 and Retry-After
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.getStatus());
        assertEquals("60", response.getHeader("Retry-After"));
    }

    @Test
    @DisplayName("Bypasses rate limiting completely if properties.enabled = false")
    void bypassesWhenDisabled() throws Exception {
        properties.setEnabled(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cases");
        MockHttpServletResponse response = new MockHttpServletResponse();

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Bypasses rate limiting for /actuator paths")
    void bypassesForActuator() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Fails open (allows request) if Redis throws an exception")
    void failsOpenOnRedisError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cases");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Simulate Redis being down
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Redis connection refused"));

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Chain should still continue
        verify(filterChain).doFilter(request, response);
        // Ensure no 429 is thrown
        assertEquals(HttpStatus.OK.value(), response.getStatus());
    }

    @Test
    @DisplayName("Uses X-Forwarded-For if available to determine IP")
    void usesXForwardedFor() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cases");
        request.setRemoteAddr("192.168.1.100"); // Gateway IP
        request.addHeader("X-Forwarded-For", "203.0.113.5, 192.168.1.100");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1L);

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Verify the key passed to Redis script starts with the client IP
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("rate_limit:203.0.113.5")), anyString(), anyString(), anyString(), anyString());
    }
}
