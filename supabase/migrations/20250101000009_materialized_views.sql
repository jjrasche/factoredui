-- auxi schema: factor computation views + experiment results + governor aggregates
-- Separate MV per factor: independent refresh, independent failure, each with unique index for CONCURRENTLY.
-- Structural factors (Lighthouse, CLS, LCP, contrast, tap targets) are inserted directly by edge functions.

-- Alarm: completion rate (impression → completion ratio per user per component)
CREATE MATERIALIZED VIEW auxi.mv_alarm_completion AS
SELECT
  e_start.user_id,
  e_start.component_path,
  'completion_rate'::text AS factor_name,
  'alarm'::auxi.factor_tier AS factor_tier,
  COUNT(DISTINCT e_end.session_id) FILTER (
    WHERE e_end.event_type = 'navigation'
      AND e_end.payload->>'action' = 'complete'
  )::double precision
  / NULLIF(COUNT(DISTINCT e_start.session_id), 0) AS value,
  now() AS computed_at
FROM auxi.events e_start
LEFT JOIN auxi.events e_end
  ON e_end.user_id = e_start.user_id
  AND e_end.session_id = e_start.session_id
  AND e_end.component_path = e_start.component_path
  AND e_end.event_type = 'navigation'
WHERE e_start.event_type = 'impression'
  AND e_start.created_at > now() - interval '7 days'
GROUP BY e_start.user_id, e_start.component_path
WITH NO DATA;

CREATE UNIQUE INDEX ON auxi.mv_alarm_completion (user_id, component_path);


-- Alarm: error rate (error events / total events)
CREATE MATERIALIZED VIEW auxi.mv_alarm_error_rate AS
SELECT
  e.user_id,
  e.component_path,
  'error_rate'::text AS factor_name,
  'alarm'::auxi.factor_tier AS factor_tier,
  COUNT(*) FILTER (WHERE e.event_type = 'error')::double precision
    / NULLIF(COUNT(*), 0) AS value,
  now() AS computed_at
FROM auxi.events e
WHERE e.created_at > now() - interval '7 days'
GROUP BY e.user_id, e.component_path
WITH NO DATA;

CREATE UNIQUE INDEX ON auxi.mv_alarm_error_rate (user_id, component_path);


-- Diagnostic: rage clicks
CREATE MATERIALIZED VIEW auxi.mv_diagnostic_rage_clicks AS
SELECT
  e.user_id,
  e.component_path,
  'rage_click_count'::text AS factor_name,
  'diagnostic'::auxi.factor_tier AS factor_tier,
  COUNT(*)::double precision AS value,
  now() AS computed_at
FROM auxi.events e
WHERE e.event_type = 'rage_click'
  AND e.created_at > now() - interval '7 days'
GROUP BY e.user_id, e.component_path
WITH NO DATA;

CREATE UNIQUE INDEX ON auxi.mv_diagnostic_rage_clicks (user_id, component_path);


-- Diagnostic: dead clicks
CREATE MATERIALIZED VIEW auxi.mv_diagnostic_dead_clicks AS
SELECT
  e.user_id,
  e.component_path,
  'dead_click_count'::text AS factor_name,
  'diagnostic'::auxi.factor_tier AS factor_tier,
  COUNT(*)::double precision AS value,
  now() AS computed_at
FROM auxi.events e
WHERE e.event_type = 'dead_click'
  AND e.created_at > now() - interval '7 days'
GROUP BY e.user_id, e.component_path
WITH NO DATA;

CREATE UNIQUE INDEX ON auxi.mv_diagnostic_dead_clicks (user_id, component_path);


-- Diagnostic: scroll reversals
CREATE MATERIALIZED VIEW auxi.mv_diagnostic_scroll_reversals AS
SELECT
  e.user_id,
  e.component_path,
  'scroll_reversal_count'::text AS factor_name,
  'diagnostic'::auxi.factor_tier AS factor_tier,
  COUNT(*)::double precision AS value,
  now() AS computed_at
