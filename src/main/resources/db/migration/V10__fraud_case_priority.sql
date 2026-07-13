-- Aegis Fraud-Shield: V10 — Fraud case triage priority
-- Adds a priority column to fraud_case, derived from the risk_score captured
-- at case-creation time (see FraudCaseService#computePriority). Stored on the
-- entity rather than computed on read so it stays stable per case and can be
-- filtered/paginated efficiently in SQL.
--
-- Thresholds: LOW  < 50, MEDIUM 50-74, HIGH >= 75 (mirrors the same
-- risk_score already persisted per row, so existing rows can be backfilled
-- directly rather than defaulting blindly).

ALTER TABLE fraud_case ADD COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'LOW'
    CHECK (priority IN ('LOW','MEDIUM','HIGH'));

UPDATE fraud_case
SET priority = CASE
    WHEN risk_score >= 75 THEN 'HIGH'
    WHEN risk_score >= 50 THEN 'MEDIUM'
    ELSE 'LOW'
END
WHERE risk_score IS NOT NULL;

-- Index for the analyst dashboard query: "show me the highest-priority queue"
CREATE INDEX idx_fraud_case_priority ON fraud_case (priority);
