-- auxi schema: governance support
-- Adds winning_variant to experiments + dashboard view for governors.

ALTER TABLE auxi.experiments
  ADD COLUMN winning_variant text;

-- Constraint: winning_variant only set when concluded
ALTER TABLE auxi.experiments
  ADD CONSTRAINT experiments_winner_when_concluded CHECK (
    (status != 'concluded' AND winning_variant IS NULL) OR
    (status = 'concluded')
  );

-- Governor dashboard: one row per experiment with assignment counts and status
CREATE OR REPLACE VIEW auxi.v_experiment_summary AS
SELECT
  e.id              AS experiment_id,
  e.name,
  e.component_path,
  e.status,
  e.winning_variant,
  e.created_at,
  e.concluded_at,
  v.variant_key,
  v.traffic_percentage,
  COUNT(DISTINCT a.user_id) AS assigned_users,
  COUNT(DISTINCT x.user_id) AS exposed_users
FROM auxi.experiments e
LEFT JOIN auxi.experiment_variants v
  ON v.experiment_id = e.id
LEFT JOIN auxi.experiment_assignments a
  ON a.experiment_id = e.id AND a.variant_key = v.variant_key
LEFT JOIN auxi.experiment_exposures x
  ON x.experiment_id = e.id AND x.variant_key = v.variant_key
GROUP BY
  e.id, e.name, e.component_path, e.status,
  e.winning_variant, e.created_at, e.concluded_at,
  v.variant_key, v.traffic_percentage
ORDER BY e.created_at DESC, v.variant_key;
