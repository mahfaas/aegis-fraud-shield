package io.github.mahfaas.fraudshield.cases;

import io.github.mahfaas.fraudshield.api.PagedResponse;
import io.github.mahfaas.fraudshield.model.VerdictedTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
public class FraudCaseService {

    /** Non-terminal statuses considered "open work" for SLA breach purposes. */
    private static final Set<FraudCaseStatus> OPEN_STATUSES =
            Set.of(FraudCaseStatus.OPEN, FraudCaseStatus.INVESTIGATING);

    private final FraudCaseRepository repository;
    private final FraudCaseNoteRepository noteRepository;
    private final Duration highPrioritySla;
    private final Duration mediumPrioritySla;
    private final Duration lowPrioritySla;

    public FraudCaseService(
            FraudCaseRepository repository,
            FraudCaseNoteRepository noteRepository,
            @Value("${fraud.cases.sla.high-hours:4}") long highPriorityHours,
            @Value("${fraud.cases.sla.medium-hours:24}") long mediumPriorityHours,
            @Value("${fraud.cases.sla.low-hours:72}") long lowPriorityHours) {
        this.repository = repository;
        this.noteRepository = noteRepository;
        this.highPrioritySla = Duration.ofHours(highPriorityHours);
        this.mediumPrioritySla = Duration.ofHours(mediumPriorityHours);
        this.lowPrioritySla = Duration.ofHours(lowPriorityHours);
    }

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

        int riskScore = verdictedTransaction.getTotalRiskScore();
        CasePriority priority = computePriority(riskScore);
        Instant now = Instant.now();

        FraudCase fraudCase = FraudCase.builder()
                .transactionId(txId)
                .accountId(verdictedTransaction.getTransaction().getAccountId())
                .amount(verdictedTransaction.getTransaction().getAmount())
                .riskScore(riskScore)
                .triggeredRules(triggeredRules)
                .status(FraudCaseStatus.OPEN)
                .priority(priority)
                .slaDueAt(now.plus(slaFor(priority)))
                .build();

