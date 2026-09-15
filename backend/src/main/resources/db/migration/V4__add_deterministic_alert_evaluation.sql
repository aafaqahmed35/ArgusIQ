ALTER TABLE alert_rules ADD COLUMN name varchar(255) NOT NULL DEFAULT 'Alert rule';
ALTER TABLE alert_rules ADD COLUMN severity varchar(40) NOT NULL DEFAULT 'WARNING';
ALTER TABLE alert_rules ADD COLUMN service_name varchar(255);
ALTER TABLE alert_rules ADD COLUMN minimum_samples bigint;
ALTER TABLE alert_rules ADD COLUMN created_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE alert_rules ADD COLUMN updated_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE alert_rules ALTER COLUMN threshold DROP NOT NULL;
ALTER TABLE alert_rules ALTER COLUMN window_seconds DROP NOT NULL;
ALTER TABLE alert_rules ALTER COLUMN comparator DROP NOT NULL;

-- Pre-V4 rules were unevaluated scheduler placeholders. They remain visible but
-- are disabled until recreated using one of the deterministic alert contracts.
UPDATE alert_rules SET enabled = FALSE;

ALTER TABLE alerts ADD COLUMN rule_id bigint;
ALTER TABLE alerts ADD COLUMN rule_name varchar(255);
ALTER TABLE alerts ADD COLUMN first_triggered_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE alerts ADD COLUMN last_triggered_at timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE alerts ADD COLUMN evaluation_time timestamp(6) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE alerts ADD COLUMN window_start timestamp(6) without time zone;
ALTER TABLE alerts ADD COLUMN window_end timestamp(6) without time zone;
ALTER TABLE alerts ADD COLUMN observed_at timestamp(6) without time zone;
ALTER TABLE alerts ADD COLUMN metric varchar(80);
ALTER TABLE alerts ADD COLUMN observed_value double precision;
ALTER TABLE alerts ADD COLUMN threshold_value double precision;
ALTER TABLE alerts ADD COLUMN unit varchar(40);
ALTER TABLE alerts ADD COLUMN sample_count bigint;
ALTER TABLE alerts ADD COLUMN error_count bigint;
ALTER TABLE alerts ADD COLUMN operation_name varchar(255);
ALTER TABLE alerts ADD COLUMN observed_status varchar(40);
ALTER TABLE alerts ADD COLUMN http_status integer;
ALTER TABLE alerts ADD COLUMN last_evaluation_state varchar(40) NOT NULL DEFAULT 'MATCHED';
ALTER TABLE alerts ADD COLUMN acknowledged_at timestamp(6) without time zone;
ALTER TABLE alerts ADD COLUMN active_key varchar(512);

-- created_time is the only historical lifecycle timestamp available for legacy
-- rows. Preserve it as the earliest truthful trigger/evaluation estimate rather
-- than presenting migration execution time as occurrence history.
UPDATE alerts
SET first_triggered_at = created_time,
    last_triggered_at = created_time,
    evaluation_time = created_time,
    last_evaluation_state = 'LEGACY_IMPORTED';

-- Preserve the old free-form evidence as explicitly labelled legacy text. It
-- cannot be converted truthfully into deterministic structured evidence.
UPDATE alerts
SET description = CASE
    WHEN evidence IS NULL OR TRIM(evidence) = '' THEN description
    WHEN description IS NULL OR TRIM(description) = '' THEN 'Legacy evidence: ' || evidence
    ELSE description || CHR(10) || 'Legacy evidence: ' || evidence
END;

-- owner/recommendation placeholder values were never part of the deterministic
-- alert contract and are intentionally retired. No recommendation is inferred.
ALTER TABLE alerts DROP COLUMN evidence;
ALTER TABLE alerts DROP COLUMN recommendation_placeholder;
ALTER TABLE alerts DROP COLUMN owner_placeholder;

ALTER TABLE alerts ADD CONSTRAINT fk_alert_rule FOREIGN KEY (rule_id) REFERENCES alert_rules (id);
ALTER TABLE alerts ADD CONSTRAINT uk_alert_active_key UNIQUE (active_key);
CREATE INDEX idx_alert_rule_id ON alerts (rule_id);
CREATE INDEX idx_alert_last_triggered ON alerts (last_triggered_at);
