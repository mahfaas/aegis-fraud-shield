package io.github.mahfaas.fraudshield.cases;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity representing a single append-only note in a {@link FraudCase}'s
 * investigation history.
 *
 * <p>Unlike {@link FraudCase#getAnalystNotes()} (a single free-text field
 * overwritten on every status update), notes recorded here accumulate —
 * giving a full audit trail of everything an analyst observed during the
 * investigation, independent of status transitions.
 *
 * <p>No auth/user system exists in this application, so {@link #author} is a
 * plain optional free-text field rather than a foreign key to a user table.
 *
 * <h3>Table: {@code fraud_case_note}</h3>
 * <pre>
 *   id              BIGSERIAL  PK
 *   fraud_case_id   BIGINT     NOT NULL FK -&gt; fraud_case(id)
 *   author          VARCHAR    (free-text, nullable)
 *   note            TEXT       NOT NULL
 *   created_at      TIMESTAMP  NOT NULL
 * </pre>
 */
@Entity
@Table(name = "fraud_case_note")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCaseNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Owning side of the relationship to the parent case.
     *
     * <p>{@code @ManyToOne(fetch = LAZY)} avoids loading the parent case
     * when only the note itself is needed.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fraud_case_id", nullable = false)
    private FraudCase fraudCase;

    /** Free-text identifier of who wrote the note. Optional — no auth system exists. */
    @Column(length = 100)
    private String author;

    /** The note content. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String note;

    /** UTC instant when the note was recorded. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onPrePersist() {
        this.createdAt = Instant.now();
    }
}