FROM auxi.events e
WHERE e.event_type = 'scroll_reversal'
  AND e.created_at > now() - interval '7 days'
GROUP BY e.user_id, e.component_path
WITH NO DATA;

CREATE UNIQUE INDEX ON auxi.mv_diagnostic_scroll_reversals (user_id, component_path);


-- Diagnostic: hesitation (avg ms from impression to first interaction)
CREATE MATERIALIZED VIEW auxi.mv_diagnostic_hesitation AS
SELECT
  imp.user_id,
  imp.component_path,
  'hesitation_ms'::text AS factor_name,
  'diagnostic'::auxi.factor_tier AS factor_tier,
  AVG(EXTRACT(EPOCH FROM (first_act.created_at - imp.created_at)) * 1000) AS value,
  now() AS computed_at
FROM auxi.events imp
INNER JOIN LATERAL (
  SELECT e2.created_at
  FROM auxi.events e2
  WHERE e2.session_id = imp.session_id
    AND e2.component_path = imp.component_path
    AND e2.event_type IN ('click', 'input', 'scroll', 'submit')
    AND e2.created_at > imp.created_at
  ORDER BY e2.created_at
  LIMIT 1
) first_act ON true
WHERE imp.event_type = 'impression'
  AND imp.created_at > now() - interval '7 days'
GROUP BY imp.user_id, imp.component_path
WITH NO DATA;

CREATE UNIQUE INDEX ON auxi.mv_diagnostic_hesitation (user_id, component_path);


-- Alarm: drop-off rate (sessions with impression but no completion or navigation away)
CREATE MATERIALIZED VIEW auxi.mv_alarm_drop_off AS
SELECT
  e.user_id,
  e.component_path,
  'drop_off_rate'::text AS factor_name,
  'alarm'::auxi.factor_tier AS factor_tier,
  1.0 - (
    COUNT(DISTINCT e.session_id) FILTER (
      WHERE e.event_type = 'navigation'
    )::double precision
    / NULLIF(COUNT(DISTINCT e.session_id), 0)
  ) AS value,
  now() AS computed_at
FROM auxi.events e
WHERE e.created_at > now() - interval '7 days'
GROUP BY e.user_id, e.component_path
WITH NO DATA;

CREATE UNIQUE INDEX ON auxi.mv_alarm_drop_off (user_id, component_path);


-- Diagnostic: retry count (repeated submit/click actions on same component within a session)
CREATE MATERIALIZED VIEW auxi.mv_diagnostic_retries AS
SELECT
  e.user_id,
  e.component_path,
  'retry_count'::text AS factor_name,
  'diagnostic'::auxi.factor_tier AS factor_tier,
  GREATEST(COUNT(*)::double precision - COUNT(DISTINCT e.session_id), 0) AS value,
  now() AS computed_at
FROM auxi.events e
WHERE e.event_type IN ('submit', 'click')
  AND e.created_at > now() - interval '7 days'
GROUP BY e.user_id, e.component_path
WITH NO DATA;

CREATE UNIQUE INDEX ON auxi.mv_diagnostic_retries (user_id, component_path);


-- Unified view: all current factors from all MVs
CREATE OR REPLACE VIEW auxi.v_factors_current AS
  SELECT user_id, component_path, factor_name, factor_tier, value, computed_at
    FROM auxi.mv_alarm_completion
  UNION ALL
  SELECT user_id, component_path, factor_name, factor_tier, value, computed_at
    FROM auxi.mv_alarm_error_rate
  UNION ALL
  SELECT user_id, component_path, factor_name, factor_tier, value, computed_at
    FROM auxi.mv_alarm_drop_off
  UNION ALL
  SELECT user_id, component_path, factor_name, factor_tier, value, computed_at
    FROM auxi.mv_diagnostic_retries
  UNION ALL
  SELECT user_id, component_path, factor_name, factor_tier, value, computed_at
    FROM auxi.mv_diagnostic_rage_clicks
  UNION ALL
  SELECT user_id, component_path, factor_name, factor_tier, value, computed_at
    FROM auxi.mv_diagnostic_dead_clicks
  UNION ALL
  SELECT user_id, component_path, factor_name, factor_tier, value, computed_at
    FROM auxi.mv_diagnostic_scroll_reversals
  UNION ALL
  SELECT user_id, component_path, factor_name, factor_tier, value, computed_at
    FROM auxi.mv_diagnostic_hesitation;


