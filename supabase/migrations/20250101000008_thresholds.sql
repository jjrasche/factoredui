-- observe schema: governance-defined factor thresholds
-- Governors set thresholds that trigger alerts or spawn experiments.
-- NULL component_path = global threshold (applies to any component).

CREATE TABLE observe.thresholds (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  factor_name     text NOT NULL,
  component_path  text,
  operator        observe.threshold_operator NOT NULL,
  value           double precision NOT NULL,
  action          observe.threshold_action NOT NULL,
  created_by      uuid NOT NULL REFERENCES auth.users(id),
  created_at      timestamptz NOT NULL DEFAULT now(),

  CONSTRAINT uq_thresholds_factor_component_op
    UNIQUE (factor_name, component_path, operator)
);

CREATE INDEX idx_thresholds_factor
  ON observe.thresholds (factor_name);

ALTER TABLE observe.thresholds ENABLE ROW LEVEL SECURITY;
