package io.github.mahfaas.fraudshield.api;

import io.github.mahfaas.fraudshield.cases.CasePriority;
import io.github.mahfaas.fraudshield.cases.FraudCase;
import io.github.mahfaas.fraudshield.cases.FraudCaseNote;
import io.github.mahfaas.fraudshield.cases.FraudCaseService;
import io.github.mahfaas.fraudshield.cases.FraudCaseStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for fraud case management.
 *
 * <h3>Endpoints</h3>
 * <pre>
 *   GET  /api/v1/cases                        — list all cases (paginated)
 *   GET  /api/v1/cases/{id}                   — get a specific case
 *   GET  /api/v1/cases/by-status/{status}     — filter by lifecycle status
 *   GET  /api/v1/cases/by-priority/{priority} — filter by triage priority (LOW/MEDIUM/HIGH)
 *   GET  /api/v1/cases/breached               — open/investigating cases past their SLA deadline
 *   GET  /api/v1/cases/stats                  — status count breakdown
 *   GET  /api/v1/cases/stats/review-accuracy  — manual-review outcome accuracy (rule precision)
 *   PUT  /api/v1/cases/{id}/status            — advance the case lifecycle
 *   PUT  /api/v1/cases/{id}/assign            — assign/unassign a case to an analyst
 *   GET  /api/v1/cases/by-assignee/{assignee} — filter by assigned analyst
 *   POST /api/v1/cases/{id}/reopen            — reopen a closed case (requires a reason)
 *   POST /api/v1/cases/bulk-assign            — assign/unassign many cases at once
 *   POST /api/v1/cases/bulk-status            — advance many cases' lifecycle at once
 * </pre>
 *
 * <h3>Case lifecycle</h3>
 * <pre>
 *   OPEN ──► INVESTIGATING ──► CLOSED_FRAUD
 *                           └──► CLOSED_LEGITIMATE
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/cases")
@RequiredArgsConstructor
@Tag(name = "Fraud Cases", description = "Lifecycle management for MANUAL_REVIEW fraud investigation cases")
public class FraudCaseController {

    private final FraudCaseService fraudCaseService;

