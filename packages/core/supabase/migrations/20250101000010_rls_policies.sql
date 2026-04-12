-- auxi schema: RLS policies + grants
-- Three audiences: individual users (own data), AI/service_role (all), governors (aggregated views)
-- service_role bypasses RLS automatically in Supabase.

-- Sessions: user reads/writes/updates own
CREATE POLICY sessions_select_own ON auxi.sessions
  FOR SELECT TO authenticated USING (user_id = auth.uid());
CREATE POLICY sessions_insert_own ON auxi.sessions
  FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());
CREATE POLICY sessions_update_own ON auxi.sessions
  FOR UPDATE TO authenticated
  USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());

-- Events: user reads own, inserts own (immutable: no update/delete)
CREATE POLICY events_select_own ON auxi.events
  FOR SELECT TO authenticated USING (user_id = auth.uid());
CREATE POLICY events_insert_own ON auxi.events
  FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());

-- Factors: user reads own (server writes via pg_cron)
CREATE POLICY factors_select_own ON auxi.factors
  FOR SELECT TO authenticated USING (user_id = auth.uid());

-- Factor snapshots: user reads own (server writes via pg_cron)
CREATE POLICY factor_snapshots_select_own ON auxi.factor_snapshots
  FOR SELECT TO authenticated USING (user_id = auth.uid());

-- Vectors: user reads own
CREATE POLICY vectors_select_own ON auxi.user_factor_vectors
  FOR SELECT TO authenticated USING (user_id = auth.uid());

-- Clusters: user reads own
CREATE POLICY clusters_select_own ON auxi.user_clusters
  FOR SELECT TO authenticated USING (user_id = auth.uid());

-- Experiments: all authenticated can read (SDK needs definitions for flag evaluation)
CREATE POLICY experiments_select_all ON auxi.experiments
  FOR SELECT TO authenticated USING (true);

-- Experiment variants: all can read (SDK needs variant configs)
CREATE POLICY variants_select_all ON auxi.experiment_variants
  FOR SELECT TO authenticated USING (true);

-- Assignments: user reads own (server writes during bucketing)
CREATE POLICY assignments_select_own ON auxi.experiment_assignments
  FOR SELECT TO authenticated USING (user_id = auth.uid());

-- Exposures: user reads own, inserts own (SDK logs when variant rendered)
CREATE POLICY exposures_select_own ON auxi.experiment_exposures
  FOR SELECT TO authenticated USING (user_id = auth.uid());
CREATE POLICY exposures_insert_own ON auxi.experiment_exposures
  FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());

-- Variant configs: all can read (needed to render SDUI variants)
CREATE POLICY configs_select_all ON auxi.variant_configs
  FOR SELECT TO authenticated USING (true);

-- Thresholds: all can read (governance is transparent)
CREATE POLICY thresholds_select_all ON auxi.thresholds
  FOR SELECT TO authenticated USING (true);


-- Grants
GRANT SELECT ON ALL TABLES IN SCHEMA auxi TO authenticated;
GRANT INSERT ON auxi.events, auxi.sessions, auxi.experiment_exposures TO authenticated;
GRANT UPDATE ON auxi.sessions TO authenticated;

GRANT ALL ON ALL TABLES IN SCHEMA auxi TO service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA auxi TO service_role;
