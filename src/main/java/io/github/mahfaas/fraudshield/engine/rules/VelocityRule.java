package io.github.mahfaas.fraudshield.engine.rules;

import io.github.mahfaas.fraudshield.engine.Rule;
import io.github.mahfaas.fraudshield.engine.RuleResult;
import io.github.mahfaas.fraudshield.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Detects velocity anomalies — too many transactions from the same account
 * within a short time window.
 * <p>
 * Uses Redis INCR + EXPIRE (TTL) to maintain per-account counters.
 * Each key follows the pattern {@code velocity:{accountId}} and auto-expires
 * after the configured window.
 * </p>
 */
@Slf4j
@Component
public class VelocityRule implements Rule {

    private static final String RULE_NAME = "VELOCITY";
    private static final String KEY_PREFIX = "velocity:";

    private final StringRedisTemplate redisTemplate;
    private final AtomicInteger maxTransactions;
    private final AtomicLong windowSeconds;

    public VelocityRule(
            StringRedisTemplate redisTemplate,
            @Value("${fraud.rules.velocity.max-transactions:5}") int maxTransactions,
            @Value("${fraud.rules.velocity.window-seconds:60}") long windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.maxTransactions = new AtomicInteger(maxTransactions);
        this.windowSeconds = new AtomicLong(windowSeconds);
        log.info("VelocityRule initialized: maxTransactions={}, windowSeconds={}",
                maxTransactions, windowSeconds);
    }

    @Override
    public RuleResult evaluate(Transaction transaction) {
        String key = KEY_PREFIX + transaction.getAccountId();

        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds.get()));
        }

        if (count != null && count > maxTransactions.get()) {
            return RuleResult.decline(RULE_NAME,
                    "Account " + transaction.getAccountId()
                            + " exceeded velocity limit: " + count
                            + " transactions in " + windowSeconds.get() + "s (max: " + maxTransactions.get() + ")");
        }

        return RuleResult.approve(RULE_NAME);
    }

    @Override
    public String getName() {
        return RULE_NAME;
    }

    @Override
    public int getOrder() {
        return 30; // after BlacklistRule(10) and AmountAnomalyRule(20)
    }

    public void setMaxTransactions(int max) {
        this.maxTransactions.set(max);
        log.info("Updated velocity maxTransactions to {}", max);
    }

    public void setWindowSeconds(long seconds) {
        this.windowSeconds.set(seconds);
        log.info("Updated velocity windowSeconds to {}", seconds);
    }

    public int getMaxTransactions() {
        return maxTransactions.get();
    }

    public long getWindowSeconds() {
        return windowSeconds.get();
    }
}
