-- auxi schema: pg_cron scheduled jobs
-- Factor refresh → upsert → snapshot → vector computation → clustering

-- Hourly: refresh all factor materialized views (CONCURRENTLY requires unique index on each MV)
SELECT cron.schedule(
  'auxi-refresh-factors',
  '0 * * * *',
  $$
    REFRESH MATERIALIZED VIEW CONCURRENTLY auxi.mv_alarm_completion;
    REFRESH MATERIALIZED VIEW CONCURRENTLY auxi.mv_alarm_error_rate;
    REFRESH MATERIALIZED VIEW CONCURRENTLY auxi.mv_alarm_drop_off;
    REFRESH MATERIALIZED VIEW CONCURRENTLY auxi.mv_diagnostic_retries;
    REFRESH MATERIALIZED VIEW CONCURRENTLY auxi.mv_diagnostic_rage_clicks;
    REFRESH MATERIALIZED VIEW CONCURRENTLY auxi.mv_diagnostic_dead_clicks;
    REFRESH MATERIALIZED VIEW CONCURRENTLY auxi.mv_diagnostic_scroll_reversals;
    REFRESH MATERIALIZED VIEW CONCURRENTLY auxi.mv_diagnostic_hesitation;
  $$
);

-- Hourly +5min: upsert computed factors from unified view into factors table
SELECT cron.schedule(
  'auxi-upsert-factors',
  '5 * * * *',
  $$
    INSERT INTO auxi.factors (user_id, component_path, factor_name, factor_tier, value, computed_at)
    SELECT user_id, component_path, factor_name, factor_tier, value, computed_at
    FROM auxi.v_factors_current
    ON CONFLICT (user_id, component_path, factor_name)
    DO UPDATE SET
      value = EXCLUDED.value,
      computed_at = EXCLUDED.computed_at;
  $$
);

-- Daily 02:00 UTC: snapshot current factors for point-in-time queries
SELECT cron.schedule(
  'auxi-snapshot-factors',
  '0 2 * * *',
  $$
    INSERT INTO auxi.factor_snapshots (user_id, component_path, factor_name, factor_tier, value, computed_at, snapshot_at)
    SELECT user_id, component_path, factor_name, factor_tier, value, computed_at, now()
    FROM auxi.factors;
  $$
);

-- Daily 02:30 UTC: pivot factors into vector(16) per user
-- Fixed dimension order: completion_rate, drop_off_rate, error_rate, hesitation_ms,
-- rage_click_count, scroll_reversal_count, dead_click_count, retry_count,
-- lighthouse_perf, cls, lcp_ms, contrast_ratio, tap_target_ok, [3 reserved]
SELECT cron.schedule(
  'auxi-compute-vectors',
  '30 2 * * *',
  $$
    INSERT INTO auxi.user_factor_vectors (user_id, vector, updated_at)
    SELECT
      f.user_id,
      ARRAY[
        COALESCE(MAX(f.value) FILTER (WHERE f.factor_name = 'completion_rate'), 0),
        COALESCE(MAX(f.value) FILTER (WHERE f.factor_name = 'drop_off_rate'), 0),
        COALESCE(MAX(f.value) FILTER (WHERE f.factor_name = 'error_rate'), 0),
        COALESCE(MAX(f.value) FILTER (WHERE f.factor_name = 'hesitation_ms'), 0),
        COALESCE(MAX(f.value) FILTER (WHERE f.factor_name = 'rage_click_count'), 0),
        COALESCE(MAX(f.value) FILTER (WHERE f.factor_name = 'scroll_reversal_count'), 0),
        COALESCE(MAX(f.value) FILTER (WHERE f.factor_name = 'dead_click_count'), 0),
        COALESCE(MAX(f.value) FILTER (WHERE f.factor_name = 'retry_count'), 0),
        COALESCE(MAX(f.value) FILTER (WHERE f.factor_name = 'lighthouse_perf'), 0),
        COALESCE(MAX(f.value) FILTER (WHERE f.factor_name = 'cls'), 0),
        COALESCE(MAX(f.value) FILTER (WHERE f.factor_name = 'lcp_ms'), 0),
        COALESCE(MAX(f.value) FILTER (WHERE f.factor_name = 'contrast_ratio'), 0),
        COALESCE(MAX(f.value) FILTER (WHERE f.factor_name = 'tap_target_ok'), 0),
        0, 0, 0
      ]::vector(16),
      now()
    FROM auxi.factors f
    GROUP BY f.user_id
    ON CONFLICT (user_id)
    DO UPDATE SET
      vector = EXCLUDED.vector,
      updated_at = EXCLUDED.updated_at;
  $$
);

-- Daily 03:00 UTC: trigger clustering edge function via pg_net
-- Clustering runs in an edge function because pgvector has no built-in k-means.
-- The edge function reads user_factor_vectors, runs k-means, writes to user_clusters.
SELECT cron.schedule(
  'auxi-cluster-users',
  '0 3 * * *',
  $$
    SELECT net.http_post(
      url := current_setting('app.supabase_url') || '/functions/v1/auxi-cluster',
      headers := jsonb_build_object(
        'Authorization', 'Bearer ' || current_setting('app.service_role_key'),
        'Content-Type', 'application/json'
      ),
      body := '{}'::jsonb
    );
  $$
);
