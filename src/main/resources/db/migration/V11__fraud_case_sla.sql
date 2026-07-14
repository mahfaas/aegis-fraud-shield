-- Aegis Fraud-Shield: V11 — Fraud case SLA due date
-- Adds an sla_due_at deadline to fraud_case, derived from priority at case
-- creation time (see FraudCaseService#computeSlaDueAt). Completes the
-- priority/SLA pairing started in V10 — priority alone doesn't tell an
-- analyst *when* a case needs attention by.
--
-- Existing rows are backfilled from created_at using the same default
-- per-priority windows the service applies to new cases (HIGH 4h, MEDIUM 24h,
-- LOW 72h), so historical data stays consistent with live queries.

ALTER TABLE fraud_case ADD COLUMN sla_due_at TIMESTAMP;

UPDATE fraud_case
SET sla_due_at = created_at + CASE priority
    WHEN 'HIGH'   THEN INTERVAL '4 hours'
    WHEN 'MEDIUM' THEN INTERVAL '24 hours'
    ELSE               INTERVAL '72 hours'
END;

ALTER TABLE fraud_case ALTER COLUMN sla_due_at SET NOT NULL;

-- Index for the breached-case query: open/investigating cases past due, ordered by urgency
CREATE INDEX idx_fraud_case_sla_due_at ON fraud_case (sla_due_at);
