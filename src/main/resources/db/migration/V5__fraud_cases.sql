-- Aegis Fraud-Shield: V5 — Fraud case management table
-- Creates the fraud_case table which stores investigation cases
-- auto-generated from MANUAL_REVIEW verdicts produced by the Rule Engine.
--
-- Lifecycle: OPEN → INVESTIGATING → CLOSED_FRAUD | CLOSED_LEGITIMATE
-- State machine transitions are enforced at the application layer (FraudCaseService).

CREATE TABLE fraud_case (
    id               BIGSERIAL     PRIMARY KEY,

    -- The originating transaction reference (unique: one case per transaction)
    transaction_id   VARCHAR(36)   NOT NULL UNIQUE,
    account_id       VARCHAR(50)   NOT NULL,
    amount           NUMERIC(15,2),

    -- Composite risk score from the Rule Engine at verdict time
    risk_score       INT,

    -- Pipe-delimited rule names that fired (e.g. 'GEO_VELOCITY|TIME_WINDOW')
    triggered_rules  TEXT,

    -- Lifecycle status — constrained to valid enum values
    status           VARCHAR(30)   NOT NULL DEFAULT 'OPEN'
                         CHECK (status IN ('OPEN','INVESTIGATING','CLOSED_FRAUD','CLOSED_LEGITIMATE')),

    -- Free-text analyst notes, updated via PUT /api/v1/cases/{id}/status
    analyst_notes    TEXT,

    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- Index for the most common query: list cases by status (analyst dashboard)
CREATE INDEX idx_fraud_case_status ON fraud_case (status);

-- Index for look-up by account (cross-reference with audit log)
CREATE INDEX idx_fraud_case_account ON fraud_case (account_id);

-- Index for look-up by transaction ID (idempotency check)
CREATE UNIQUE INDEX idx_fraud_case_tx_id ON fraud_case (transaction_id);
