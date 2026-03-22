package io.github.mahfaas.fraudshield.engine.rules;

import io.github.mahfaas.fraudshield.engine.Rule;
import io.github.mahfaas.fraudshield.engine.RuleResult;
import io.github.mahfaas.fraudshield.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Detects impossible travel — transactions from the same account but
 * different countries within a short time window.
 * <p>
 * Stores the last country and timestamp per account in Redis.
 * If the next transaction comes from a different country within 1 hour,
 * it is flagged as suspicious.
 * </p>
 */
@Slf4j
@Component
public class GeoVelocityRule implements Rule {

    private static final String RULE_NAME = "GEO_VELOCITY";
    private static final String KEY_PREFIX = "geo:";
    private static final long WINDOW_SECONDS = 3600; // 1 hour

    private final StringRedisTemplate redisTemplate;

    public GeoVelocityRule(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        log.info("GeoVelocityRule initialized: window={}s", WINDOW_SECONDS);
    }

    @Override
    public RuleResult evaluate(Transaction transaction) {
        String key = KEY_PREFIX + transaction.getAccountId();
        String currentCountry = transaction.getCountry();
        Instant currentTime = transaction.getTimestamp();

        String storedValue = redisTemplate.opsForValue().get(key);

        String newValue = currentCountry + "|" + currentTime.toEpochMilli();
        redisTemplate.opsForValue().set(key, newValue, Duration.ofSeconds(WINDOW_SECONDS));

        if (storedValue != null) {
            String[] parts = storedValue.split("\\|");
            if (parts.length == 2) {
                String previousCountry = parts[0];
                long previousTimestamp = Long.parseLong(parts[1]);
                long elapsedSeconds = currentTime.toEpochMilli() / 1000 - previousTimestamp / 1000;

                if (!previousCountry.equals(currentCountry) && elapsedSeconds < WINDOW_SECONDS) {
                    return RuleResult.manualReview(RULE_NAME,
                            "Impossible travel: " + previousCountry + " → " + currentCountry
                                    + " in " + elapsedSeconds + "s (account " + transaction.getAccountId() + ")");
                }
            }
        }

        return RuleResult.approve(RULE_NAME);
    }

    @Override
    public String getName() {
        return RULE_NAME;
    }

    @Override
    public int getOrder() {
        return 40; // last rule
    }
}
