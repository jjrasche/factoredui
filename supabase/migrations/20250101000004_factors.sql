-- factoredui schema: computed factors + historical snapshots
-- factors = current state (upserted hourly), factor_snapshots = immutable history (inserted daily)

CREATE TABLE factoredui.factors (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  component_path  text NOT NULL,
  factor_name     text NOT NULL,
  factor_tier     factoredui.factor_tier NOT NULL,
  value           double precision NOT NULL,
  computed_at     timestamptz NOT NULL DEFAULT now(),

  CONSTRAINT uq_factors_user_component_factor
    UNIQUE (user_id, component_path, factor_name)
);

CREATE INDEX idx_factors_component_factor
  ON factoredui.factors (component_path, factor_name);

CREATE INDEX idx_factors_user
  ON factoredui.factors (user_id);

CREATE INDEX idx_factors_tier
  ON factoredui.factors (factor_tier);

ALTER TABLE factoredui.factors ENABLE ROW LEVEL SECURITY;


CREATE TABLE factoredui.factor_snapshots (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  component_path  text NOT NULL,
  factor_name     text NOT NULL,
  factor_tier     factoredui.factor_tier NOT NULL,
  value           double precision NOT NULL,
  computed_at     timestamptz NOT NULL,
  snapshot_at     timestamptz NOT NULL DEFAULT now()
);

-- Governor trend queries: factors for a component over time
CREATE INDEX idx_factor_snapshots_component_time
  ON factoredui.factor_snapshots (component_path, snapshot_at DESC);

-- Individual user queries: my factors for a component over time
CREATE INDEX idx_factor_snapshots_user_component_time
  ON factoredui.factor_snapshots (user_id, component_path, snapshot_at DESC);

-- Time range scans on append-only snapshot data
CREATE INDEX idx_factor_snapshots_snapshot_brin
  ON factoredui.factor_snapshots USING brin (snapshot_at);

ALTER TABLE factoredui.factor_snapshots ENABLE ROW LEVEL SECURITY;
