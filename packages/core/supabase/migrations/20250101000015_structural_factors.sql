-- auxi schema: structural factor materialized views
-- Derived from impression event payloads: component_depth, sibling_count, form_field_count.
-- SDK writes these as payload keys on impression events.

-- Structural: component depth in the DOM tree
CREATE MATERIALIZED VIEW auxi.mv_structural_component_depth AS
SELECT
  e.user_id,
  e.component_path,
  'component_depth'::text AS factor_name,
  'structural'::auxi.factor_tier AS factor_tier,
  AVG((e.payload->>'component_depth')::double precision) AS value,
  now() AS computed_at
FROM auxi.events e
WHERE e.event_type = 'impression'
  AND e.payload ? 'component_depth'
  AND e.created_at > now() - interval '7 days'
GROUP BY e.user_id, e.component_path
WITH NO DATA;

CREATE UNIQUE INDEX ON auxi.mv_structural_component_depth (user_id, component_path);


-- Structural: sibling count at the component's level
CREATE MATERIALIZED VIEW auxi.mv_structural_sibling_count AS
SELECT
  e.user_id,
  e.component_path,
  'sibling_count'::text AS factor_name,
  'structural'::auxi.factor_tier AS factor_tier,
  AVG((e.payload->>'sibling_count')::double precision) AS value,
  now() AS computed_at
FROM auxi.events e
WHERE e.event_type = 'impression'
  AND e.payload ? 'sibling_count'
  AND e.created_at > now() - interval '7 days'
GROUP BY e.user_id, e.component_path
WITH NO DATA;

CREATE UNIQUE INDEX ON auxi.mv_structural_sibling_count (user_id, component_path);


-- Structural: form field count within the component
CREATE MATERIALIZED VIEW auxi.mv_structural_form_field_count AS
SELECT
  e.user_id,
  e.component_path,
  'form_field_count'::text AS factor_name,
  'structural'::auxi.factor_tier AS factor_tier,
  AVG((e.payload->>'form_field_count')::double precision) AS value,
  now() AS computed_at
FROM auxi.events e
WHERE e.event_type = 'impression'
  AND e.payload ? 'form_field_count'
  AND e.created_at > now() - interval '7 days'
GROUP BY e.user_id, e.component_path
WITH NO DATA;

CREATE UNIQUE INDEX ON auxi.mv_structural_form_field_count (user_id, component_path);


-- Extend v_factors_current to include structural factors
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
    FROM auxi.mv_diagnostic_hesitation
  UNION ALL
  SELECT user_id, component_path, factor_name, factor_tier, value, computed_at
    FROM auxi.mv_structural_component_depth
  UNION ALL
  SELECT user_id, component_path, factor_name, factor_tier, value, computed_at
    FROM auxi.mv_structural_sibling_count
  UNION ALL
  SELECT user_id, component_path, factor_name, factor_tier, value, computed_at
    FROM auxi.mv_structural_form_field_count;


-- Extend refresh function to include structural views
CREATE OR REPLACE FUNCTION auxi.refresh_factor_views()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  REFRESH MATERIALIZED VIEW auxi.mv_alarm_completion;
  REFRESH MATERIALIZED VIEW auxi.mv_alarm_error_rate;
  REFRESH MATERIALIZED VIEW auxi.mv_alarm_drop_off;
  REFRESH MATERIALIZED VIEW auxi.mv_diagnostic_retries;
  REFRESH MATERIALIZED VIEW auxi.mv_diagnostic_rage_clicks;
  REFRESH MATERIALIZED VIEW auxi.mv_diagnostic_dead_clicks;
  REFRESH MATERIALIZED VIEW auxi.mv_diagnostic_scroll_reversals;
  REFRESH MATERIALIZED VIEW auxi.mv_diagnostic_hesitation;
  REFRESH MATERIALIZED VIEW auxi.mv_structural_component_depth;
  REFRESH MATERIALIZED VIEW auxi.mv_structural_sibling_count;
  REFRESH MATERIALIZED VIEW auxi.mv_structural_form_field_count;
END;
$$;


-- Bootstrap: initial non-concurrent refresh
REFRESH MATERIALIZED VIEW auxi.mv_structural_component_depth;
REFRESH MATERIALIZED VIEW auxi.mv_structural_sibling_count;
REFRESH MATERIALIZED VIEW auxi.mv_structural_form_field_count;