        FraudCase saved = repository.save(fraudCase);
        log.info("FraudCase created: id={}, txId={}, riskScore={}, priority={}, slaDueAt={}",
                saved.getId(), txId, saved.getRiskScore(), saved.getPriority(), saved.getSlaDueAt());
        return saved;
    }

    /**
     * Derives a {@link CasePriority} from the composite risk score at case
     * creation time. Thresholds: {@code LOW} below 50, {@code MEDIUM} 50–74,
     * {@code HIGH} 75 and above — mirroring how close the score sits to the
     * {@code fraud.rules.risk-score.hard-threshold} auto-decline cutoff.
     */
    static CasePriority computePriority(int riskScore) {
        if (riskScore >= 75) {
            return CasePriority.HIGH;
        }
        if (riskScore >= 50) {
            return CasePriority.MEDIUM;
        }
        return CasePriority.LOW;
    }

    /**
     * Returns the SLA window for a given {@link CasePriority}, configurable via
     * {@code fraud.cases.sla.{high,medium,low}-hours} (defaults: 4h / 24h / 72h).
     */
    private Duration slaFor(CasePriority priority) {
        return switch (priority) {
            case HIGH -> highPrioritySla;
            case MEDIUM -> mediumPrioritySla;
            case LOW -> lowPrioritySla;
        };
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
        return applyStatusUpdate(fraudCase, newStatus, analystNote);
    }

    /**
     * Core status-transition logic shared by {@link #updateStatus} and
     * {@link #bulkUpdateStatus}. Assumes {@code fraudCase} was already loaded —
     * callers are responsible for the not-found lookup.
     */
    private FraudCase applyStatusUpdate(FraudCase fraudCase, FraudCaseStatus newStatus, String analystNote) {
        FraudCaseStatus previousStatus = fraudCase.getStatus();
        validateTransition(previousStatus, newStatus);

        fraudCase.setStatus(newStatus);
        if (analystNote != null && !analystNote.isBlank()) {
            fraudCase.setAnalystNotes(analystNote);
        }

        FraudCase updated = repository.save(fraudCase);
        log.info("FraudCase {} transitioned: {} → {}", updated.getId(), previousStatus, newStatus);
        return updated;
    }

    // ── Assignment ────────────────────────────────────────────────────────────

    /**
     * Assigns (or unassigns) a case to an analyst.
     *
     * <p>Passing a null or blank {@code assignee} clears the assignment.
     * Closed cases can no longer be reassigned — there is nothing left to work.
     *
     * @param id       the case database ID
     * @param assignee free-text analyst identifier, or null/blank to unassign
     * @return the updated case
     * @throws FraudCaseNotFoundException if case not found
     * @throws ResponseStatusException     if the case is already closed (409)
     */
    @Transactional
    public FraudCase assign(Long id, String assignee) {
        FraudCase fraudCase = repository.findById(id)
                .orElseThrow(() -> new FraudCaseNotFoundException(id));
        return applyAssign(fraudCase, assignee);
    }

    /**
     * Core assignment logic shared by {@link #assign} and {@link #bulkAssign}.
     * Assumes {@code fraudCase} was already loaded — callers are responsible
     * for the not-found lookup.
     */
    private FraudCase applyAssign(FraudCase fraudCase, String assignee) {
        if (fraudCase.getStatus().isClosed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot assign a case in terminal status " + fraudCase.getStatus());
        }

        String normalized = (assignee == null || assignee.isBlank()) ? null : assignee;
        fraudCase.setAssignedTo(normalized);

        FraudCase updated = repository.save(fraudCase);
        log.info("FraudCase {} assigned to {}", updated.getId(), normalized);
        return updated;
    }

    /**
     * Returns a paginated list of cases assigned to the given analyst, newest first.
     */
    @Transactional(readOnly = true)
    public PagedResponse<FraudCase> getByAssignee(String assignee, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<FraudCase> result = repository.findByAssignedToOrderByCreatedAtDesc(assignee, pageable);
        return PagedResponse.of(result.getContent(), page, size, result.getTotalElements());
    }

    /**
     * Returns a paginated list of cases filtered by triage priority, newest first.
     */
    @Transactional(readOnly = true)
    public PagedResponse<FraudCase> getByPriority(CasePriority priority, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<FraudCase> result = repository.findByPriorityOrderByCreatedAtDesc(priority, pageable);
        return PagedResponse.of(result.getContent(), page, size, result.getTotalElements());
    }

    // ── Bulk operations ──────────────────────────────────────────────────────

    /** Upper bound on how many case IDs a single bulk request may touch. */
    private static final int MAX_BULK_SIZE = 100;

    /**
     * Assigns (or unassigns) many cases to an analyst in one call.
     *
     * <p>Best-effort: each case ID is processed independently, so one bad ID
     * (not found, or already closed) doesn't block the rest of the batch —
     * outcomes are reported per-ID in the returned {@link BulkOperationResult}.
     *
     * @param caseIds  the case database IDs to assign (1-{@value #MAX_BULK_SIZE})
     * @param assignee free-text analyst identifier, or null/blank to unassign
     * @throws ResponseStatusException if the ID list is empty or exceeds {@value #MAX_BULK_SIZE} (400)
     */
    @Transactional
    public BulkOperationResult bulkAssign(List<Long> caseIds, String assignee) {
        validateBulkSize(caseIds);

        List<Long> succeeded = new ArrayList<>();
        Map<Long, String> failed = new LinkedHashMap<>();

        for (Long id : caseIds) {
            try {
                FraudCase fraudCase = repository.findById(id)
                        .orElseThrow(() -> new FraudCaseNotFoundException(id));
                applyAssign(fraudCase, assignee);
                succeeded.add(id);
            } catch (FraudCaseNotFoundException | ResponseStatusException e) {
                failed.put(id, e.getMessage());
            }
        }

        log.info("Bulk assign to {}: {} succeeded, {} failed", assignee, succeeded.size(), failed.size());
        return new BulkOperationResult(succeeded, failed);
    }

    /**
     * Transitions many cases to a new status in one call, applying the same
     * state-machine validation as {@link #updateStatus} to each.
     *
     * <p>Best-effort: each case ID is processed independently, so one invalid
     * transition doesn't block the rest of the batch — outcomes are reported
     * per-ID in the returned {@link BulkOperationResult}.
     *
     * @param caseIds     the case database IDs to transition (1-{@value #MAX_BULK_SIZE})
     * @param newStatus   the desired target status
     * @param analystNote optional free-text note applied to every successfully-updated case
     * @throws ResponseStatusException if the ID list is empty or exceeds {@value #MAX_BULK_SIZE} (400)
     */
    @Transactional
    public BulkOperationResult bulkUpdateStatus(List<Long> caseIds, FraudCaseStatus newStatus, String analystNote) {
        validateBulkSize(caseIds);

        List<Long> succeeded = new ArrayList<>();
        Map<Long, String> failed = new LinkedHashMap<>();

        for (Long id : caseIds) {
            try {
                FraudCase fraudCase = repository.findById(id)
                        .orElseThrow(() -> new FraudCaseNotFoundException(id));
                applyStatusUpdate(fraudCase, newStatus, analystNote);
                succeeded.add(id);
            } catch (FraudCaseNotFoundException | ResponseStatusException e) {
                failed.put(id, e.getMessage());
            }
        }

        log.info("Bulk status update to {}: {} succeeded, {} failed", newStatus, succeeded.size(), failed.size());
        return new BulkOperationResult(succeeded, failed);
    }

    private void validateBulkSize(List<Long> caseIds) {
        if (caseIds == null || caseIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No case IDs provided");
        }
        if (caseIds.size() > MAX_BULK_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Too many case IDs: " + caseIds.size() + " exceeds the limit of " + MAX_BULK_SIZE);
        }
    }

    /**
     * Per-ID outcome of a bulk operation.
     *
     * @param succeeded case IDs that were updated successfully
     * @param failed    case IDs that could not be updated, mapped to the failure reason
     */
    public record BulkOperationResult(List<Long> succeeded, Map<Long, String> failed) {}

    // ── SLA ───────────────────────────────────────────────────────────────────

    /**
     * Returns a paginated list of open/investigating cases past their
     * {@code slaDueAt} deadline, most overdue first — the analyst queue for
     * cases that need attention right now.
     */
    @Transactional(readOnly = true)
    public PagedResponse<FraudCase> getBreached(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<FraudCase> result = repository.findBreached(OPEN_STATUSES, Instant.now(), pageable);
        return PagedResponse.of(result.getContent(), page, size, result.getTotalElements());
    }

    /**
     * Count of currently-breached cases, used by the
     * {@code fraud.cases.sla_breached} gauge in {@code FraudMetrics}.
     */
    @Transactional(readOnly = true)
    public long getBreachedCount() {
        return repository.countBreached(OPEN_STATUSES, Instant.now());
    }

    // ── Reopen (explicit escape hatch) ───────────────────────────────────────

    /**
     * Reopens a closed case back to {@code INVESTIGATING}.
     *
     * <p>Unlike {@link #updateStatus}, which strictly enforces forward-only
     * transitions, this is a deliberate override for the case where a
     * supervisor needs to revisit a determination (e.g. new evidence surfaces).
     * A non-blank {@code reason} is mandatory, and the reopen is recorded as a
     * {@link FraudCaseNote} so the override is visible in the case history.
     *
     * @param id         the case database ID
     * @param reopenedBy optional free-text identifier of who reopened the case (no auth system exists)
     * @param reason     mandatory justification for the reopen
     * @return the updated case, now in {@code INVESTIGATING}
     * @throws FraudCaseNotFoundException if case not found
     * @throws ResponseStatusException     if the case is not currently closed (409),
     *                                     or if no reason was given (400)
     */
    @Transactional
    public FraudCase reopen(Long id, String reopenedBy, String reason) {
        FraudCase fraudCase = repository.findById(id)
                .orElseThrow(() -> new FraudCaseNotFoundException(id));

        if (!fraudCase.getStatus().isClosed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot reopen a case that is not closed: current status " + fraudCase.getStatus());
        }
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A reason is required to reopen a case");
        }

        FraudCaseStatus previousStatus = fraudCase.getStatus();
        fraudCase.setStatus(FraudCaseStatus.INVESTIGATING);
        FraudCase updated = repository.save(fraudCase);

        noteRepository.save(FraudCaseNote.builder()
                .fraudCase(updated)
                .author(reopenedBy)
                .note("Case reopened from " + previousStatus + ": " + reason)
                .build());

        log.info("FraudCase {} reopened from {} by {}", id, previousStatus, reopenedBy);
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

    /**
     * Reports how MANUAL_REVIEW verdicts from the Rule Engine were ultimately
     * resolved by analysts over the given lookback window — a proxy for rule
     * engine precision.
     *
     * <p>{@code CLOSED_LEGITIMATE} outcomes are false positives (the engine
     * flagged a transaction an analyst judged fine); {@code CLOSED_FRAUD}
     * outcomes are true positives. {@code OPEN}/{@code INVESTIGATING} cases
     * are still pending and excluded from the rate calculations.
     *
     * @param days lookback window in days
     * @return the accuracy breakdown; rate fields are {@code null} when no
     *         cases have been closed yet in the window (avoids a divide-by-zero)
     */
    @Transactional(readOnly = true)
    public ReviewAccuracyStats getReviewAccuracy(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Object[]> rows = repository.countByStatusSince(since);

        Map<FraudCaseStatus, Long> counts = rows.stream()
                .collect(Collectors.toMap(
                        row -> (FraudCaseStatus) row[0],
                        row -> (Long) row[1],
                        (a, b) -> a,
                        () -> new EnumMap<>(FraudCaseStatus.class)
                ));

        long closedFraud = counts.getOrDefault(FraudCaseStatus.CLOSED_FRAUD, 0L);
        long closedLegitimate = counts.getOrDefault(FraudCaseStatus.CLOSED_LEGITIMATE, 0L);
        long stillOpen = counts.getOrDefault(FraudCaseStatus.OPEN, 0L)
                + counts.getOrDefault(FraudCaseStatus.INVESTIGATING, 0L);
        long totalCases = closedFraud + closedLegitimate + stillOpen;
        long totalClosed = closedFraud + closedLegitimate;

        Double falsePositiveRate = totalClosed == 0 ? null : (double) closedLegitimate / totalClosed;
        Double truePositiveRate = totalClosed == 0 ? null : (double) closedFraud / totalClosed;
        Double resolvedRate = totalCases == 0 ? null : (double) totalClosed / totalCases;

        return new ReviewAccuracyStats(
                days, totalCases, closedFraud, closedLegitimate, stillOpen,
                falsePositiveRate, truePositiveRate, resolvedRate
        );
    }

    /**
     * Review-accuracy report for {@link #getReviewAccuracy}.
     *
     * @param windowDays         the lookback window this report covers
     * @param totalCases         total cases created in the window
     * @param closedFraud        cases confirmed as fraud (true positives)
     * @param closedLegitimate   cases confirmed as legitimate (false positives)
     * @param stillOpen          cases still OPEN or INVESTIGATING
     * @param falsePositiveRate  closedLegitimate / totalClosed, or null if nothing has closed yet
     * @param truePositiveRate   closedFraud / totalClosed, or null if nothing has closed yet
     * @param resolvedRate       totalClosed / totalCases, or null if totalCases is zero
     */
    public record ReviewAccuracyStats(
            int windowDays,
            long totalCases,
            long closedFraud,
            long closedLegitimate,
            long stillOpen,
            Double falsePositiveRate,
            Double truePositiveRate,
            Double resolvedRate
    ) {}

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
