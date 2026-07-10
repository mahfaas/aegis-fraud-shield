package io.github.mahfaas.fraudshield.cases;

import io.github.mahfaas.fraudshield.api.PagedResponse;
import io.github.mahfaas.fraudshield.model.VerdictedTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Business logic for the fraud case management lifecycle.
 *
 * <h3>Case creation</h3>
 * <p>Cases are created automatically from
 * {@link io.github.mahfaas.fraudshield.audit.AuditService#record} whenever a
 * {@code MANUAL_REVIEW} verdict is produced. The creation is idempotent —
 * if a case already exists for a given {@code transactionId}, the call is a no-op.
 *
 * <h3>State machine</h3>
 * <pre>
 *   OPEN ──► INVESTIGATING ──► CLOSED_FRAUD
 *                           └──► CLOSED_LEGITIMATE
 * </pre>
 * <p>Only forward transitions are allowed. Attempts to re-open a closed case
 * or to make backward transitions are rejected with {@code 409 Conflict}.
 *
 * <h3>Interview talking points</h3>
 * <ul>
 *   <li>Idempotent creation via {@code existsByTransactionId} guard</li>
 *   <li>State machine validation without a framework — pure enum logic</li>
 *   <li>JPQL GROUP BY for status aggregation — same pattern as the audit log</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudCaseService {

    private final FraudCaseRepository repository;
    private final FraudCaseNoteRepository noteRepository;

    // ── Case creation ─────────────────────────────────────────────────────────

    /**
     * Creates a fraud case from a MANUAL_REVIEW verdict.
     * Idempotent: if a case already exists for the transaction, this is a no-op.
     *
     * <p>Called by {@link io.github.mahfaas.fraudshield.audit.AuditService#record}
     * after every MANUAL_REVIEW verdict is persisted to the audit log.
     *
     * @param verdictedTransaction the full verdicted transaction from the Rule Engine
     * @return the newly created case, or the existing one if already present
     */
    @Transactional
    public FraudCase createFromVerdict(VerdictedTransaction verdictedTransaction) {
        String txId = verdictedTransaction.getTransaction().getTransactionId();

        if (repository.existsByTransactionId(txId)) {
            log.debug("FraudCase already exists for txId={}, skipping creation", txId);
            return repository.findByTransactionId(txId).orElseThrow();
        }

        // Join reasons to extract triggered rule names for display
        String triggeredRules = verdictedTransaction.getReasons().stream()
                .map(r -> r.replaceAll("\\[(.+?)].*", "$1").trim())
                .collect(Collectors.joining("|"));

        FraudCase fraudCase = FraudCase.builder()
                .transactionId(txId)
                .accountId(verdictedTransaction.getTransaction().getAccountId())
                .amount(verdictedTransaction.getTransaction().getAmount())
                .riskScore(verdictedTransaction.getTotalRiskScore())
                .triggeredRules(triggeredRules)
                .status(FraudCaseStatus.OPEN)
                .build();

        FraudCase saved = repository.save(fraudCase);
        log.info("FraudCase created: id={}, txId={}, riskScore={}",
                saved.getId(), txId, saved.getRiskScore());
        return saved;
    }

    // ── Read operations ───────────────────────────────────────────────────────

    /**
     * Returns a paginated list of all fraud cases, newest first.
     */
    @Transactional(readOnly = true)
    public PagedResponse<FraudCase> getAll(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<FraudCase> result = repository.findAll(pageable);
        return PagedResponse.of(result.getContent(), page, size, result.getTotalElements());
    }

    /**
     * Returns a paginated list of cases filtered by status.
     */
    @Transactional(readOnly = true)
    public PagedResponse<FraudCase> getByStatus(FraudCaseStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<FraudCase> result = repository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return PagedResponse.of(result.getContent(), page, size, result.getTotalElements());
    }

    /**
     * Returns a single case by its database ID.
     *
     * @throws FraudCaseNotFoundException if no case with the given ID exists
     */
    @Transactional(readOnly = true)
    public FraudCase getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new FraudCaseNotFoundException(id));
    }

    // ── Status update (state machine) ─────────────────────────────────────────

    /**
     * Transitions a case to a new status, applying state-machine validation.
     *
     * <p>Valid transitions:
     * <ul>
     *   <li>{@code OPEN} → {@code INVESTIGATING}</li>
     *   <li>{@code INVESTIGATING} → {@code CLOSED_FRAUD}</li>
     *   <li>{@code INVESTIGATING} → {@code CLOSED_LEGITIMATE}</li>
     * </ul>
     *
     * @param id          the case database ID
     * @param newStatus   the desired target status
     * @param analystNote optional free-text note from the analyst
     * @return the updated case
     * @throws FraudCaseNotFoundException   if case not found
     * @throws ResponseStatusException       if the transition is invalid (409)
     */
    @Transactional
    public FraudCase updateStatus(Long id, FraudCaseStatus newStatus, String analystNote) {
        FraudCase fraudCase = repository.findById(id)
                .orElseThrow(() -> new FraudCaseNotFoundException(id));

        validateTransition(fraudCase.getStatus(), newStatus);

        fraudCase.setStatus(newStatus);
        if (analystNote != null && !analystNote.isBlank()) {
            fraudCase.setAnalystNotes(analystNote);
        }

        FraudCase updated = repository.save(fraudCase);
        log.info("FraudCase {} transitioned: {} → {}", id, fraudCase.getStatus(), newStatus);
        return updated;
    }

    // ── Aggregation ───────────────────────────────────────────────────────────

    /**
     * Returns a count breakdown by status using a JPQL GROUP BY query.
     *
     * @return map of {@code FraudCaseStatus → count}
     */
    @Transactional(readOnly = true)
    public Map<FraudCaseStatus, Long> getStatusCounts() {
        List<Object[]> rows = repository.countByStatus();
        return rows.stream()
                .collect(Collectors.toMap(
                        row -> (FraudCaseStatus) row[0],
                        row -> (Long) row[1],
                        (a, b) -> a,
                        () -> new EnumMap<>(FraudCaseStatus.class)
                ));
    }

    // ── Notes / history log ──────────────────────────────────────────────────

    /**
     * Appends a note to a case's investigation history.
     *
     * <p>Unlike {@code analystNotes} on {@link FraudCase} (overwritten on every
     * status update), notes recorded here accumulate — giving a full audit
     * trail independent of status transitions.
     *
     * @param caseId the case database ID
     * @param author optional free-text identifier of who wrote the note (no auth system exists)
     * @param note   the note content
     * @return the persisted note
     * @throws FraudCaseNotFoundException if the case does not exist
     */
    @Transactional
    public FraudCaseNote addNote(Long caseId, String author, String note) {
        FraudCase fraudCase = repository.findById(caseId)
                .orElseThrow(() -> new FraudCaseNotFoundException(caseId));

        FraudCaseNote fraudCaseNote = FraudCaseNote.builder()
                .fraudCase(fraudCase)
                .author(author)
                .note(note)
                .build();

        FraudCaseNote saved = noteRepository.save(fraudCaseNote);
        log.info("Note added to FraudCase {}: author={}", caseId, author);
        return saved;
    }

    /**
     * Returns all notes for a case, newest first.
     *
     * @param caseId the case database ID
     * @throws FraudCaseNotFoundException if the case does not exist
     */
    @Transactional(readOnly = true)
    public List<FraudCaseNote> getNotes(Long caseId) {
        if (!repository.existsById(caseId)) {
            throw new FraudCaseNotFoundException(caseId);
        }
        return noteRepository.findByFraudCaseIdOrderByCreatedAtDesc(caseId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Enforces the state machine — throws {@code 409 Conflict} on invalid transitions.
     */
    private void validateTransition(FraudCaseStatus current, FraudCaseStatus target) {
        if (current.isClosed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot transition from terminal status " + current);
        }

        boolean valid = switch (current) {
            case OPEN          -> target == FraudCaseStatus.INVESTIGATING;
            case INVESTIGATING -> target == FraudCaseStatus.CLOSED_FRAUD
                                  || target == FraudCaseStatus.CLOSED_LEGITIMATE;
            default            -> false;
        };

        if (!valid) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invalid case status transition: " + current + " → " + target);
        }
    }
}