-- Experiment results: before/after factor comparison per assignment
-- Returns one row per factor per assignment (not one row per assignment)
CREATE OR REPLACE VIEW auxi.v_experiment_results AS
SELECT
  ea.experiment_id,
  ea.variant_key,
  ea.user_id,
  ea.assigned_at,
  factor_list.factor_name,
  factor_list.factor_tier,
  fs_before.value AS value_before,
  fs_after.value AS value_after,
  fs_after.value - fs_before.value AS value_delta,
  fs_before.snapshot_at AS snapshot_before,
  fs_after.snapshot_at AS snapshot_after
FROM auxi.experiment_assignments ea
JOIN auxi.experiments exp ON exp.id = ea.experiment_id
CROSS JOIN LATERAL (
  SELECT DISTINCT fs.factor_name, fs.factor_tier
  FROM auxi.factor_snapshots fs
  WHERE fs.user_id = ea.user_id
    AND fs.component_path = exp.component_path
) factor_list
LEFT JOIN LATERAL (
  SELECT fs.value, fs.snapshot_at
  FROM auxi.factor_snapshots fs
  WHERE fs.user_id = ea.user_id
    AND fs.component_path = exp.component_path
    AND fs.factor_name = factor_list.factor_name
    AND fs.snapshot_at <= ea.assigned_at
  ORDER BY fs.snapshot_at DESC
  LIMIT 1
) fs_before ON true
LEFT JOIN LATERAL (
  SELECT fs.value, fs.snapshot_at
  FROM auxi.factor_snapshots fs
  WHERE fs.user_id = ea.user_id
    AND fs.component_path = exp.component_path
    AND fs.factor_name = factor_list.factor_name
    AND fs.snapshot_at > ea.assigned_at
  ORDER BY fs.snapshot_at DESC
  LIMIT 1
) fs_after ON true;


-- Governor view: aggregated anonymized factors per component (no user_id exposed)
-- Application layer should enforce minimum user_count >= 5 to prevent re-identification
CREATE OR REPLACE VIEW auxi.v_component_factors_agg AS
SELECT
  component_path,
  factor_name,
  factor_tier,
  COUNT(DISTINCT user_id) AS user_count,
  AVG(value) AS avg_value,
  PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY value) AS median_value,
  PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY value) AS p95_value,
  MIN(value) AS min_value,
  MAX(value) AS max_value,
  STDDEV(value) AS stddev_value
FROM auxi.v_factors_current
GROUP BY component_path, factor_name, factor_tier;


-- Bootstrap: initial non-concurrent refresh so CONCURRENTLY works in cron jobs.
-- These will return empty results on first deploy (no events yet) but satisfy the requirement
-- that CONCURRENTLY needs at least one prior population.
REFRESH MATERIALIZED VIEW auxi.mv_alarm_completion;
REFRESH MATERIALIZED VIEW auxi.mv_alarm_error_rate;
REFRESH MATERIALIZED VIEW auxi.mv_alarm_drop_off;
REFRESH MATERIALIZED VIEW auxi.mv_diagnostic_retries;
REFRESH MATERIALIZED VIEW auxi.mv_diagnostic_rage_clicks;
REFRESH MATERIALIZED VIEW auxi.mv_diagnostic_dead_clicks;
REFRESH MATERIALIZED VIEW auxi.mv_diagnostic_scroll_reversals;
REFRESH MATERIALIZED VIEW auxi.mv_diagnostic_hesitation;
