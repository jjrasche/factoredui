import type { SupabaseClient } from "@supabase/supabase-js";
import { queryFactorDelta } from "../factors/snapshots.js";
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

  const deltaAccumulator = new Map<string, { sum: number; beforeSum: number; afterSum: number; count: number }>();

  for (const userId of userIds) {
    for (const factorName of factorNames) {
      const delta = await queryFactorDelta(
        client, userId, componentPath, factorName, beforeDate, afterDate,
      );
      if (!delta) continue;

      const existing = deltaAccumulator.get(factorName) ?? { sum: 0, beforeSum: 0, afterSum: 0, count: 0 };
      existing.sum += delta.delta;
      existing.beforeSum += delta.before;
      existing.afterSum += delta.after;
      existing.count += 1;
      deltaAccumulator.set(factorName, existing);
    }
  }

  const averagedDeltas: FactorDelta[] = [];
  for (const [factorName, acc] of deltaAccumulator) {
    if (acc.count === 0) continue;
    averagedDeltas.push({
      factor_name: factorName,
      before: acc.beforeSum / acc.count,
      after: acc.afterSum / acc.count,
      delta: acc.sum / acc.count,
    });
  }

  return averagedDeltas;
}
