-- auxi schema: experiments, variants, assignments, exposures
-- The experiment loop: define → assign → expose → measure

CREATE TABLE auxi.experiments (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name            text NOT NULL,
  description     text,
  component_path  text NOT NULL,
  status          auxi.experiment_status NOT NULL DEFAULT 'draft',
  created_at      timestamptz NOT NULL DEFAULT now(),
  concluded_at    timestamptz,

  CONSTRAINT experiments_concluded_when_concluded CHECK (
    (status != 'concluded' AND concluded_at IS NULL) OR
    (status = 'concluded' AND concluded_at IS NOT NULL)
  )
);

-- SDK only queries running experiments
CREATE INDEX idx_experiments_status_running
  ON auxi.experiments (status) WHERE status = 'running';

CREATE INDEX idx_experiments_component
  ON auxi.experiments (component_path);

ALTER TABLE auxi.experiments ENABLE ROW LEVEL SECURITY;


CREATE TABLE auxi.experiment_variants (
  id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  experiment_id       uuid NOT NULL REFERENCES auxi.experiments(id) ON DELETE CASCADE,
  variant_key         text NOT NULL,
  config              jsonb NOT NULL DEFAULT '{}'::jsonb,
  traffic_percentage  smallint NOT NULL DEFAULT 0,

  CONSTRAINT uq_experiment_variants_key
    UNIQUE (experiment_id, variant_key),
  CONSTRAINT traffic_percentage_range
    CHECK (traffic_percentage >= 0 AND traffic_percentage <= 100)
);

CREATE INDEX idx_experiment_variants_experiment
  ON auxi.experiment_variants (experiment_id);

ALTER TABLE auxi.experiment_variants ENABLE ROW LEVEL SECURITY;


-- Immutable: once assigned, never reassigned
CREATE TABLE auxi.experiment_assignments (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  experiment_id   uuid NOT NULL REFERENCES auxi.experiments(id) ON DELETE CASCADE,
  variant_key     text NOT NULL,
  assigned_at     timestamptz NOT NULL DEFAULT now(),

  CONSTRAINT uq_assignments_user_experiment
    UNIQUE (user_id, experiment_id)
);

CREATE INDEX idx_assignments_experiment_variant
  ON auxi.experiment_assignments (experiment_id, variant_key);

ALTER TABLE auxi.experiment_assignments ENABLE ROW LEVEL SECURITY;


-- Allows duplicates: user can be exposed multiple times across sessions.
-- Deduplication happens in analysis queries, not at write time.
CREATE TABLE auxi.experiment_exposures (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  experiment_id   uuid NOT NULL REFERENCES auxi.experiments(id) ON DELETE CASCADE,
  variant_key     text NOT NULL,
  exposed_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_exposures_exposed_brin
  ON auxi.experiment_exposures USING brin (exposed_at);

CREATE INDEX idx_exposures_experiment_user
  ON auxi.experiment_exposures (experiment_id, user_id);

CREATE INDEX idx_exposures_user_experiment
  ON auxi.experiment_exposures (user_id, experiment_id);

ALTER TABLE auxi.experiment_exposures ENABLE ROW LEVEL SECURITY;
