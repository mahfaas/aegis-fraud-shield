package io.github.mahfaas.fraudshield.audit;

import io.github.mahfaas.fraudshield.model.Verdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Disabled;

/**
 * Integration tests for {@link AuditLogSpecification} using a real PostgreSQL container.
 * Verifies that the dynamic Specifications map correctly to actual SQL queries
 * that Hibernate executes against Postgres.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("AuditLogSpecification — PostgreSQL Integration Tests")
@Disabled("Requires Docker")
class AuditLogSpecificationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("fraud_audit_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Let Hibernate create the schema for tests
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private AuditLogRepository repository;

    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        // 1. APPROVED, Acc-1, tx-1, today
        repository.save(AuditLogEntity.builder()
                .transactionId("tx-1").accountId("Acc-1")
                .amount(BigDecimal.TEN).verdict(Verdict.APPROVED)
                .processedAt(now).build());

        // 2. DECLINED, Acc-1, tx-2, yesterday
        repository.save(AuditLogEntity.builder()
                .transactionId("tx-2").accountId("Acc-1")
                .amount(BigDecimal.valueOf(5000)).verdict(Verdict.DECLINED)
                .reasons("VELOCITY")
                .processedAt(now.minus(1, ChronoUnit.DAYS)).build());

        // 3. DECLINED, Acc-2, tx-3, today
        repository.save(AuditLogEntity.builder()
                .transactionId("tx-3").accountId("Acc-2")
                .amount(BigDecimal.valueOf(100)).verdict(Verdict.DECLINED)
                .reasons("BLACKLIST")
                .processedAt(now).build());

        // 4. MANUAL_REVIEW, Acc-3, tx-4, tomorrow (future)
        repository.save(AuditLogEntity.builder()
                .transactionId("tx-4").accountId("Acc-3")
                .amount(BigDecimal.valueOf(500)).verdict(Verdict.MANUAL_REVIEW)
                .processedAt(now.plus(1, ChronoUnit.DAYS)).build());
    }

    @Test
    @DisplayName("Empty request returns all records")
    void emptyRequestReturnsAll() {
        AuditSearchRequest request = new AuditSearchRequest(null, null, null, null, null);
        Specification<AuditLogEntity> spec = AuditLogSpecification.fromRequest(request);

        List<AuditLogEntity> result = repository.findAll(spec);

        assertEquals(4, result.size());
    }

    @Test
    @DisplayName("Filter by transactionId")
    void filterByTransactionId() {
        AuditSearchRequest request = new AuditSearchRequest("tx-2", null, null, null, null);
        Specification<AuditLogEntity> spec = AuditLogSpecification.fromRequest(request);

        List<AuditLogEntity> result = repository.findAll(spec);

        assertEquals(1, result.size());
        assertEquals("tx-2", result.get(0).getTransactionId());
    }

    @Test
    @DisplayName("Filter by accountId")
    void filterByAccountId() {
        AuditSearchRequest request = new AuditSearchRequest(null, "Acc-1", null, null, null);
        Specification<AuditLogEntity> spec = AuditLogSpecification.fromRequest(request);

        List<AuditLogEntity> result = repository.findAll(spec);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(e -> e.getAccountId().equals("Acc-1")));
    }

    @Test
    @DisplayName("Filter by verdict")
    void filterByVerdict() {
        AuditSearchRequest request = new AuditSearchRequest(null, null, Verdict.DECLINED, null, null);
        Specification<AuditLogEntity> spec = AuditLogSpecification.fromRequest(request);

        List<AuditLogEntity> result = repository.findAll(spec);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(e -> e.getVerdict() == Verdict.DECLINED));
    }

    @Test
    @DisplayName("Filter by date range (startDate AND endDate)")
    void filterByDateRange() {
        // From half a day ago to half a day in the future (should catch 'now')
        Instant start = now.minus(12, ChronoUnit.HOURS);
        Instant end = now.plus(12, ChronoUnit.HOURS);

        AuditSearchRequest request = new AuditSearchRequest(null, null, null, start, end);
        Specification<AuditLogEntity> spec = AuditLogSpecification.fromRequest(request);

        List<AuditLogEntity> result = repository.findAll(spec);

        assertEquals(2, result.size(), "Should only find 'now' entries (tx-1 and tx-3)");
        assertTrue(result.stream().anyMatch(e -> e.getTransactionId().equals("tx-1")));
        assertTrue(result.stream().anyMatch(e -> e.getTransactionId().equals("tx-3")));
    }

    @Test
    @DisplayName("Compound filter: verdict AND date range")
    void filterCompound() {
        // Today to future
        Instant start = now.minus(1, ChronoUnit.HOURS);
        
        AuditSearchRequest request = new AuditSearchRequest(null, null, Verdict.DECLINED, start, null);
        Specification<AuditLogEntity> spec = AuditLogSpecification.fromRequest(request);

        List<AuditLogEntity> result = repository.findAll(spec);

        assertEquals(1, result.size(), "Only tx-3 is declined and today or later");
        assertEquals("tx-3", result.get(0).getTransactionId());
    }
}
