-- observe schema: refresh function for factor materialized views
-- Enables programmatic refresh via RPC (tests, admin tooling, manual triggers)
-- Cron jobs use CONCURRENTLY; this function uses non-concurrent for simplicity.

CREATE OR REPLACE FUNCTION observe.refresh_factor_views()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  REFRESH MATERIALIZED VIEW observe.mv_alarm_completion;
  REFRESH MATERIALIZED VIEW observe.mv_alarm_error_rate;
  REFRESH MATERIALIZED VIEW observe.mv_alarm_drop_off;
  REFRESH MATERIALIZED VIEW observe.mv_diagnostic_retries;
  REFRESH MATERIALIZED VIEW observe.mv_diagnostic_rage_clicks;
  REFRESH MATERIALIZED VIEW observe.mv_diagnostic_dead_clicks;
  REFRESH MATERIALIZED VIEW observe.mv_diagnostic_scroll_reversals;
  REFRESH MATERIALIZED VIEW observe.mv_diagnostic_hesitation;
END;
$$;

-- Only service_role should refresh views (expensive operation)
REVOKE ALL ON FUNCTION observe.refresh_factor_views() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION observe.refresh_factor_views() TO service_role;
