import type { FactoredStore, BulkDeltaRow } from "../store.js";
import type { FactorDelta } from "../factors/snapshots.js";

export interface VariantResult {
  variant_key: string;
  user_count: number;
  factor_deltas: FactorDelta[];
}

export async function queryExperimentResults(
  store: FactoredStore,
  experimentId: string,
  factorNames: string[],
): Promise<VariantResult[]> {
  const meta = await store.getExperimentMeta(experimentId);
  if (!meta) return [];

  const variantGroups = await store.getAssignmentsByVariant(experimentId);

  const results: VariantResult[] = [];
  for (const [variantKey, userIds] of variantGroups) {
    const factorDeltas = await computeVariantDeltas(
      store, userIds, meta.component_path, factorNames, meta.created_at,
    );
    results.push({
      variant_key: variantKey,
      user_count: userIds.length,
      factor_deltas: factorDeltas,
    });
  }

  return results;
}

async function computeVariantDeltas(
  store: FactoredStore,
  userIds: string[],
  componentPath: string,
  factorNames: string[],
  experimentCreatedAt: string,
): Promise<FactorDelta[]> {
  const beforeDate = new Date(experimentCreatedAt);
  const afterDate = new Date();

  const rows = await store.bulkFactorDeltas(
    userIds,
    componentPath,
    factorNames,
    beforeDate.toISOString(),
    afterDate.toISOString(),
  );

  return rows.map((row: BulkDeltaRow) => ({
    factor_name: row.factor_name,
    before: row.avg_before,
    after: row.avg_after,
    delta: row.avg_delta,
  }));
}
