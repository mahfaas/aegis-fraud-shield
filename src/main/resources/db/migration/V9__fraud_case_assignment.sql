-- Aegis Fraud-Shield: V9 — Fraud case assignment
-- Adds an assignee column to fraud_case so analysts can claim ownership of
-- a case. No auth/user system exists in this application, so `assigned_to`
-- is a plain optional free-text field rather than a foreign key to a user
-- table (same rationale as fraud_case_note.author, see V7).

ALTER TABLE fraud_case ADD COLUMN assigned_to VARCHAR(100);

-- Index for the analyst dashboard query: "show me my assigned cases"
CREATE INDEX idx_fraud_case_assigned_to ON fraud_case (assigned_to);
