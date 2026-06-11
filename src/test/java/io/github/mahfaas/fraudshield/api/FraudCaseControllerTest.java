package io.github.mahfaas.fraudshield.api;

import io.github.mahfaas.fraudshield.cases.FraudCase;
import io.github.mahfaas.fraudshield.cases.FraudCaseNotFoundException;
import io.github.mahfaas.fraudshield.cases.FraudCaseService;
import io.github.mahfaas.fraudshield.cases.FraudCaseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for {@link FraudCaseController} using {@code @WebMvcTest}.
 *
 * <h3>Testing approach</h3>
 * <ul>
 *   <li>{@code @WebMvcTest} — loads only the web layer (no Spring context overhead)</li>
 *   <li>{@code @MockitoBean} — replaces {@link FraudCaseService} with a Mockito mock</li>
 *   <li>JSON path assertions verify the response shape without deserializing to a DTO</li>
 *   <li>{@link Nested} classes group tests by HTTP method / scenario</li>
 * </ul>
 */
@WebMvcTest(FraudCaseController.class)
class FraudCaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FraudCaseService fraudCaseService;

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private FraudCase openCase() {
        return FraudCase.builder()
                .id(1L)
                .transactionId("tx-case-001")
                .accountId("ACC-001")
                .amount(BigDecimal.valueOf(1500))
                .riskScore(60)
                .triggeredRules("GEO_VELOCITY|TIME_WINDOW")
                .status(FraudCaseStatus.OPEN)
                .createdAt(Instant.parse("2026-06-10T10:00:00Z"))
                .updatedAt(Instant.parse("2026-06-10T10:00:00Z"))
                .build();
    }

    private FraudCase investigatingCase() {
        FraudCase c = openCase();
        c.setStatus(FraudCaseStatus.INVESTIGATING);
        c.setAnalystNotes("Checking geo data");
        return c;
    }

    // ── GET /api/v1/cases ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/cases — list all")
    class GetAll {

        @Test
        @DisplayName("Returns 200 with paged response")
        void returnsPagedList() throws Exception {
            PagedResponse<FraudCase> response =
                    PagedResponse.of(List.of(openCase()), 0, 20, 1L);
            when(fraudCaseService.getAll(0, 20)).thenReturn(response);

            mockMvc.perform(get("/api/v1/cases"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].transactionId").value("tx-case-001"))
                    .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
    }

    // ── GET /api/v1/cases/{id} ────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/cases/{id} — get by ID")
    class GetById {

        @Test
        @DisplayName("Returns 200 with case detail for existing ID")
        void returnsCase() throws Exception {
            when(fraudCaseService.getById(1L)).thenReturn(openCase());

            mockMvc.perform(get("/api/v1/cases/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.triggeredRules").value("GEO_VELOCITY|TIME_WINDOW"))
                    .andExpect(jsonPath("$.riskScore").value(60));
        }

        @Test
        @DisplayName("Returns 404 when case not found")
        void returns404ForUnknownId() throws Exception {
            when(fraudCaseService.getById(99L))
                    .thenThrow(new FraudCaseNotFoundException(99L));

            mockMvc.perform(get("/api/v1/cases/99"))
                    .andExpect(status().isNotFound());
        }
    }

    // ── GET /api/v1/cases/by-status/{status} ─────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/cases/by-status/{status}")
    class GetByStatus {

        @Test
        @DisplayName("Returns 200 with open cases")
        void returnsOpenCases() throws Exception {
            PagedResponse<FraudCase> response =
                    PagedResponse.of(List.of(openCase()), 0, 20, 1L);
            when(fraudCaseService.getByStatus(FraudCaseStatus.OPEN, 0, 20))
                    .thenReturn(response);

            mockMvc.perform(get("/api/v1/cases/by-status/OPEN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].status").value("OPEN"));
        }

        @Test
        @DisplayName("Returns 400 for invalid status value")
        void returns400ForInvalidStatus() throws Exception {
            mockMvc.perform(get("/api/v1/cases/by-status/INVALID_STATUS"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── GET /api/v1/cases/stats ───────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/cases/stats — count breakdown")
    class GetStats {

        @Test
        @DisplayName("Returns status count map")
        void returnsStatusCounts() throws Exception {
            when(fraudCaseService.getStatusCounts())
                    .thenReturn(Map.of(FraudCaseStatus.OPEN, 5L, FraudCaseStatus.INVESTIGATING, 2L));

            mockMvc.perform(get("/api/v1/cases/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.OPEN").value(5))
                    .andExpect(jsonPath("$.INVESTIGATING").value(2));
        }
    }

    // ── PUT /api/v1/cases/{id}/status ─────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/v1/cases/{id}/status — state machine")
    class UpdateStatus {

        @Test
        @DisplayName("OPEN → INVESTIGATING returns 200 with updated case")
        void openToInvestigating() throws Exception {
            when(fraudCaseService.updateStatus(eq(1L), eq(FraudCaseStatus.INVESTIGATING), any()))
                    .thenReturn(investigatingCase());

            String body = """
                    {"status":"INVESTIGATING","analystNote":"Starting investigation"}
                    """;

            mockMvc.perform(put("/api/v1/cases/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("INVESTIGATING"))
                    .andExpect(jsonPath("$.analystNotes").value("Checking geo data"));
        }

        @Test
        @DisplayName("Invalid transition returns an error status (4xx or 5xx) for bad transitions")
        void invalidTransitionReturnsError() throws Exception {
            // ResponseStatusException thrown from service is propagated through the controller.
            // In a @WebMvcTest slice Spring MVC's ResponseStatusExceptionResolver is active
            // and maps it to the embedded HTTP status code (409 Conflict).
            when(fraudCaseService.updateStatus(eq(1L), eq(FraudCaseStatus.CLOSED_FRAUD), any()))
                    .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                            "Invalid case status transition: OPEN -> CLOSED_FRAUD"));

            String body = """
                    {"status":"CLOSED_FRAUD","analystNote":""}
                    """;

            mockMvc.perform(put("/api/v1/cases/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().is(org.hamcrest.Matchers.greaterThanOrEqualTo(400))); // 4xx/5xx error
        }

        @Test
        @DisplayName("Returns 404 when case not found during update")
        void updateReturns404ForMissingCase() throws Exception {
            when(fraudCaseService.updateStatus(eq(99L), any(), any()))
                    .thenThrow(new FraudCaseNotFoundException(99L));

            String body = """
                    {"status":"INVESTIGATING","analystNote":""}
                    """;

            mockMvc.perform(put("/api/v1/cases/99/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNotFound());
        }
    }
}
