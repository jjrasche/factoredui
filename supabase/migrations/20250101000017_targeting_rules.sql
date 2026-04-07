-- Add targeting_rules to experiments for factor-based audience selection.
-- Rules are evaluated client-side from cached factor values.
-- Schema: [{ "factor": "error_rate", "operator": "gt", "threshold": 0.1 }]

ALTER TABLE auxi.experiments
  ADD COLUMN targeting_rules jsonb NOT NULL DEFAULT '[]'::jsonb;

COMMENT ON COLUMN auxi.experiments.targeting_rules IS
  'JSON array of factor-based targeting rules evaluated client-side';
