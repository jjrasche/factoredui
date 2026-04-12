-- auxi schema: bulk factor delta computation
-- Replaces N×M sequential queryFactorDelta calls with a single SQL function.
-- Returns averaged deltas for a set of users across multiple factors.

CREATE OR REPLACE FUNCTION auxi.bulk_factor_deltas(
  p_user_ids     uuid[],
  p_component    text,
  p_factor_names text[],
  p_before       timestamptz,
  p_after        timestamptz
)
RETURNS TABLE (
  factor_name text,
  avg_before  double precision,
  avg_after   double precision,
  avg_delta   double precision
)
LANGUAGE sql STABLE AS $$
  WITH before_snapshots AS (
    SELECT DISTINCT ON (fs.user_id, fs.factor_name)
      fs.user_id,
      fs.factor_name,
      fs.value
    FROM auxi.factor_snapshots fs
    WHERE fs.user_id = ANY(p_user_ids)
      AND fs.component_path = p_component
      AND fs.factor_name = ANY(p_factor_names)
      AND fs.snapshot_at <= p_before
    ORDER BY fs.user_id, fs.factor_name, fs.snapshot_at DESC
  ),
  after_snapshots AS (
    SELECT DISTINCT ON (fs.user_id, fs.factor_name)
      fs.user_id,
      fs.factor_name,
      fs.value
    FROM auxi.factor_snapshots fs
    WHERE fs.user_id = ANY(p_user_ids)
      AND fs.component_path = p_component
      AND fs.factor_name = ANY(p_factor_names)
      AND fs.snapshot_at <= p_after
    ORDER BY fs.user_id, fs.factor_name, fs.snapshot_at DESC
  ),
  paired AS (
    SELECT
      b.factor_name,
      b.value AS before_val,
      a.value AS after_val,
      a.value - b.value AS delta
    FROM before_snapshots b
    JOIN after_snapshots a
      ON a.user_id = b.user_id AND a.factor_name = b.factor_name
  )
  SELECT
    p.factor_name,
    AVG(p.before_val) AS avg_before,
    AVG(p.after_val)  AS avg_after,
    AVG(p.delta)       AS avg_delta
  FROM paired p
  GROUP BY p.factor_name;
$$;
