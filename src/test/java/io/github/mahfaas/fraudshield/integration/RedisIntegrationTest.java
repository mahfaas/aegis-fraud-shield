package io.github.mahfaas.fraudshield.integration;

import com.redis.testcontainers.RedisContainer;
import io.github.mahfaas.fraudshield.engine.RuleResult;
import io.github.mahfaas.fraudshield.engine.rules.VelocityRule;
import io.github.mahfaas.fraudshield.model.Transaction;
import io.github.mahfaas.fraudshield.model.Verdict;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link VelocityRule} backed by a real Redis container.
 * Validates INCR+TTL behavior, per-account isolation, counter expiry, and
 * concurrent access safety without mocks.
 */
@Testcontainers
@DisplayName("VelocityRule — Redis Integration Tests")
class RedisIntegrationTest {

    @Container
    static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7.2-alpine"));

    private static StringRedisTemplate redisTemplate;
    private static LettuceConnectionFactory connectionFactory;

    private VelocityRule velocityRule;

    @BeforeAll
    static void startRedis() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        // max 3 transactions per 2-second window
        velocityRule = new VelocityRule(redisTemplate, 3, 2);
        // flush all keys between tests
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    private Transaction transaction(String accountId) {
        return Transaction.builder()
                .transactionId("tx-" + System.nanoTime())
                .accountId(accountId)
                .cardBin("411111")
                .amount(BigDecimal.valueOf(100))
                .currency("RUB")
                .country("RU")
                .sourceIp("1.2.3.4")
                .timestamp(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should APPROVE when count is within limit")
    void shouldApproveWithinLimit() {
        RuleResult r1 = velocityRule.evaluate(transaction("ACC-1"));
        RuleResult r2 = velocityRule.evaluate(transaction("ACC-1"));
        RuleResult r3 = velocityRule.evaluate(transaction("ACC-1"));

        assertEquals(Verdict.APPROVED, r1.getVerdict());
        assertEquals(Verdict.APPROVED, r2.getVerdict());
        assertEquals(Verdict.APPROVED, r3.getVerdict());
    }

    @Test
    @DisplayName("Should DECLINE when count exceeds limit")
    void shouldDeclineWhenExceedsLimit() {
        velocityRule.evaluate(transaction("ACC-2"));
        velocityRule.evaluate(transaction("ACC-2"));
        velocityRule.evaluate(transaction("ACC-2"));
        RuleResult result = velocityRule.evaluate(transaction("ACC-2")); // 4th

        assertEquals(Verdict.DECLINED, result.getVerdict());
        assertEquals("VELOCITY", result.getRuleName());
        assertTrue(result.getReason().contains("ACC-2"));
    }

    @Test
    @DisplayName("Should track accounts independently")
    void shouldTrackPerAccount() {
        // ACC-A within limit
        for (int i = 0; i < 3; i++) velocityRule.evaluate(transaction("ACC-A"));
        // ACC-B only one tx
        RuleResult accB = velocityRule.evaluate(transaction("ACC-B"));

        RuleResult acc4th = velocityRule.evaluate(transaction("ACC-A")); // 4th for A
        assertEquals(Verdict.DECLINED, acc4th.getVerdict());
        assertEquals(Verdict.APPROVED, accB.getVerdict());
    }

    @Test
    @DisplayName("Should reset after TTL expires (real time)")
    void shouldResetAfterTtlExpires() throws InterruptedException {
        // Exhaust limit
        for (int i = 0; i < 4; i++) velocityRule.evaluate(transaction("ACC-TTL"));
        RuleResult declined = velocityRule.evaluate(transaction("ACC-TTL"));
        assertEquals(Verdict.DECLINED, declined.getVerdict());

        // Wait for the 2-second TTL to expire
        Thread.sleep(Duration.ofSeconds(3).toMillis());

        RuleResult after = velocityRule.evaluate(transaction("ACC-TTL"));
        assertEquals(Verdict.APPROVED, after.getVerdict(),
                "Counter should have reset after TTL expiry");
    }

    @Test
    @DisplayName("Should survive concurrent increments without data corruption")
    void shouldHandleConcurrentAccess() throws InterruptedException {
        // 10 concurrent threads each send 1 transaction for ACC-CONCURRENT
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<RuleResult>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> velocityRule.evaluate(transaction("ACC-CONCURRENT"))));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        // The Redis counter must equal exactly 10 (no lost updates)
        String key = "velocity:ACC-CONCURRENT";
        String rawCount = redisTemplate.opsForValue().get(key);
        assertNotNull(rawCount, "Redis key must exist");
        assertEquals(10L, Long.parseLong(rawCount), "All 10 increments must be counted");
    }

    @Test
    @DisplayName("Should reflect runtime threshold update immediately")
    void shouldReflectRuntimeThresholdUpdate() {
        velocityRule.setMaxTransactions(1);

        velocityRule.evaluate(transaction("ACC-RT")); // 1st — approved
        RuleResult second = velocityRule.evaluate(transaction("ACC-RT")); // 2nd — declined

        assertEquals(Verdict.DECLINED, second.getVerdict());
    }
}
