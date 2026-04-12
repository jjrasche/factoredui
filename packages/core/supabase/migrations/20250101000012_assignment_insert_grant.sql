-- Fix: SDK evaluateFlag() inserts assignments client-side.
-- Original grants only allowed service_role. Add INSERT for authenticated users.

CREATE POLICY assignments_insert_own ON auxi.experiment_assignments
  FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());

GRANT INSERT ON auxi.experiment_assignments TO authenticated;
