-- auxi schema: computed factors + historical snapshots
-- factors = current state (upserted hourly), factor_snapshots = immutable history (inserted daily)

CREATE TABLE auxi.factors (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  component_path  text NOT NULL,
  factor_name     text NOT NULL,
  factor_tier     auxi.factor_tier NOT NULL,
  value           double precision NOT NULL,
  computed_at     timestamptz NOT NULL DEFAULT now(),

  CONSTRAINT uq_factors_user_component_factor
    UNIQUE (user_id, component_path, factor_name)
);

CREATE INDEX idx_factors_component_factor
  ON auxi.factors (component_path, factor_name);

CREATE INDEX idx_factors_user
  ON auxi.factors (user_id);

CREATE INDEX idx_factors_tier
  ON auxi.factors (factor_tier);

ALTER TABLE auxi.factors ENABLE ROW LEVEL SECURITY;


CREATE TABLE auxi.factor_snapshots (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  component_path  text NOT NULL,
  factor_name     text NOT NULL,
  factor_tier     auxi.factor_tier NOT NULL,
  value           double precision NOT NULL,
  computed_at     timestamptz NOT NULL,
  snapshot_at     timestamptz NOT NULL DEFAULT now()
);

-- Governor trend queries: factors for a component over time
CREATE INDEX idx_factor_snapshots_component_time
  ON auxi.factor_snapshots (component_path, snapshot_at DESC);

-- Individual user queries: my factors for a component over time
CREATE INDEX idx_factor_snapshots_user_component_time
  ON auxi.factor_snapshots (user_id, component_path, snapshot_at DESC);

-- Time range scans on append-only snapshot data
CREATE INDEX idx_factor_snapshots_snapshot_brin
  ON auxi.factor_snapshots USING brin (snapshot_at);

ALTER TABLE auxi.factor_snapshots ENABLE ROW LEVEL SECURITY;
