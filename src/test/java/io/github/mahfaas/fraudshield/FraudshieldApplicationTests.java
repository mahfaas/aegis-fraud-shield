package io.github.mahfaas.fraudshield;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test verifying the Spring application context loads without errors.
 * <p>
 * Uses mocked/in-memory configuration to avoid requiring real infrastructure.
 * Full integration tests with real containers live in the {@code integration} package.
 * </p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        // Use H2/embedded config would need full app refactor,
        // instead disable components that need real infra at test time
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "spring.main.allow-bean-definition-overriding=true"
})
class FraudshieldApplicationTests {

    @Test
    void contextLoads() {
        // Verifies Spring context wires all non-infra beans without exceptions.
        // Rule Engine, Metrics, Validators, and Controllers should all load correctly.
    }
}
