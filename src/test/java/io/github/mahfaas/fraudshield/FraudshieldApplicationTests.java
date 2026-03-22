package io.github.mahfaas.fraudshield;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requires running infrastructure (PostgreSQL, Redis, Kafka). Will be converted to integration test with Testcontainers.")
class FraudshieldApplicationTests {

	@Test
	void contextLoads() {
	}

}