    // ── List all ─────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(
            summary = "List all fraud cases",
            description = "Returns a paginated list of all fraud cases, newest first."
    )
    public PagedResponse<FraudCase> getAll(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0")  int page,
            @Parameter(description = "Page size (max 50)")    @RequestParam(defaultValue = "20") int size) {

        return fraudCaseService.getAll(page, Math.min(size, 50));
    }

    // ── Get by ID ─────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a fraud case by ID",
            description = "Returns the full case detail including current status and analyst notes."
    )
    public FraudCase getById(
            @Parameter(description = "Case database ID") @PathVariable Long id) {

        return fraudCaseService.getById(id);
    }

    // ── Filter by status ──────────────────────────────────────────────────────

    @GetMapping("/by-status/{status}")
    @Operation(
            summary = "List cases by status",
            description = "Returns paginated cases filtered by OPEN, INVESTIGATING, CLOSED_FRAUD, or CLOSED_LEGITIMATE."
    )
    public PagedResponse<FraudCase> getByStatus(
            @Parameter(description = "Case status to filter by") @PathVariable FraudCaseStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return fraudCaseService.getByStatus(status, page, Math.min(size, 50));
    }

    // ── Filter by priority ────────────────────────────────────────────────────

    @GetMapping("/by-priority/{priority}")
    @Operation(
            summary = "List cases by triage priority",
            description = "Returns paginated cases filtered by LOW, MEDIUM, or HIGH triage priority."
    )
    public PagedResponse<FraudCase> getByPriority(
            @Parameter(description = "Case priority to filter by") @PathVariable CasePriority priority,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return fraudCaseService.getByPriority(priority, page, Math.min(size, 50));
    }

    // ── SLA ───────────────────────────────────────────────────────────────────

    @GetMapping("/breached")
    @Operation(
            summary = "List SLA-breached cases",
            description = """
                    Returns paginated OPEN or INVESTIGATING cases past their sla-due-at
                    deadline, most overdue first. The deadline is derived from triage
                    priority at case-creation time (defaults: HIGH 4h, MEDIUM 24h, LOW 72h,
                    configurable via fraud.cases.sla.*).
                    """
    )
    public PagedResponse<FraudCase> getBreached(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return fraudCaseService.getBreached(page, Math.min(size, 50));
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    @Operation(
            summary = "Case status count breakdown",
            description = "Returns the count of cases in each lifecycle status, sourced from a JPQL GROUP BY query."
    )
    public Map<FraudCaseStatus, Long> getStatusCounts() {
        return fraudCaseService.getStatusCounts();
    }

    @GetMapping("/stats/review-accuracy")
    @Operation(
            summary = "Manual-review outcome accuracy",
            description = """
                    Reports how MANUAL_REVIEW verdicts from the Rule Engine were ultimately
                    resolved by analysts over the given lookback window — a proxy for rule
                    engine precision. CLOSED_LEGITIMATE outcomes are false positives;
                    CLOSED_FRAUD outcomes are true positives. Rate fields are null when no
                    cases have closed yet in the window.
                    """
    )
    public FraudCaseService.ReviewAccuracyStats getReviewAccuracy(
            @Parameter(description = "Lookback window in days") @RequestParam(defaultValue = "30") int days) {

        return fraudCaseService.getReviewAccuracy(days);
    }

    // ── Status update (state machine) ─────────────────────────────────────────

    @PutMapping("/{id}/status")
    @Operation(
            summary = "Advance case lifecycle status",
            description = """
                    Transitions a case to its next lifecycle status.
                    Valid transitions:
                    - OPEN → INVESTIGATING
                    - INVESTIGATING → CLOSED_FRAUD
                    - INVESTIGATING → CLOSED_LEGITIMATE
                    Returns 409 Conflict for invalid transitions or attempts to re-open closed cases.
                    """
    )
    public FraudCase updateStatus(
            @Parameter(description = "Case database ID") @PathVariable Long id,
            @RequestBody StatusUpdateRequest request) {

        return fraudCaseService.updateStatus(id, request.status(), request.analystNote());
    }

    // ── Assignment ────────────────────────────────────────────────────────────

    @PutMapping("/{id}/assign")
    @Operation(
            summary = "Assign or unassign a case",
            description = """
                    Sets the analyst assigned to work the case. Pass a null or blank
                    assignee to unassign. Returns 409 Conflict if the case is already
                    in a terminal (closed) status.
                    """
    )
    public FraudCase assign(
            @Parameter(description = "Case database ID") @PathVariable Long id,
            @RequestBody AssignRequest request) {

        return fraudCaseService.assign(id, request.assignee());
    }

    // ── Filter by assignee ────────────────────────────────────────────────────

    @GetMapping("/by-assignee/{assignee}")
    @Operation(
            summary = "List cases by assignee",
            description = "Returns paginated cases currently assigned to the given analyst."
    )
    public PagedResponse<FraudCase> getByAssignee(
            @Parameter(description = "Assignee identifier") @PathVariable String assignee,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return fraudCaseService.getByAssignee(assignee, page, Math.min(size, 50));
    }

    // ── Reopen (explicit escape hatch) ────────────────────────────────────────

    @PostMapping("/{id}/reopen")
    @Operation(
            summary = "Reopen a closed case",
            description = """
                    Reopens a CLOSED_FRAUD or CLOSED_LEGITIMATE case back to INVESTIGATING.
                    A non-blank reason is mandatory and is recorded as a case note.
                    Returns 409 Conflict if the case is not currently closed, or 400 Bad
                    Request if no reason is given.
                    """
    )
    public FraudCase reopen(
            @Parameter(description = "Case database ID") @PathVariable Long id,
            @RequestBody ReopenRequest request) {

        return fraudCaseService.reopen(id, request.reopenedBy(), request.reason());
    }

    // ── Bulk operations ───────────────────────────────────────────────────────

    @PostMapping("/bulk-assign")
    @Operation(
            summary = "Assign or unassign many cases at once",
            description = """
                    Best-effort bulk assignment: each case ID is processed independently, so
                    one bad ID (not found, or already closed) doesn't block the rest. Outcomes
                    are reported per-ID in the response. Accepts 1-100 case IDs; returns 400
                    Bad Request if the list is empty or exceeds that limit.
                    """
    )
    public FraudCaseService.BulkOperationResult bulkAssign(
            @RequestBody BulkAssignRequest request) {

        return fraudCaseService.bulkAssign(request.caseIds(), request.assignee());
    }

    @PostMapping("/bulk-status")
    @Operation(
            summary = "Advance many cases' lifecycle status at once",
            description = """
                    Best-effort bulk status transition, applying the same state-machine rules
                    as the single-case endpoint to each ID independently — one invalid
                    transition doesn't block the rest. Outcomes are reported per-ID in the
                    response. Accepts 1-100 case IDs; returns 400 Bad Request if the list is
                    empty or exceeds that limit.
                    """
    )
    public FraudCaseService.BulkOperationResult bulkUpdateStatus(
            @RequestBody BulkStatusUpdateRequest request) {

        return fraudCaseService.bulkUpdateStatus(request.caseIds(), request.status(), request.analystNote());
    }

    // ── Notes / history log ──────────────────────────────────────────────────

    @PostMapping("/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Add a note to a case",
            description = "Appends a note to the case's investigation history. Unlike the analystNote " +
                          "on the status-update endpoint (which is overwritten each time), notes here " +
                          "accumulate into a full history log."
    )
    public FraudCaseNote addNote(
            @Parameter(description = "Case database ID") @PathVariable Long id,
            @RequestBody AddNoteRequest request) {

        return fraudCaseService.addNote(id, request.author(), request.note());
    }

    @GetMapping("/{id}/notes")
    @Operation(
            summary = "List notes for a case",
            description = "Returns the full note history for a case, newest first."
    )
    public List<FraudCaseNote> getNotes(
            @Parameter(description = "Case database ID") @PathVariable Long id) {

        return fraudCaseService.getNotes(id);
    }

    // ── Request / Response DTOs ───────────────────────────────────────────────

    /**
     * Request body for the status update endpoint.
     *
     * @param status      the desired target status
     * @param analystNote optional free-text note from the investigating analyst
     */
    @ResponseStatus(HttpStatus.OK)
    public record StatusUpdateRequest(FraudCaseStatus status, String analystNote) {}

    /**
     * Request body for adding a note to a case's history log.
     *
     * @param author optional free-text identifier of who wrote the note (no auth system exists)
     * @param note   the note content
     */
    public record AddNoteRequest(String author, String note) {}

    /**
     * Request body for the assign endpoint.
     *
     * @param assignee free-text analyst identifier, or null/blank to unassign
     */
    public record AssignRequest(String assignee) {}

    /**
     * Request body for the reopen endpoint.
     *
     * @param reopenedBy optional free-text identifier of who reopened the case (no auth system exists)
     * @param reason     mandatory justification for the reopen
     */
    public record ReopenRequest(String reopenedBy, String reason) {}

    /**
     * Request body for the bulk-assign endpoint.
     *
     * @param caseIds  the case database IDs to assign (1-100)
     * @param assignee free-text analyst identifier, or null/blank to unassign
     */
    public record BulkAssignRequest(List<Long> caseIds, String assignee) {}

    /**
     * Request body for the bulk-status endpoint.
     *
     * @param caseIds     the case database IDs to transition (1-100)
     * @param status      the desired target status
     * @param analystNote optional free-text note applied to every successfully-updated case
     */
    public record BulkStatusUpdateRequest(List<Long> caseIds, FraudCaseStatus status, String analystNote) {}
}
