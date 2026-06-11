-- Aegis Fraud-Shield: V6 — Audit log indexes for dynamic search
-- 
-- V1 created indexes on account_id and verdict.
-- The new search API allows filtering by transaction_id, dates, and compound combinations.
-- Here we add missing indexes to support the dynamic Specification queries.

-- Index for exact lookups (also useful for idempotency checks)
CREATE INDEX idx_audit_transaction_id ON audit_log (transaction_id);

-- Index for date-range queries (used by startDate and endDate)
CREATE INDEX idx_audit_processed_at ON audit_log (processed_at);

-- A composite index for the most common analyst dashboard query: "declines in the last X hours"
CREATE INDEX idx_audit_verdict_date ON audit_log (verdict, processed_at);
