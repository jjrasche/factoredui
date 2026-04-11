-- auxi schema: SDUI spec storage
-- Signed declarative UI specs. The source of truth for what users see.
-- Experiments assign users to specific spec versions.
-- Governance promotes winning variants to active.

CREATE TABLE auxi.ui_specs (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  spec_version    integer NOT NULL,
  renderer_min    integer NOT NULL DEFAULT 1,
  component_tree  jsonb NOT NULL,
  spec_hash       text NOT NULL,
  signature       text NOT NULL,
  created_at      timestamptz NOT NULL DEFAULT now(),
  created_by      text NOT NULL,

  CONSTRAINT uq_ui_specs_version UNIQUE (spec_version),
  CONSTRAINT ui_specs_version_positive CHECK (spec_version > 0),
  CONSTRAINT ui_specs_renderer_min_positive CHECK (renderer_min > 0)
);

CREATE INDEX idx_ui_specs_version
  ON auxi.ui_specs (spec_version DESC);

ALTER TABLE auxi.ui_specs ENABLE ROW LEVEL SECURITY;

COMMENT ON TABLE auxi.ui_specs IS
  'Signed declarative UI specs. Each version is immutable once stored.';

COMMENT ON COLUMN auxi.ui_specs.created_by IS
  'Who created this spec: governance, experiment:<id>, manual, llm';

COMMENT ON COLUMN auxi.ui_specs.spec_hash IS
  'SHA-256 hash of component_tree JSON for integrity verification';

COMMENT ON COLUMN auxi.ui_specs.signature IS
  'Ed25519 signature of spec_hash for authenticity verification';


CREATE TABLE auxi.ui_active (
  platform        text PRIMARY KEY,
  spec_id         uuid NOT NULL REFERENCES auxi.ui_specs(id),
  activated_at    timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE auxi.ui_active ENABLE ROW LEVEL SECURITY;

COMMENT ON TABLE auxi.ui_active IS
  'Which spec version is currently active per platform. Updated by governance.';


-- Grants
GRANT SELECT ON auxi.ui_specs TO authenticated;
GRANT ALL ON auxi.ui_specs TO service_role;
GRANT SELECT ON auxi.ui_active TO authenticated;
GRANT ALL ON auxi.ui_active TO service_role;
