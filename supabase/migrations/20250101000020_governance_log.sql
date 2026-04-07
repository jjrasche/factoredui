-- auxi schema: append-only governance audit log
-- Records every automated evaluation so governors can audit decisions.

CREATE TABLE auxi.governance_log (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  experiment_id uuid NOT NULL REFERENCES auxi.experiments(id),
  verdict     text NOT NULL CHECK (verdict IN ('conclude', 'flag_review', 'continue')),
  winning_variant text,
  factor_verdicts jsonb NOT NULL DEFAULT '[]'::jsonb,
  evaluated_at timestamptz NOT NULL DEFAULT now()
);

-- Index for querying logs per experiment
CREATE INDEX idx_governance_log_experiment
  ON auxi.governance_log (experiment_id, evaluated_at DESC);

-- Append-only: no UPDATE or DELETE via RLS
ALTER TABLE auxi.governance_log ENABLE ROW LEVEL SECURITY;

-- Service role (cron) can insert
CREATE POLICY governance_log_insert_service
  ON auxi.governance_log
  FOR INSERT
  TO service_role
  WITH CHECK (true);

-- Authenticated users (governors) can read
CREATE POLICY governance_log_select_authenticated
  ON auxi.governance_log
  FOR SELECT
  TO authenticated
  USING (true);

-- Service role can also read
CREATE POLICY governance_log_select_service
  ON auxi.governance_log
  FOR SELECT
  TO service_role
  USING (true);

-- Grants for tables/views created after migration 010's bulk GRANT
GRANT SELECT ON auxi.governance_log TO authenticated;
GRANT ALL ON auxi.governance_log TO service_role;
GRANT SELECT ON auxi.v_experiment_summary TO authenticated;
GRANT SELECT ON auxi.v_experiment_summary TO service_role;
