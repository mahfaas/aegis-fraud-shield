-- Aegis Fraud-Shield: V8 — IP reputation / geo-mismatch table
--
-- Backs the opt-in IpReputationRule (fraud.rules.ip-reputation.enabled).
-- Each row maps a source-IP prefix to its known country and risk category.
-- The rule matches a transaction's sourceIp against the longest matching prefix and:
--   - flags a geo-mismatch if the IP's known country differs from the transaction's declared country
--   - applies the row's risk_score (0/50/100, same convention as RuleResult) for the category itself
--
-- Prefixes below are illustrative example ranges for demo/interview purposes, not a
-- real IP-intelligence feed.

CREATE TABLE ip_reputation (
    id           BIGSERIAL     PRIMARY KEY,
    ip_prefix    VARCHAR(45)   NOT NULL UNIQUE,
    country      VARCHAR(2)    NOT NULL,
    category     VARCHAR(20)   NOT NULL,
    risk_score   INT           NOT NULL DEFAULT 0
                     CHECK (risk_score IN (0, 50, 100)),
    created_at   TIMESTAMP     NOT NULL DEFAULT NOW()
);

INSERT INTO ip_reputation (ip_prefix, country, category, risk_score) VALUES
    ('185.220.',  'DE', 'TOR_EXIT',    100),
    ('192.42.116.', 'NL', 'TOR_EXIT',  100),
    ('45.83.',    'NL', 'DATACENTER',  50),
    ('195.181.',  'RO', 'DATACENTER',  50);
