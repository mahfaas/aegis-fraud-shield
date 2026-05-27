package io.github.mahfaas.fraudshield.integration;

import io.github.mahfaas.fraudshield.blacklist.*;
import io.github.mahfaas.fraudshield.engine.rules.BlacklistRule;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the PostgreSQL-backed blacklist persistence layer.
 * Uses a real PostgreSQL Testcontainer + Flyway migrations.
 * Validates the full round-trip: REST-layer → Service → Repository → DB → in-memory sync.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({BlacklistService.class, BlacklistRule.class})
@DisplayName("BlacklistService — PostgreSQL Integration Tests")
class PostgresBlacklistIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("fraud_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Let Hibernate create the schema for tests (Flyway disabled in @DataJpaTest)
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private BlacklistRepository repository;

    @Autowired
    private BlacklistService blacklistService;

    @Autowired
    private BlacklistRule blacklistRule;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
        blacklistRule.loadIps(List.of());
        blacklistRule.loadBins(List.of());
    }

    @Test
    @DisplayName("Should persist an IP entry and find it by type")
    void shouldPersistAndFindIpEntry() {
        blacklistService.addEntry(BlacklistType.IP, "10.0.0.1", "test IP");

        List<BlacklistEntity> ips = blacklistService.findByType(BlacklistType.IP);
        assertEquals(1, ips.size());
        assertEquals("10.0.0.1", ips.get(0).getValue());
        assertEquals(BlacklistType.IP, ips.get(0).getType());
        assertNotNull(ips.get(0).getCreatedAt());
    }

    @Test
    @DisplayName("Should persist a BIN entry and find it by type")
    void shouldPersistAndFindBinEntry() {
        blacklistService.addEntry(BlacklistType.BIN, "427600", "test BIN");

        List<BlacklistEntity> bins = blacklistService.findByType(BlacklistType.BIN);
        assertEquals(1, bins.size());
        assertEquals("427600", bins.get(0).getValue());
    }

    @Test
    @DisplayName("Should delete an entry from DB and sync to in-memory rule")
    void shouldDeleteAndSyncToRule() {
        blacklistService.addEntry(BlacklistType.IP, "192.168.1.1", "to be removed");
        assertTrue(blacklistRule.isIpBlacklisted("192.168.1.1"), "Should be in-memory after add");

        blacklistService.removeEntry(BlacklistType.IP, "192.168.1.1");

        List<BlacklistEntity> remaining = blacklistService.findByType(BlacklistType.IP);
        assertTrue(remaining.isEmpty(), "Should be removed from DB");
        assertFalse(blacklistRule.isIpBlacklisted("192.168.1.1"), "Should be removed from in-memory");
    }

    @Test
    @DisplayName("Should sync to in-memory BlacklistRule immediately on add")
    void shouldSyncToInMemoryOnAdd() {
        assertFalse(blacklistRule.isIpBlacklisted("5.5.5.5"), "Should not be blacklisted initially");

        blacklistService.addEntry(BlacklistType.IP, "5.5.5.5", "attack IP");

        assertTrue(blacklistRule.isIpBlacklisted("5.5.5.5"), "Should be in-memory immediately after add");
    }

    @Test
    @DisplayName("Should throw when adding duplicate entry")
    void shouldThrowOnDuplicateEntry() {
        blacklistService.addEntry(BlacklistType.BIN, "555555", "first add");

        assertThrows(IllegalArgumentException.class,
                () -> blacklistService.addEntry(BlacklistType.BIN, "555555", "duplicate"));
    }

    @Test
    @DisplayName("Should return all entries across types")
    void shouldFindAllEntries() {
        blacklistService.addEntry(BlacklistType.IP, "1.2.3.4", "ip1");
        blacklistService.addEntry(BlacklistType.IP, "1.2.3.5", "ip2");
        blacklistService.addEntry(BlacklistType.BIN, "411111", "bin1");

        List<BlacklistEntity> all = blacklistService.findAll();
        assertEquals(3, all.size());
    }

    @Test
    @DisplayName("Should load multiple IPs in bulk via loadIps")
    void shouldBulkLoadIps() {
        List<String> ips = List.of("10.0.0.1", "10.0.0.2", "10.0.0.3");
        blacklistRule.loadIps(ips);

        assertTrue(blacklistRule.isIpBlacklisted("10.0.0.1"));
        assertTrue(blacklistRule.isIpBlacklisted("10.0.0.2"));
        assertTrue(blacklistRule.isIpBlacklisted("10.0.0.3"));
        assertFalse(blacklistRule.isIpBlacklisted("10.0.0.99"));
    }
}
