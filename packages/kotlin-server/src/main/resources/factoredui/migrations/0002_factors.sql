-- Factor engine, wave 2 — v1 factor taxonomy.
--
-- Materializes the three-tier factor model as Postgres views over the capture
-- tables. No funnel is encoded yet, so v1 ships only factors computable from
-- flat events: error_rate (alarm) + rage/dead/scroll-reversal rates and
-- decision latency (diagnostic). Completion/drop-off and the structural tier
-- (Lighthouse) are deferred — see DECISIONS.md.
--
-- Views (not materialized views) so reads always reflect the latest events;
-- the capture indexes on (component_path, occurred_at) and (session_id,
-- occurred_at) back these aggregations. Re-runnable: CREATE OR REPLACE.

-- Per (user, session, component) decision latency: time from the component's
-- first impression to its first deliberate interaction (click/input/submit).
-- Only well-ordered pairs count (interaction at or after impression), so
-- hesitation is never negative.
CREATE OR REPLACE VIEW factoredui_session_hesitation AS
SELECT
    s.user_id,
    e.session_id,
    e.component_path,
    EXTRACT(EPOCH FROM (
        MIN(e.occurred_at) FILTER (WHERE e.event_type IN ('click', 'input', 'submit'))
        - MIN(e.occurred_at) FILTER (WHERE e.event_type = 'impression')
    )) * 1000.0 AS hesitation_ms
FROM factoredui_events e
JOIN factoredui_sessions s ON s.id = e.session_id
GROUP BY s.user_id, e.session_id, e.component_path
HAVING MIN(e.occurred_at) FILTER (WHERE e.event_type = 'impression') IS NOT NULL
   AND MIN(e.occurred_at) FILTER (WHERE e.event_type IN ('click', 'input', 'submit'))
       >= MIN(e.occurred_at) FILTER (WHERE e.event_type = 'impression');

-- One row per (user, component, factor). A factor is absent (not zero) when its
-- denominator is zero — NULLIF turns 0/0 into NULL and the outer filter drops
-- it, so "no clicks" yields no rage/dead-click-rate rather than a fake 0.
CREATE OR REPLACE VIEW factoredui_user_factors AS
WITH event_counts AS (
    SELECT
        s.user_id,
        e.component_path,
        COUNT(*) AS total_events,
        COUNT(*) FILTER (WHERE e.event_type = 'error') AS error_count,
        COUNT(*) FILTER (WHERE e.event_type = 'click') AS click_count,
        COUNT(*) FILTER (WHERE e.event_type = 'rage_click') AS rage_click_count,
        COUNT(*) FILTER (WHERE e.event_type = 'dead_click') AS dead_click_count,
        COUNT(*) FILTER (WHERE e.event_type = 'scroll') AS scroll_count,
        COUNT(*) FILTER (WHERE e.event_type = 'scroll_reversal') AS scroll_reversal_count
    FROM factoredui_events e
    JOIN factoredui_sessions s ON s.id = e.session_id
    GROUP BY s.user_id, e.component_path
),
hesitation AS (
    SELECT
        user_id,
        component_path,
        percentile_cont(0.5) WITHIN GROUP (ORDER BY hesitation_ms) AS hesitation_p50_ms
    FROM factoredui_session_hesitation
    GROUP BY user_id, component_path
)
SELECT user_id, component_path, factor_name, factor_tier, value, now() AS computed_at
FROM (
    SELECT user_id, component_path,
           'error_rate' AS factor_name, 'alarm' AS factor_tier,
           error_count::double precision / NULLIF(total_events, 0) AS value
    FROM event_counts
    UNION ALL
    SELECT user_id, component_path,
           'rage_click_rate', 'diagnostic',
           rage_click_count::double precision / NULLIF(click_count, 0)
    FROM event_counts
    UNION ALL
    SELECT user_id, component_path,
           'dead_click_rate', 'diagnostic',
           dead_click_count::double precision / NULLIF(click_count, 0)
    FROM event_counts
    UNION ALL
    SELECT user_id, component_path,
           'scroll_reversal_rate', 'diagnostic',
           scroll_reversal_count::double precision / NULLIF(scroll_count, 0)
    FROM event_counts
    UNION ALL
    SELECT user_id, component_path,
           'hesitation_time_p50_ms', 'diagnostic',
           hesitation_p50_ms
    FROM hesitation
) factor_rows
WHERE value IS NOT NULL;

-- Cross-user rollup per (component, factor) — the row the factor dashboard
-- binds to. stddev_value is NULL for a single sample (Postgres stddev_samp);
-- that distinction is preserved, not coerced to zero.
CREATE OR REPLACE VIEW factoredui_component_factors AS
SELECT
    component_path,
    factor_name,
    factor_tier,
    COUNT(DISTINCT user_id) AS user_count,
    AVG(value) AS avg_value,
    percentile_cont(0.5) WITHIN GROUP (ORDER BY value) AS median_value,
    percentile_cont(0.95) WITHIN GROUP (ORDER BY value) AS p95_value,
    MIN(value) AS min_value,
    MAX(value) AS max_value,
    STDDEV(value) AS stddev_value
FROM factoredui_user_factors
GROUP BY component_path, factor_name, factor_tier;
