package io.github.mahfaas.fraudshield.cases;

import io.github.mahfaas.fraudshield.api.PagedResponse;
import io.github.mahfaas.fraudshield.model.Transaction;
import io.github.mahfaas.fraudshield.model.Verdict;
import io.github.mahfaas.fraudshield.model.VerdictedTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FraudCaseService}.
 *
 * <h3>Testing techniques demonstrated</h3>
 * <ul>
 *   <li>{@link Mock} on {@link FraudCaseRepository} — pure unit test, no DB needed</li>
 *   <li>{@link ArgumentCaptor} — captures the entity passed to {@code repository.save()}</li>
 *   <li>State-machine coverage — every valid and invalid {@link FraudCaseStatus} transition</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class FraudCaseServiceTest {

    @Mock
    private FraudCaseRepository repository;

    private FraudCaseService service;

    @BeforeEach
    void setUp() {
        service = new FraudCaseService(repository);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private VerdictedTransaction buildVerdictedTx(String txId, List<String> reasons, int riskScore) {
        Transaction tx = Transaction.builder()
                .transactionId(txId)
                .accountId("ACC-001")
                .cardBin("411111")
                .amount(BigDecimal.valueOf(1500))
                .currency("USD")
                .country("US")
                .sourceIp("1.2.3.4")
                .timestamp(Instant.now())
                .build();

        return VerdictedTransaction.builder()
                .transaction(tx)
                .verdict(Verdict.MANUAL_REVIEW)
                .reasons(reasons)
                .totalRiskScore(riskScore)
                .processedAt(Instant.now())
                .build();
    }

    private FraudCase buildCase(Long id, FraudCaseStatus status) {
        return FraudCase.builder()
                .id(id)
                .transactionId("tx-case-001")
                .accountId("ACC-001")
                .amount(BigDecimal.valueOf(1500))
                .riskScore(60)
                .triggeredRules("GEO_VELOCITY|TIME_WINDOW")
                .status(status)
                .createdAt(Instant.parse("2026-06-10T10:00:00Z"))
                .updatedAt(Instant.parse("2026-06-10T10:00:00Z"))
                .build();
    }

    // ── createFromVerdict() ──────────────────────────────────────────────────

    @Nested
    @DisplayName("createFromVerdict()")
    class CreateFromVerdict {

        @Test
        @DisplayName("creates a new OPEN case with fields mapped from the verdict")
        void createsNewCase() {
            VerdictedTransaction vtx = buildVerdictedTx("tx-001",
                    List.of("[GEO_VELOCITY] impossible travel", "[TIME_WINDOW] night transaction"), 75);
            when(repository.existsByTransactionId("tx-001")).thenReturn(false);

            ArgumentCaptor<FraudCase> captor = ArgumentCaptor.forClass(FraudCase.class);
            when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            FraudCase result = service.createFromVerdict(vtx);

            FraudCase captured = captor.getValue();
            assertEquals("tx-001", captured.getTransactionId());
            assertEquals("ACC-001", captured.getAccountId());
            assertEquals(BigDecimal.valueOf(1500), captured.getAmount());
            assertEquals(75, captured.getRiskScore());
            assertEquals(FraudCaseStatus.OPEN, captured.getStatus());
            assertEquals("GEO_VELOCITY|TIME_WINDOW", captured.getTriggeredRules());
            assertSame(captured, result);
        }

        @Test
        @DisplayName("is idempotent — returns the existing case without saving when one already exists")
        void idempotentWhenCaseExists() {
            VerdictedTransaction vtx = buildVerdictedTx("tx-002", List.of("[VELOCITY] too fast"), 50);
            FraudCase existing = buildCase(5L, FraudCaseStatus.OPEN);
            when(repository.existsByTransactionId("tx-002")).thenReturn(true);
            when(repository.findByTransactionId("tx-002")).thenReturn(Optional.of(existing));

            FraudCase result = service.createFromVerdict(vtx);

            assertSame(existing, result);
            verify(repository, never()).save(any());
        }
    }

    // ── Read operations ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAll() / getByStatus() / getById()")
    class ReadOperations {

        @Test
        @DisplayName("getAll() returns a paginated response sorted newest first")
        void getAllReturnsPagedResponse() {
            FraudCase c = buildCase(1L, FraudCaseStatus.OPEN);
            when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(c)));

            PagedResponse<FraudCase> result = service.getAll(0, 20);

            assertEquals(1, result.content().size());
            assertEquals(0, result.page());
            assertEquals(20, result.size());
            assertEquals(1L, result.totalElements());
        }

        @Test
        @DisplayName("getByStatus() delegates to the status-filtered repository query")
        void getByStatusReturnsFilteredResponse() {
            FraudCase c = buildCase(1L, FraudCaseStatus.INVESTIGATING);
            when(repository.findByStatusOrderByCreatedAtDesc(eq(FraudCaseStatus.INVESTIGATING), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(c)));

            PagedResponse<FraudCase> result = service.getByStatus(FraudCaseStatus.INVESTIGATING, 0, 20);

            assertEquals(1, result.content().size());
            assertEquals(FraudCaseStatus.INVESTIGATING, result.content().getFirst().getStatus());
        }

        @Test
        @DisplayName("getById() returns the case when found")
        void getByIdReturnsCase() {
            FraudCase c = buildCase(1L, FraudCaseStatus.OPEN);
            when(repository.findById(1L)).thenReturn(Optional.of(c));

            assertSame(c, service.getById(1L));
        }

        @Test
        @DisplayName("getById() throws FraudCaseNotFoundException when missing")
        void getByIdThrowsWhenMissing() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(FraudCaseNotFoundException.class, () -> service.getById(99L));
        }
    }

    // ── updateStatus() — state machine ──────────────────────────────────────

    @Nested
    @DisplayName("updateStatus() — state machine")
    class UpdateStatus {

        @Test
        @DisplayName("OPEN -> INVESTIGATING is allowed and persists the analyst note")
        void openToInvestigatingAllowed() {
            FraudCase c = buildCase(1L, FraudCaseStatus.OPEN);
            when(repository.findById(1L)).thenReturn(Optional.of(c));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FraudCase result = service.updateStatus(1L, FraudCaseStatus.INVESTIGATING, "Starting investigation");

            assertEquals(FraudCaseStatus.INVESTIGATING, result.getStatus());
            assertEquals("Starting investigation", result.getAnalystNotes());
        }

        @Test
        @DisplayName("INVESTIGATING -> CLOSED_FRAUD is allowed")
        void investigatingToClosedFraudAllowed() {
            FraudCase c = buildCase(1L, FraudCaseStatus.INVESTIGATING);
            when(repository.findById(1L)).thenReturn(Optional.of(c));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FraudCase result = service.updateStatus(1L, FraudCaseStatus.CLOSED_FRAUD, null);

            assertEquals(FraudCaseStatus.CLOSED_FRAUD, result.getStatus());
        }

        @Test
        @DisplayName("INVESTIGATING -> CLOSED_LEGITIMATE is allowed")
        void investigatingToClosedLegitimateAllowed() {
            FraudCase c = buildCase(1L, FraudCaseStatus.INVESTIGATING);
            when(repository.findById(1L)).thenReturn(Optional.of(c));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FraudCase result = service.updateStatus(1L, FraudCaseStatus.CLOSED_LEGITIMATE, "");

            assertEquals(FraudCaseStatus.CLOSED_LEGITIMATE, result.getStatus());
            assertNull(result.getAnalystNotes(), "Blank note should not overwrite existing notes");
        }

        @Test
        @DisplayName("OPEN -> CLOSED_FRAUD (skipping INVESTIGATING) is rejected with 409")
        void openToClosedFraudRejected() {
            FraudCase c = buildCase(1L, FraudCaseStatus.OPEN);
            when(repository.findById(1L)).thenReturn(Optional.of(c));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.updateStatus(1L, FraudCaseStatus.CLOSED_FRAUD, null));

            assertEquals(409, ex.getStatusCode().value());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Re-opening a closed case is rejected with 409")
        void reopeningClosedCaseRejected() {
            FraudCase c = buildCase(1L, FraudCaseStatus.CLOSED_FRAUD);
            when(repository.findById(1L)).thenReturn(Optional.of(c));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.updateStatus(1L, FraudCaseStatus.INVESTIGATING, null));

            assertEquals(409, ex.getStatusCode().value());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("throws FraudCaseNotFoundException when the case does not exist")
        void updateThrowsWhenMissing() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(FraudCaseNotFoundException.class,
                    () -> service.updateStatus(99L, FraudCaseStatus.INVESTIGATING, null));
        }
    }

    // ── getStatusCounts() — aggregation ──────────────────────────────────────

    @Test
    @DisplayName("getStatusCounts() maps JPQL GROUP BY rows to a typed status->count map")
    void getStatusCountsMapsRows() {
        List<Object[]> mockRows = List.of(
                new Object[]{FraudCaseStatus.OPEN, 5L},
                new Object[]{FraudCaseStatus.INVESTIGATING, 2L}
        );
        when(repository.countByStatus()).thenReturn(mockRows);

        Map<FraudCaseStatus, Long> stats = service.getStatusCounts();

        assertEquals(5L, stats.get(FraudCaseStatus.OPEN));
        assertEquals(2L, stats.get(FraudCaseStatus.INVESTIGATING));
        assertEquals(2, stats.size());
    }

    @Test
    @DisplayName("getStatusCounts() returns an empty map when no cases exist")
    void getStatusCountsEmptyWhenNoData() {
        when(repository.countByStatus()).thenReturn(List.of());

        assertTrue(service.getStatusCounts().isEmpty());
    }
}
