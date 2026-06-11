-- Aegis Fraud-Shield: V4 — Risk score threshold configuration
-- Stores soft (MANUAL_REVIEW) and hard (DECLINED) composite risk score thresholds.
-- The RiskScoreThresholdRule reads these at startup and can be updated at runtime
-- via the /api/v1/rules/config endpoint without application restart.

INSERT INTO rule_config (rule_name, enabled, description)
VALUES ('RISK_SCORE_THRESHOLD', true,
        'Composite risk scoring: escalates verdict based on total accumulated risk score from all preceding rules.')
ON CONFLICT (rule_name) DO NOTHING;

-- Seed tags: soft threshold (MANUAL_REVIEW) and hard threshold (DECLINED)
INSERT INTO rule_config_tag (rule_config_id, tag_key, tag_value)
SELECT id, 'soft_threshold', '60'
FROM   rule_config WHERE rule_name = 'RISK_SCORE_THRESHOLD'
ON CONFLICT (rule_config_id, tag_key) DO NOTHING;

INSERT INTO rule_config_tag (rule_config_id, tag_key, tag_value)
SELECT id, 'hard_threshold', '90'
FROM   rule_config WHERE rule_name = 'RISK_SCORE_THRESHOLD'
ON CONFLICT (rule_config_id, tag_key) DO NOTHING;

INSERT INTO rule_config_tag (rule_config_id, tag_key, tag_value)
SELECT id, 'severity', 'HIGH'
FROM   rule_config WHERE rule_name = 'RISK_SCORE_THRESHOLD'
ON CONFLICT (rule_config_id, tag_key) DO NOTHING;

INSERT INTO rule_config_tag (rule_config_id, tag_key, tag_value)
SELECT id, 'category', 'COMPOSITE'
FROM   rule_config WHERE rule_name = 'RISK_SCORE_THRESHOLD'
ON CONFLICT (rule_config_id, tag_key) DO NOTHING;
