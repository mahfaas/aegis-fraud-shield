package io.github.mahfaas.fraudshield.kafka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyService — Unit Tests")
class IdempotencyServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        idempotencyService = new IdempotencyService(redisTemplate);
    }

    @Test
    @DisplayName("Should return false (not a duplicate) for a new transaction")
    void shouldReturnFalseForNewTransaction() {
        when(valueOps.setIfAbsent(eq("idempotent:tx-001"), eq("1"), eq(Duration.ofHours(24))))
                .thenReturn(true); // key was absent → new transaction

        boolean result = idempotencyService.isDuplicate("tx-001");

        assertFalse(result, "First time seeing this txId — should NOT be duplicate");
    }

    @Test
    @DisplayName("Should return true (duplicate) when the key already exists in Redis")
    void shouldReturnTrueForDuplicate() {
        when(valueOps.setIfAbsent(eq("idempotent:tx-001"), eq("1"), eq(Duration.ofHours(24))))
                .thenReturn(false); // key already existed → duplicate

        boolean result = idempotencyService.isDuplicate("tx-001");

        assertTrue(result, "Key existed in Redis — should be a duplicate");
    }

    @Test
    @DisplayName("Should use the correct Redis key prefix")
    void shouldUseCorrectKeyPrefix() {
        when(valueOps.setIfAbsent(any(), any(), any())).thenReturn(true);

        idempotencyService.isDuplicate("abc-123");

        verify(valueOps).setIfAbsent(eq("idempotent:abc-123"), eq("1"), eq(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("Should use 24-hour TTL for idempotency key")
    void shouldUse24HourTtl() {
        when(valueOps.setIfAbsent(any(), any(), any())).thenReturn(true);

        idempotencyService.isDuplicate("tx-ttl-test");

        verify(valueOps).setIfAbsent(any(), any(), eq(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("Should handle null return from Redis (treat as new, not duplicate)")
    void shouldHandleNullReturnFromRedis() {
        when(valueOps.setIfAbsent(any(), any(), any())).thenReturn(null);

        boolean result = idempotencyService.isDuplicate("tx-null-case");

        assertTrue(result, "Null from Redis is treated as 'key existed' (safe default to skip)");
    }
}
