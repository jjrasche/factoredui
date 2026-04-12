-- Fix: fetchAssignment uses PostgREST embedded select on experiment_variants,
-- which requires a FK relationship to resolve the join.

ALTER TABLE auxi.experiment_assignments
  ADD CONSTRAINT fk_assignments_variant
  FOREIGN KEY (experiment_id, variant_key)
  REFERENCES auxi.experiment_variants (experiment_id, variant_key);
