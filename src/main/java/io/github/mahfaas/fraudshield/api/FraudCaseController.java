package io.github.mahfaas.fraudshield.api;

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
 *   GET  /api/v1/cases/stats                  — status count breakdown
 *   PUT  /api/v1/cases/{id}/status            — advance the case lifecycle
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

    // ── Stats ─────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    @Operation(
            summary = "Case status count breakdown",
            description = "Returns the count of cases in each lifecycle status, sourced from a JPQL GROUP BY query."
    )
    public Map<FraudCaseStatus, Long> getStatusCounts() {
        return fraudCaseService.getStatusCounts();
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
}
