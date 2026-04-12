-- auxi schema: SDUI opt-in component configurations
-- Stores JSON component specs for LLM-generated variants that don't exist in code.
-- Referenced by experiment_variants.config via {config_id: "uuid"}.

CREATE TABLE auxi.variant_configs (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  component_path  text NOT NULL,
  config          jsonb NOT NULL,
  version         integer NOT NULL DEFAULT 1,
  created_at      timestamptz NOT NULL DEFAULT now(),

  CONSTRAINT uq_variant_configs_component_version
    UNIQUE (component_path, version)
);

CREATE INDEX idx_variant_configs_component
  ON auxi.variant_configs (component_path, version DESC);

ALTER TABLE auxi.variant_configs ENABLE ROW LEVEL SECURITY;
