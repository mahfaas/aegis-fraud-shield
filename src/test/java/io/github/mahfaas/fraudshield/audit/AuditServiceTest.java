package io.github.mahfaas.fraudshield.audit;

import io.github.mahfaas.fraudshield.api.PagedResponse;
import io.github.mahfaas.fraudshield.cases.FraudCase;
import io.github.mahfaas.fraudshield.cases.FraudCaseService;
import io.github.mahfaas.fraudshield.model.Transaction;
import io.github.mahfaas.fraudshield.model.Verdict;
import io.github.mahfaas.fraudshield.model.VerdictedTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuditService}.
 *
 * <h3>Testing techniques demonstrated</h3>
 * <ul>
 *   <li>{@link Mock} on {@link AuditLogRepository} — pure unit test, no DB needed</li>
 *   <li>{@link ArgumentCaptor} — captures the entity passed to {@code repository.save()}
 *       so we can assert its fields without relying on the return value</li>
 *   <li>{@link ParameterizedTest} + {@link EnumSource} — tests
 *       {@link AuditService#record} for every {@link Verdict} value automatically</li>
 *   <li>Stream aggregation tests — verifies {@link AuditService#getVerdictStats()} correctly
 *       maps JPQL result rows</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository repository;

    @Mock
    private FraudCaseService fraudCaseService;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        // lenient: createFromVerdict is only called by tests that produce MANUAL_REVIEW verdicts.
        // Tests covering other code paths (getPage, getVerdictStats) don't invoke it,
        // so we use lenient() to suppress UnnecessaryStubbingException.
        lenient().when(fraudCaseService.createFromVerdict(any())).thenReturn(new FraudCase());
        auditService = new AuditService(repository, fraudCaseService);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private VerdictedTransaction buildVerdictedTx(Verdict verdict, String txId, BigDecimal amount) {
        Transaction tx = Transaction.builder()
                .transactionId(txId)
                .accountId("ACC-001")
                .cardBin("411111")
                .amount(amount)
                .currency("USD")
                .country("US")
                .sourceIp("1.2.3.4")
                .timestamp(Instant.now())
                .build();

        return VerdictedTransaction.builder()
                .transaction(tx)
                .verdict(verdict)
                .reasons(verdict == Verdict.APPROVED ? List.of() : List.of("[RULE] triggered"))
                .totalRiskScore(verdict == Verdict.DECLINED ? 100 : verdict == Verdict.MANUAL_REVIEW ? 50 : 0)
                .processedAt(Instant.now())
                .build();
    }

    private AuditLogEntity buildEntity(Long id, String txId, Verdict verdict, BigDecimal amount) {
        return AuditLogEntity.builder()
                .id(id)
                .transactionId(txId)
                .accountId("ACC-001")
                .amount(amount)
                .verdict(verdict)
                .reasons(verdict == Verdict.APPROVED ? null : "[RULE] triggered")
                .processedAt(Instant.now())
                .build();
    }

    // ── record() tests ────────────────────────────────────────────────────────

    /**
     * Tests that record() persists an entity for every possible Verdict.
     * Uses @EnumSource to automatically iterate over all enum constants —
     * no risk of forgetting a new verdict in the future.
     */
    @ParameterizedTest(name = "[{index}] verdict={0}")
    @EnumSource(Verdict.class)
    @DisplayName("record() persists an AuditLogEntity for every Verdict value")
    void recordPersistsEntityForEveryVerdict(Verdict verdict) {
        VerdictedTransaction vtx = buildVerdictedTx(verdict, "tx-" + verdict, BigDecimal.valueOf(1000));

        AuditLogEntity savedEntity = buildEntity(1L, "tx-" + verdict, verdict, BigDecimal.valueOf(1000));
        when(repository.save(any())).thenReturn(savedEntity);

        AuditLogEntity result = auditService.record(vtx);

        assertNotNull(result);
        verify(repository, times(1)).save(any(AuditLogEntity.class));
    }

    @Test
    @DisplayName("record() maps transaction fields correctly to the entity")
    void recordMapsFieldsCorrectly() {
        VerdictedTransaction vtx = buildVerdictedTx(Verdict.DECLINED, "tx-999", BigDecimal.valueOf(750_000));
        // Capture what gets passed to save()
        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        auditService.record(vtx);

        AuditLogEntity captured = captor.getValue();
        assertEquals("tx-999",                   captured.getTransactionId());
        assertEquals("ACC-001",                  captured.getAccountId());
        assertEquals(BigDecimal.valueOf(750_000), captured.getAmount());
        assertEquals(Verdict.DECLINED,            captured.getVerdict());
        assertNotNull(captured.getProcessedAt());
        // Reasons list should be joined with '|'
        assertNotNull(captured.getReasons());
        assertFalse(captured.getReasons().isBlank());
    }

    @Test
    @DisplayName("record() stores reasons joined with pipe delimiter")
    void recordJoinsReasonsWithPipe() {
        Transaction tx = Transaction.builder()
                .transactionId("tx-multi").accountId("ACC-X").cardBin("411111")
                .amount(BigDecimal.TEN).currency("USD").country("US")
                .sourceIp("1.2.3.4").timestamp(Instant.now()).build();

        VerdictedTransaction vtx = VerdictedTransaction.builder()
                .transaction(tx)
                .verdict(Verdict.DECLINED)
                .reasons(List.of("[BLACKLIST] blocked", "[VELOCITY] too fast"))
                .totalRiskScore(100)
                .processedAt(Instant.now())
                .build();

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        auditService.record(vtx);

        String reasons = captor.getValue().getReasons();
        assertEquals("[BLACKLIST] blocked|[VELOCITY] too fast", reasons);
    }

    // ── getVerdictStats() — stream aggregation ──────────────────────────────

    @Test
    @DisplayName("getVerdictStats() maps JPQL GROUP BY rows to a typed Verdict->Long map")
    void getVerdictStatsMapsRows() {
        // Simulate JPQL GROUP BY result: Object[] {Verdict, count}
        List<Object[]> mockRows = List.of(
                new Object[]{Verdict.APPROVED,      800L},
                new Object[]{Verdict.DECLINED,      150L},
                new Object[]{Verdict.MANUAL_REVIEW,  50L}
        );
        when(repository.countByVerdict()).thenReturn(mockRows);

        Map<Verdict, Long> stats = auditService.getVerdictStats();

        assertEquals(800L, stats.get(Verdict.APPROVED));
        assertEquals(150L, stats.get(Verdict.DECLINED));
        assertEquals(50L,  stats.get(Verdict.MANUAL_REVIEW));
        assertEquals(3,    stats.size());
    }

    @Test
    @DisplayName("getVerdictStats() returns empty map when no transactions exist")
    void getVerdictStatsEmptyWhenNoData() {
        when(repository.countByVerdict()).thenReturn(List.of());

        Map<Verdict, Long> stats = auditService.getVerdictStats();

        assertTrue(stats.isEmpty());
    }

    // ── getPage() — pagination wrapper ──────────────────────────────────────

    @Test
    @DisplayName("getPage() returns correctly structured PagedResponse<AuditLogSummary>")
    void getPageReturnsPaginatedResponse() {
        AuditLogEntity e1 = buildEntity(1L, "tx-001", Verdict.APPROVED, BigDecimal.valueOf(500));
        AuditLogEntity e2 = buildEntity(2L, "tx-002", Verdict.DECLINED, BigDecimal.valueOf(600_000));

        when(repository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(e1, e2)));

        PagedResponse<AuditLogSummary> response = auditService.getPage(0, 20);

        assertEquals(0,  response.page());
        assertEquals(20, response.size());
        assertEquals(2,  response.content().size());
        assertEquals(2,  response.totalElements());
        assertEquals(1,  response.totalPages());
        assertEquals("tx-001", response.content().get(0).transactionId());
        assertEquals("tx-002", response.content().get(1).transactionId());
    }

    @Test
    @DisplayName("getPage() projects AuditLogEntity to AuditLogSummary correctly")
    void getPageProjectsEntityToSummary() {
        AuditLogEntity entity = buildEntity(42L, "tx-XYZ", Verdict.MANUAL_REVIEW, BigDecimal.valueOf(250_000));
        entity.setReasons("[AMOUNT] exceeded|[VELOCITY] too fast");

        when(repository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        PagedResponse<AuditLogSummary> response = auditService.getPage(0, 10);
        AuditLogSummary summary = response.content().getFirst();

        assertEquals(42L,                    summary.id());
        assertEquals("tx-XYZ",               summary.transactionId());
        assertEquals(Verdict.MANUAL_REVIEW,   summary.verdict());
        assertEquals(2,                       summary.reasons().size(), "Reasons should be split by '|'");
    }

    // ── getDailyVerdictBreakdown() — native query row mapping ──────────────

    @Test
    @DisplayName("getDailyVerdictBreakdown() maps native query rows to DailyVerdictStats")
    void getDailyVerdictBreakdownMapsRows() {
        List<Object[]> mockRows = List.of(
                new Object[]{java.sql.Date.valueOf("2026-07-08"), 100L, 20L, 5L},
                new Object[]{java.sql.Date.valueOf("2026-07-09"), 90L, 15L, 8L}
        );
        when(repository.dailyVerdictBreakdown(any())).thenReturn(mockRows);

        List<AuditService.DailyVerdictStats> stats = auditService.getDailyVerdictBreakdown(7);

        assertEquals(2, stats.size());
        assertEquals(java.time.LocalDate.parse("2026-07-08"), stats.get(0).date());
        assertEquals(100L, stats.get(0).approved());
        assertEquals(20L,  stats.get(0).declined());
        assertEquals(5L,   stats.get(0).manualReview());
    }

    @Test
    @DisplayName("getDailyVerdictBreakdown() returns empty list when no data in window")
    void getDailyVerdictBreakdownEmptyWhenNoData() {
        when(repository.dailyVerdictBreakdown(any())).thenReturn(List.of());

        List<AuditService.DailyVerdictStats> stats = auditService.getDailyVerdictBreakdown(7);

        assertTrue(stats.isEmpty());
    }

    // ── getTopDecliningAccounts() — native query row mapping ───────────────

    @Test
    @DisplayName("getTopDecliningAccounts() maps native query rows to AccountRiskSummary")
    void getTopDecliningAccountsMapsRows() {
        List<Object[]> mockRows = List.of(
                new Object[]{"ACC-777", 12L, BigDecimal.valueOf(950_000)},
                new Object[]{"ACC-042", 5L,  BigDecimal.valueOf(200_000)}
        );
        when(repository.topDecliningAccounts(any(), eq(10))).thenReturn(mockRows);

        List<AuditService.AccountRiskSummary> stats = auditService.getTopDecliningAccounts(30, 10);

        assertEquals(2, stats.size());
        assertEquals("ACC-777",                     stats.get(0).accountId());
        assertEquals(12L,                            stats.get(0).declineCount());
        assertEquals(BigDecimal.valueOf(950_000),    stats.get(0).declineAmount());
    }

    @Test
    @DisplayName("getTopDecliningAccounts() returns empty list when no declines in window")
    void getTopDecliningAccountsEmptyWhenNoData() {
        when(repository.topDecliningAccounts(any(), eq(5))).thenReturn(List.of());

        List<AuditService.AccountRiskSummary> stats = auditService.getTopDecliningAccounts(30, 5);

        assertTrue(stats.isEmpty());
    }

    // ── getRuleTriggerFrequency() — reason-string extraction + counting ────

    @Test
    @DisplayName("getRuleTriggerFrequency() counts rule names extracted from reasons, ordered by frequency")
    void getRuleTriggerFrequencyCountsAndOrders() {
        AuditLogEntity e1 = buildEntity(1L, "tx-001", Verdict.DECLINED, BigDecimal.valueOf(1000));
        e1.setReasons("[BLACKLIST] blocked|[VELOCITY] too fast");
        AuditLogEntity e2 = buildEntity(2L, "tx-002", Verdict.MANUAL_REVIEW, BigDecimal.valueOf(2000));
        e2.setReasons("[VELOCITY] too fast");
        AuditLogEntity e3 = buildEntity(3L, "tx-003", Verdict.APPROVED, BigDecimal.valueOf(500));
        e3.setReasons(null); // no reasons — must be filtered out, not throw

        when(repository.findRecentEntries(any(), any()))
                .thenReturn(new PageImpl<>(List.of(e1, e2, e3)));

        List<AuditService.RuleTriggerCount> result = auditService.getRuleTriggerFrequency(7, 10);

        assertEquals(2, result.size());
        assertEquals("VELOCITY",  result.get(0).ruleName());
        assertEquals(2L,          result.get(0).triggerCount());
        assertEquals("BLACKLIST", result.get(1).ruleName());
        assertEquals(1L,          result.get(1).triggerCount());
    }

    @Test
    @DisplayName("getRuleTriggerFrequency() truncates to the requested limit")
    void getRuleTriggerFrequencyRespectsLimit() {
        AuditLogEntity e1 = buildEntity(1L, "tx-001", Verdict.DECLINED, BigDecimal.valueOf(1000));
        e1.setReasons("[BLACKLIST] blocked|[VELOCITY] too fast|[AMOUNT] exceeded");

        when(repository.findRecentEntries(any(), any()))
                .thenReturn(new PageImpl<>(List.of(e1)));

        List<AuditService.RuleTriggerCount> result = auditService.getRuleTriggerFrequency(7, 2);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("getRuleTriggerFrequency() returns empty list when no data in window")
    void getRuleTriggerFrequencyEmptyWhenNoData() {
        when(repository.findRecentEntries(any(), any())).thenReturn(new PageImpl<>(List.of()));

        List<AuditService.RuleTriggerCount> result = auditService.getRuleTriggerFrequency(7, 10);

        assertTrue(result.isEmpty());
    }
}
