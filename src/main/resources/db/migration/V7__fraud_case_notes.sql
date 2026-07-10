-- Aegis Fraud-Shield: V7 — Fraud case notes / history log
-- Creates the fraud_case_note table, an append-only log of analyst notes
-- attached to a fraud_case. Distinct from the single-value fraud_case.analyst_notes
-- column (which is overwritten on every status update) — this table preserves
-- the full history of everything an analyst recorded during the investigation.
--
-- No auth/user system exists in this application, so `author` is a plain
-- optional free-text field rather than a foreign key to a user table.

CREATE TABLE fraud_case_note (
    id             BIGSERIAL     PRIMARY KEY,
    fraud_case_id  BIGINT        NOT NULL REFERENCES fraud_case(id),
    author         VARCHAR(100),
    note           TEXT          NOT NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- Index for the primary access pattern: list notes for a case, newest first
CREATE INDEX idx_fraud_case_note_case_id ON fraud_case_note (fraud_case_id, created_at DESC);
