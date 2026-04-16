import type { SupabaseClient } from "@supabase/supabase-js";
import type {
  FactoredStore,
  EventRow,
  RunningExperiment,
  VariantWithTraffic,
  ExperimentMeta,
  ExperimentInsertRow,
  BulkDeltaRow,
  Unsubscribe,
  Factor,
  ExperimentAssignment,
  ComponentFactorAggregate,
  FactorSnapshot,
  UserCluster,
  Threshold,
  GovernanceAction,
  FactorVerdict,
  GovernanceLogRow,
  ExperimentSummaryRow,
  ExperimentSummaryFilters,
  CreatedExperiment,
  VariantDefinition,
  SignedSpec,
  Spec,
} from "@factoredui/core";

export function createSupabaseStore(client: SupabaseClient<any, any, any>): FactoredStore {
  return {
    // --- Auth ---

    async getCurrentUserId(): Promise<string | null> {
      const { data: { user } } = await client.auth.getUser();
      return user?.id ?? null;
    },

    // --- Capture ---

    async insertEvents(events: EventRow[]): Promise<void> {
      const { error } = await client.from("events").insert(events);
      if (error) throw new Error(`insertEvents failed: ${error.message}`);
    },

    async insertSession(userId: string, metadata: Record<string, unknown>): Promise<{ id: string }> {
      const { data, error } = await client
        .from("sessions")
        .insert({ user_id: userId, metadata })
        .select("id")
        .single();

      if (error) throw new Error(`insertSession failed: ${error.message}`);
      return { id: (data as { id: string }).id };
    },

    async endSession(sessionId: string): Promise<void> {
      await client
        .from("sessions")
        .update({ ended_at: new Date().toISOString() })
        .eq("id", sessionId);
    },

    // --- Factors ---

    async queryFactors(userId: string, componentPath?: string): Promise<Factor[]> {
      let query = client
        .from("v_factors_current")
        .select("user_id, component_path, factor_name, factor_tier, value, computed_at")
        .eq("user_id", userId);

      if (componentPath) {
        query = query.eq("component_path", componentPath);
      }

      const { data, error } = await query;
      if (error) throw new Error(`queryFactors failed: ${error.message}`);
      return data as Factor[];
    },

    async queryComponentFactors(componentPath: string): Promise<ComponentFactorAggregate[]> {
      const { data, error } = await client
        .from("v_component_factors_agg")
        .select(
          "component_path, factor_name, factor_tier, user_count, avg_value, median_value, p95_value, min_value, max_value, stddev_value",
        )
        .eq("component_path", componentPath);

      if (error) throw new Error(`queryComponentFactors failed: ${error.message}`);
      return data as ComponentFactorAggregate[];
    },

    async queryFactorHistory(
      userId: string,
      componentPath: string,
      factorName: string,
      since: Date,
    ): Promise<FactorSnapshot[]> {
      const { data, error } = await client
        .from("factor_snapshots")
        .select("factor_name, factor_tier, value, snapshot_at")
        .eq("user_id", userId)
        .eq("component_path", componentPath)
        .eq("factor_name", factorName)
        .gte("snapshot_at", since.toISOString())
        .order("snapshot_at", { ascending: true });

      if (error) throw new Error(`queryFactorHistory failed: ${error.message}`);
      return data as FactorSnapshot[];
    },

    async findClosestSnapshot(
      userId: string,
      componentPath: string,
      factorName: string,
      targetDate: Date,
    ): Promise<FactorSnapshot | null> {
      const { data, error } = await client
        .from("factor_snapshots")
        .select("factor_name, factor_tier, value, snapshot_at")
        .eq("user_id", userId)
        .eq("component_path", componentPath)
        .eq("factor_name", factorName)
        .lte("snapshot_at", targetDate.toISOString())
        .order("snapshot_at", { ascending: false })
        .limit(1)
        .maybeSingle();

      if (error) throw new Error(`findClosestSnapshot failed: ${error.message}`);
      return data as FactorSnapshot | null;
    },

    // --- Clustering ---

    async queryUserCluster(userId: string): Promise<UserCluster | null> {
      const { data, error } = await client
        .from("user_clusters")
        .select("user_id, cluster_id, assigned_at")
        .eq("user_id", userId)
        .maybeSingle();

      if (error) throw new Error(`queryUserCluster failed: ${error.message}`);
      return data as UserCluster | null;
    },

    async queryClusterMembers(clusterId: number): Promise<UserCluster[]> {
      const { data, error } = await client
        .from("user_clusters")
        .select("user_id, cluster_id, assigned_at")
        .eq("cluster_id", clusterId);

      if (error) throw new Error(`queryClusterMembers failed: ${error.message}`);
      return (data ?? []) as UserCluster[];
    },

    // --- Experiments: flag evaluation ---

    async getAssignment(userId: string, experimentName: string): Promise<ExperimentAssignment | null> {
      const { data, error } = await client
        .from("experiment_assignments")
        .select(`
          experiment_id,
          variant_key,
          experiments!inner ( name, status ),
          experiment_variants!inner ( config )
        `)
        .eq("user_id", userId)
        .eq("experiments.name", experimentName)
        .eq("experiments.status", "running")
        .maybeSingle();

      if (error || !data) return null;

      const row = data as unknown as {
        experiment_id: string;
        variant_key: string;
        experiment_variants: { config: Record<string, unknown> } | null;
      };

      return {
        experiment_id: row.experiment_id,
        variant_key: row.variant_key,
        config: row.experiment_variants?.config ?? {},
      };
    },

    async getRunningExperiment(experimentName: string): Promise<RunningExperiment | null> {
      const { data, error } = await client
        .from("experiments")
        .select("id, name, component_path, targeting_rules, platforms")
        .eq("name", experimentName)
        .eq("status", "running")
        .maybeSingle();

      if (error || !data) return null;
      return data as RunningExperiment;
    },

    async hasConflictingAssignment(
      userId: string,
      componentPath: string,
      excludeExperimentId: string,
    ): Promise<boolean> {
      const { data, error } = await client
        .from("experiment_assignments")
        .select("experiment_id, experiments!inner ( id, status, component_path )")
        .eq("user_id", userId)
        .eq("experiments.status", "running")
        .eq("experiments.component_path", componentPath)
        .neq("experiment_id", excludeExperimentId)
        .limit(1);

      if (error) return false;
      return (data?.length ?? 0) > 0;
    },

    async getVariants(experimentId: string): Promise<VariantWithTraffic[]> {
      const { data, error } = await client
        .from("experiment_variants")
        .select("variant_key, config, traffic_percentage")
        .eq("experiment_id", experimentId)
        .order("variant_key");

      if (error || !data) return [];
      return data as VariantWithTraffic[];
    },

    async writeAssignment(userId: string, experimentId: string, variantKey: string): Promise<void> {
      const { error } = await client
        .from("experiment_assignments")
        .insert({ user_id: userId, experiment_id: experimentId, variant_key: variantKey });

      if (error) throw new Error(`writeAssignment failed: ${error.message}`);
    },

    async recordExposure(userId: string, experimentId: string, variantKey: string): Promise<void> {
      await client.from("experiment_exposures").insert({
        user_id: userId,
        experiment_id: experimentId,
        variant_key: variantKey,
      });
    },

    // --- Experiments: lifecycle ---

    async insertExperiment(row: ExperimentInsertRow): Promise<CreatedExperiment> {
      const { data, error } = await client
        .from("experiments")
        .insert(row)
        .select("id, name, status, component_path")
        .single();

      if (error) throw new Error(`insertExperiment failed: ${error.message}`);
      return data as CreatedExperiment;
    },

    async insertVariants(experimentId: string, variants: VariantDefinition[]): Promise<void> {
      const rows = variants.map(v => ({
        experiment_id: experimentId,
        variant_key: v.variant_key,
        config: v.config,
        traffic_percentage: v.traffic_percentage,
      }));

      const { error } = await client
        .from("experiment_variants")
        .insert(rows);

      if (error) throw new Error(`insertVariants failed: ${error.message}`);
    },

    async startExperiment(experimentId: string): Promise<void> {
      const { data, error } = await client
        .from("experiments")
        .update({ status: "running" })
        .eq("id", experimentId)
        .eq("status", "draft")
        .select("id");

      if (error) throw new Error(`startExperiment failed: ${error.message}`);
      if (!data || data.length === 0) {
        throw new Error(`startExperiment: experiment ${experimentId} not found or not in draft status`);
      }
    },

    // --- Experiments: governance ---

    async getExperimentMeta(experimentId: string): Promise<ExperimentMeta | null> {
      const { data, error } = await client
        .from("experiments")
        .select("component_path, created_at")
        .eq("id", experimentId)
        .maybeSingle();

      if (error || !data) return null;
      return data as ExperimentMeta;
    },

    async queryThresholds(factorNames: string[], componentPath: string): Promise<Threshold[]> {
      const { data, error } = await client
        .from("thresholds")
        .select("id, factor_name, component_path, operator, value, action")
        .in("factor_name", factorNames)
        .or(`component_path.eq.${componentPath},component_path.is.null`);

      if (error) throw new Error(`queryThresholds failed: ${error.message}`);
      return (data ?? []) as Threshold[];
    },

    async concludeExperiment(experimentId: string, winningVariant: string): Promise<void> {
      const { error } = await client
        .from("experiments")
        .update({
          status: "concluded",
          concluded_at: new Date().toISOString(),
          winning_variant: winningVariant,
        })
        .eq("id", experimentId)
        .eq("status", "running");

      if (error) throw new Error(`concludeExperiment failed: ${error.message}`);
    },

    async insertGovernanceVerdict(
      experimentId: string,
      verdict: GovernanceAction,
      winningVariant: string | null,
      factorVerdicts: FactorVerdict[],
    ): Promise<void> {
      const { error } = await client
        .from("governance_log")
        .insert({
          experiment_id: experimentId,
          verdict,
          winning_variant: winningVariant,
          factor_verdicts: factorVerdicts,
        });

      if (error) throw new Error(`insertGovernanceVerdict failed: ${error.message}`);
    },

    // --- Experiments: governance log ---

    async queryGovernanceLog(experimentId: string): Promise<GovernanceLogRow[]> {
      const { data, error } = await client
        .from("governance_log")
        .select("id, experiment_id, verdict, winning_variant, factor_verdicts, evaluated_at")
        .eq("experiment_id", experimentId)
        .order("evaluated_at", { ascending: false });

      if (error) throw new Error(`queryGovernanceLog failed: ${error.message}`);
      return (data ?? []) as GovernanceLogRow[];
    },

    async queryRecentGovernanceLog(limit: number): Promise<GovernanceLogRow[]> {
      const { data, error } = await client
        .from("governance_log")
        .select("id, experiment_id, verdict, winning_variant, factor_verdicts, evaluated_at")
        .order("evaluated_at", { ascending: false })
        .limit(limit);

      if (error) throw new Error(`queryRecentGovernanceLog failed: ${error.message}`);
      return (data ?? []) as GovernanceLogRow[];
    },

    async queryGovernanceLogByVerdict(verdict: GovernanceAction): Promise<GovernanceLogRow[]> {
      const { data, error } = await client
        .from("governance_log")
        .select("id, experiment_id, verdict, winning_variant, factor_verdicts, evaluated_at")
        .eq("verdict", verdict)
        .order("evaluated_at", { ascending: false });

      if (error) throw new Error(`queryGovernanceLogByVerdict failed: ${error.message}`);
      return (data ?? []) as GovernanceLogRow[];
    },

    // --- Experiments: results ---

    async getAssignmentsByVariant(experimentId: string): Promise<Map<string, string[]>> {
      const { data, error } = await client
        .from("experiment_assignments")
        .select("variant_key, user_id")
        .eq("experiment_id", experimentId);

      if (error || !data) return new Map();

      const groups = new Map<string, string[]>();
      for (const row of data as { variant_key: string; user_id: string }[]) {
        const existing = groups.get(row.variant_key) ?? [];
        existing.push(row.user_id);
        groups.set(row.variant_key, existing);
      }
      return groups;
    },

    async bulkFactorDeltas(
      userIds: string[],
      componentPath: string,
      factorNames: string[],
      before: string,
      after: string,
    ): Promise<BulkDeltaRow[]> {
      const { data, error } = await client.rpc("bulk_factor_deltas", {
        p_user_ids: userIds,
        p_component: componentPath,
        p_factor_names: factorNames,
        p_before: before,
        p_after: after,
      });

      if (error) throw new Error(`bulkFactorDeltas failed: ${error.message}`);
      return (data ?? []) as BulkDeltaRow[];
    },

    // --- Dashboard ---

    async queryExperimentSummaries(filters?: ExperimentSummaryFilters): Promise<ExperimentSummaryRow[]> {
      let query = client.from("v_experiment_summary").select("*");

      if (filters?.status) query = query.eq("status", filters.status);
      if (filters?.component_path) query = query.eq("component_path", filters.component_path);
      if (filters?.created_after) query = query.gte("created_at", filters.created_after);
      if (filters?.created_before) query = query.lte("created_at", filters.created_before);

      const { data, error } = await query;
      if (error) throw new Error(`queryExperimentSummaries failed: ${error.message}`);
      return (data ?? []) as ExperimentSummaryRow[];
    },

    async queryExperimentSummary(experimentId: string): Promise<ExperimentSummaryRow[]> {
      const { data, error } = await client
        .from("v_experiment_summary")
        .select("*")
        .eq("experiment_id", experimentId);

      if (error) throw new Error(`queryExperimentSummary failed: ${error.message}`);
      return (data ?? []) as ExperimentSummaryRow[];
    },

    // --- SDUI ---

    async loadActiveSpec(platform: string): Promise<SignedSpec | null> {
      const { data, error } = await client
        .from("ui_active")
        .select("spec_id, ui_specs(*)")
        .eq("platform", platform)
        .single();

      if (error || !data) return null;

      return mapRowToSignedSpec(data);
    },

    // --- Realtime ---

    subscribe(
      channel: string,
      table: string,
      filter: string | null,
      onInsert: (row: unknown) => void,
    ): Unsubscribe {
      const realtimeChannel = client
        .channel(channel)
        .on(
          "postgres_changes",
          {
            event: "INSERT",
            schema: "factoredui",
            table,
            ...(filter ? { filter } : {}),
          },
          (payload: { new: unknown }) => onInsert(payload.new),
        )
        .subscribe();

      return () => {
        client.removeChannel(realtimeChannel);
      };
    },
  };
}

function mapRowToSignedSpec(data: unknown): SignedSpec {
  const row = data as {
    spec_id: string;
    ui_specs: {
      component_tree: unknown;
      spec_version: number;
      renderer_min: number;
      spec_hash: string;
      signature: string;
    };
  };

  return {
    spec: {
      spec_version: row.ui_specs.spec_version,
      renderer_min: row.ui_specs.renderer_min,
      root: row.ui_specs.component_tree as Spec["root"],
    },
    signature: row.ui_specs.signature,
    signed_at: "",
    spec_hash: row.ui_specs.spec_hash,
  };
}
