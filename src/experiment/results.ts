import type { SupabaseClient } from "@supabase/supabase-js";
import type { FactorDelta } from "../factors/snapshots.js";

export interface VariantResult {
  variant_key: string;
  user_count: number;
  factor_deltas: FactorDelta[];
}

export async function queryExperimentResults(
  client: SupabaseClient,
  experimentId: string,
  factorNames: string[],
): Promise<VariantResult[]> {
  const experiment = await fetchExperiment(client, experimentId);
  if (!experiment) return [];

  const variantGroups = await fetchAssignmentsByVariant(client, experimentId);

  const results: VariantResult[] = [];
  for (const [variantKey, userIds] of variantGroups) {
    const factorDeltas = await computeVariantDeltas(
      client, userIds, experiment.component_path, factorNames, experiment.created_at,
    );
    results.push({
      variant_key: variantKey,
      user_count: userIds.length,
      factor_deltas: factorDeltas,
    });
  }

  return results;
}

async function fetchExperiment(
  client: SupabaseClient,
  experimentId: string,
): Promise<{ component_path: string; created_at: string } | null> {
  const { data, error } = await client
    .from("experiments")
    .select("component_path, created_at")
    .eq("id", experimentId)
    .maybeSingle();

  if (error || !data) return null;
  return data as { component_path: string; created_at: string };
}

async function fetchAssignmentsByVariant(
  client: SupabaseClient,
  experimentId: string,
): Promise<Map<string, string[]>> {
  const { data, error } = await client
    .from("experiment_assignments")
    .select("variant_key, user_id")
    .eq("experiment_id", experimentId);

  if (error || !data) return new Map();

  const groups = new Map<string, string[]>();
  for (const row of data) {
    const existing = groups.get(row.variant_key) ?? [];
    existing.push(row.user_id);
    groups.set(row.variant_key, existing);
  }
  return groups;
}

async function computeVariantDeltas(
  client: SupabaseClient,
  userIds: string[],
  componentPath: string,
  factorNames: string[],
  experimentCreatedAt: string,
): Promise<FactorDelta[]> {
  const beforeDate = new Date(experimentCreatedAt);
  const afterDate = new Date();

  const { data, error } = await client.rpc("bulk_factor_deltas", {
    p_user_ids: userIds,
    p_component: componentPath,
    p_factor_names: factorNames,
    p_before: beforeDate.toISOString(),
    p_after: afterDate.toISOString(),
  });

  if (error) throw new Error(`bulk_factor_deltas failed: ${error.message}`);
  if (!data) return [];

  return (data as BulkDeltaRow[]).map(row => ({
    factor_name: row.factor_name,
    before: row.avg_before,
    after: row.avg_after,
    delta: row.avg_delta,
  }));
}

interface BulkDeltaRow {
  factor_name: string;
  avg_before: number;
  avg_after: number;
  avg_delta: number;
}
