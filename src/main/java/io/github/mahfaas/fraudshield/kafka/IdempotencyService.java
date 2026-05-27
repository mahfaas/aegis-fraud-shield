package io.github.mahfaas.fraudshield.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Prevents duplicate processing of the same transaction.
 * <p>
 * Kafka guarantees <em>at-least-once</em> delivery — the same message can be
 * re-delivered after a consumer restart or rebalance. This service uses a Redis
 * {@code SETNX} (set-if-not-exists) with a 24-hour TTL to detect and skip
 * already-processed transaction IDs.
 * </p>
 *
 * <p><strong>Key pattern:</strong> {@code idempotent:{transactionId}}</p>
 *
 * <p><strong>Interview talking point:</strong> This implements the
 * <em>idempotency</em> pattern. Combined with Kafka's at-least-once semantics,
 * it achieves <em>effectively-once</em> processing without requiring Kafka
 * transactions, which have significant throughput overhead.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotent:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    /**
     * Attempts to mark the transaction as being processed.
     *
     * @param transactionId the unique transaction ID
     * @return {@code true} if this transaction was already processed (duplicate);
     *         {@code false} if this is the first time we see it (safe to process)
     */
    public boolean isDuplicate(String transactionId) {
        String key = KEY_PREFIX + transactionId;
        // SETNX: sets key only if it does NOT already exist
        Boolean wasAbsent = redisTemplate.opsForValue().setIfAbsent(key, "1", TTL);
        boolean isNew = Boolean.TRUE.equals(wasAbsent);
        if (!isNew) {
            log.warn("Duplicate transaction detected and skipped: txId={}", transactionId);
        }
        return !isNew; // true = duplicate (key already existed)
    }
}
