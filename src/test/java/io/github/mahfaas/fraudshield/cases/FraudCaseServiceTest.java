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

    @Mock
    private FraudCaseNoteRepository noteRepository;

    private FraudCaseService service;

    @BeforeEach
    void setUp() {
        service = new FraudCaseService(repository, noteRepository);
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

    // ── assign() / getByAssignee() ───────────────────────────────────────────

    @Nested
    @DisplayName("assign() / getByAssignee()")
    class Assignment {

        @Test
        @DisplayName("assign() sets the assignee on an open case")
        void assignSetsAssignee() {
            FraudCase c = buildCase(1L, FraudCaseStatus.OPEN);
            when(repository.findById(1L)).thenReturn(Optional.of(c));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FraudCase result = service.assign(1L, "analyst-1");

            assertEquals("analyst-1", result.getAssignedTo());
        }

        @Test
        @DisplayName("assign() with a blank assignee clears the assignment")
        void assignBlankClearsAssignee() {
            FraudCase c = buildCase(1L, FraudCaseStatus.INVESTIGATING);
            c.setAssignedTo("analyst-1");
            when(repository.findById(1L)).thenReturn(Optional.of(c));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FraudCase result = service.assign(1L, "  ");

            assertNull(result.getAssignedTo());
        }

        @Test
        @DisplayName("assign() rejects assignment of a closed case with 409")
        void assignRejectsClosedCase() {
            FraudCase c = buildCase(1L, FraudCaseStatus.CLOSED_FRAUD);
            when(repository.findById(1L)).thenReturn(Optional.of(c));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.assign(1L, "analyst-1"));

            assertEquals(409, ex.getStatusCode().value());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("assign() throws FraudCaseNotFoundException when the case does not exist")
        void assignThrowsWhenMissing() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(FraudCaseNotFoundException.class, () -> service.assign(99L, "analyst-1"));
        }

        @Test
        @DisplayName("getByAssignee() delegates to the assignee-filtered repository query")
        void getByAssigneeReturnsFilteredResponse() {
            FraudCase c = buildCase(1L, FraudCaseStatus.INVESTIGATING);
            c.setAssignedTo("analyst-1");
            when(repository.findByAssignedToOrderByCreatedAtDesc(eq("analyst-1"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(c)));

            PagedResponse<FraudCase> result = service.getByAssignee("analyst-1", 0, 20);

            assertEquals(1, result.content().size());
            assertEquals("analyst-1", result.content().getFirst().getAssignedTo());
        }
    }

    // ── reopen() — explicit escape hatch ─────────────────────────────────────

    @Nested
    @DisplayName("reopen()")
    class Reopen {

        @Test
        @DisplayName("reopen() moves a closed case back to INVESTIGATING and records a note")
        void reopenMovesClosedCaseToInvestigating() {
            FraudCase c = buildCase(1L, FraudCaseStatus.CLOSED_FRAUD);
            when(repository.findById(1L)).thenReturn(Optional.of(c));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<FraudCaseNote> noteCaptor = ArgumentCaptor.forClass(FraudCaseNote.class);
            when(noteRepository.save(noteCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

            FraudCase result = service.reopen(1L, "supervisor-1", "New evidence surfaced");

            assertEquals(FraudCaseStatus.INVESTIGATING, result.getStatus());
            FraudCaseNote note = noteCaptor.getValue();
            assertEquals("supervisor-1", note.getAuthor());
            assertTrue(note.getNote().contains("New evidence surfaced"));
            assertTrue(note.getNote().contains("CLOSED_FRAUD"));
        }

        @Test
        @DisplayName("reopen() rejects a case that is not closed with 409")
        void reopenRejectsNonClosedCase() {
            FraudCase c = buildCase(1L, FraudCaseStatus.INVESTIGATING);
            when(repository.findById(1L)).thenReturn(Optional.of(c));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.reopen(1L, "supervisor-1", "reason"));

            assertEquals(409, ex.getStatusCode().value());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("reopen() rejects a blank reason with 400")
        void reopenRejectsBlankReason() {
            FraudCase c = buildCase(1L, FraudCaseStatus.CLOSED_LEGITIMATE);
            when(repository.findById(1L)).thenReturn(Optional.of(c));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.reopen(1L, "supervisor-1", "  "));

            assertEquals(400, ex.getStatusCode().value());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("reopen() throws FraudCaseNotFoundException when the case does not exist")
        void reopenThrowsWhenMissing() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(FraudCaseNotFoundException.class,
                    () -> service.reopen(99L, "supervisor-1", "reason"));
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

    // ── getReviewAccuracy() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getReviewAccuracy()")
    class ReviewAccuracy {

        @Test
        @DisplayName("computes false/true positive and resolved rates from status counts")
        void computesRatesFromStatusCounts() {
            List<Object[]> mockRows = List.of(
                    new Object[]{FraudCaseStatus.CLOSED_FRAUD, 3L},
                    new Object[]{FraudCaseStatus.CLOSED_LEGITIMATE, 1L},
                    new Object[]{FraudCaseStatus.OPEN, 1L},
                    new Object[]{FraudCaseStatus.INVESTIGATING, 2L}
            );
            when(repository.countByStatusSince(any())).thenReturn(mockRows);

            FraudCaseService.ReviewAccuracyStats stats = service.getReviewAccuracy(30);

            assertEquals(30, stats.windowDays());
            assertEquals(7, stats.totalCases());
            assertEquals(3L, stats.closedFraud());
            assertEquals(1L, stats.closedLegitimate());
            assertEquals(3L, stats.stillOpen());
            assertEquals(0.75, stats.truePositiveRate());
            assertEquals(0.25, stats.falsePositiveRate());
            assertEquals(4.0 / 7, stats.resolvedRate());
        }

        @Test
        @DisplayName("returns null rates when no cases have closed yet")
        void returnsNullRatesWhenNothingClosed() {
            List<Object[]> mockRows = List.<Object[]>of(
                    new Object[]{FraudCaseStatus.OPEN, 4L}
            );
            when(repository.countByStatusSince(any())).thenReturn(mockRows);

            FraudCaseService.ReviewAccuracyStats stats = service.getReviewAccuracy(7);

            assertEquals(4L, stats.totalCases());
            assertNull(stats.falsePositiveRate());
            assertNull(stats.truePositiveRate());
            assertEquals(0.0, stats.resolvedRate());
        }

        @Test
        @DisplayName("returns all-null rates when no cases exist in the window")
        void returnsNullRatesWhenNoCases() {
            when(repository.countByStatusSince(any())).thenReturn(List.of());

            FraudCaseService.ReviewAccuracyStats stats = service.getReviewAccuracy(30);

            assertEquals(0L, stats.totalCases());
            assertNull(stats.falsePositiveRate());
            assertNull(stats.truePositiveRate());
            assertNull(stats.resolvedRate());
        }
    }

    // ── addNote() / getNotes() — history log ─────────────────────────────────

    @Nested
    @DisplayName("addNote() / getNotes() — history log")
    class Notes {

        @Test
        @DisplayName("addNote() persists a note linked to the case")
        void addNotePersistsNote() {
            FraudCase c = buildCase(1L, FraudCaseStatus.INVESTIGATING);
            when(repository.findById(1L)).thenReturn(Optional.of(c));

            ArgumentCaptor<FraudCaseNote> captor = ArgumentCaptor.forClass(FraudCaseNote.class);
            when(noteRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            FraudCaseNote result = service.addNote(1L, "analyst-1", "Checked device fingerprint, looks suspicious");

            FraudCaseNote captured = captor.getValue();
            assertSame(c, captured.getFraudCase());
            assertEquals("analyst-1", captured.getAuthor());
            assertEquals("Checked device fingerprint, looks suspicious", captured.getNote());
            assertSame(captured, result);
        }

        @Test
        @DisplayName("addNote() throws FraudCaseNotFoundException when the case does not exist")
        void addNoteThrowsWhenMissing() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(FraudCaseNotFoundException.class,
                    () -> service.addNote(99L, "analyst-1", "note"));
            verify(noteRepository, never()).save(any());
        }

        @Test
        @DisplayName("getNotes() returns notes newest first")
        void getNotesReturnsNotes() {
            when(repository.existsById(1L)).thenReturn(true);
            FraudCaseNote n1 = FraudCaseNote.builder().id(1L).author("analyst-1").note("first").build();
            FraudCaseNote n2 = FraudCaseNote.builder().id(2L).author("analyst-2").note("second").build();
            when(noteRepository.findByFraudCaseIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(n2, n1));

            List<FraudCaseNote> result = service.getNotes(1L);

            assertEquals(2, result.size());
            assertEquals("second", result.get(0).getNote());
        }

        @Test
        @DisplayName("getNotes() throws FraudCaseNotFoundException when the case does not exist")
        void getNotesThrowsWhenMissing() {
            when(repository.existsById(99L)).thenReturn(false);

            assertThrows(FraudCaseNotFoundException.class, () -> service.getNotes(99L));
        }
    }
}
