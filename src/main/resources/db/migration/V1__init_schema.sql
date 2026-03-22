-- Aegis Fraud-Shield: Initial schema
-- V1: Blacklist entries + Rule configuration

CREATE TABLE blacklist_entry (
    id          BIGSERIAL PRIMARY KEY,
    type        VARCHAR(10)  NOT NULL CHECK (type IN ('IP', 'BIN')),
    value       VARCHAR(45)  NOT NULL,
    reason      VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (type, value)
);

CREATE INDEX idx_blacklist_type_value ON blacklist_entry (type, value);

CREATE TABLE rule_config (
    id          BIGSERIAL PRIMARY KEY,
    rule_name   VARCHAR(50)  NOT NULL UNIQUE,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    config_json JSONB        NOT NULL DEFAULT '{}',
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Seed default rule configs
INSERT INTO rule_config (rule_name, config_json) VALUES
    ('BLACKLIST',      '{"enabled": true}'),
    ('AMOUNT_ANOMALY', '{"declineThreshold": 500000, "reviewThreshold": 100000}'),
    ('VELOCITY',       '{"maxTransactions": 5, "windowSeconds": 60}');

CREATE TABLE audit_log (
    id              BIGSERIAL PRIMARY KEY,
    transaction_id  VARCHAR(36)  NOT NULL,
    account_id      VARCHAR(50)  NOT NULL,
    amount          NUMERIC(15,2) NOT NULL,
    verdict         VARCHAR(20)  NOT NULL,
    reasons         TEXT,
    processed_at    TIMESTAMP    NOT NULL,
    reviewed_by     VARCHAR(50),
    reviewed_at     TIMESTAMP,
    review_verdict  VARCHAR(20)
);

CREATE INDEX idx_audit_account ON audit_log (account_id);
CREATE INDEX idx_audit_verdict ON audit_log (verdict);
