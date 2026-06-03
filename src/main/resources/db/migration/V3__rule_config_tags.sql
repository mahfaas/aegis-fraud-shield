-- Aegis Fraud-Shield: V3 — Rule config tags
-- Adds a one-to-many relationship: one rule_config row can have many descriptive tags.
-- This migration demonstrates DDL schema management: FK constraints, indexes,
-- and referential integrity with ON DELETE CASCADE.

CREATE TABLE rule_config_tag (
    id              BIGSERIAL    PRIMARY KEY,
    rule_config_id  BIGINT       NOT NULL
                        REFERENCES rule_config(id) ON DELETE CASCADE,
    tag_key         VARCHAR(50)  NOT NULL,
    tag_value       VARCHAR(255) NOT NULL,
    UNIQUE (rule_config_id, tag_key)
);

CREATE INDEX idx_rule_config_tag_config_id ON rule_config_tag (rule_config_id);

-- Seed initial tags for the three default rule configs
INSERT INTO rule_config_tag (rule_config_id, tag_key, tag_value)
SELECT id, 'severity', 'HIGH'
FROM   rule_config WHERE rule_name = 'BLACKLIST';

INSERT INTO rule_config_tag (rule_config_id, tag_key, tag_value)
SELECT id, 'category', 'IDENTITY'
FROM   rule_config WHERE rule_name = 'BLACKLIST';

INSERT INTO rule_config_tag (rule_config_id, tag_key, tag_value)
SELECT id, 'severity', 'MEDIUM'
FROM   rule_config WHERE rule_name = 'AMOUNT_ANOMALY';

INSERT INTO rule_config_tag (rule_config_id, tag_key, tag_value)
SELECT id, 'category', 'AMOUNT'
FROM   rule_config WHERE rule_name = 'AMOUNT_ANOMALY';

INSERT INTO rule_config_tag (rule_config_id, tag_key, tag_value)
SELECT id, 'severity', 'MEDIUM'
FROM   rule_config WHERE rule_name = 'VELOCITY';

INSERT INTO rule_config_tag (rule_config_id, tag_key, tag_value)
SELECT id, 'category', 'BEHAVIOUR'
FROM   rule_config WHERE rule_name = 'VELOCITY';
