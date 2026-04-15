import type { FactoredStore } from "../store.js";

export interface ExperimentSummaryRow {
  experiment_id: string;
  name: string;
  component_path: string;
  status: string;
  winning_variant: string | null;
  created_at: string;
  concluded_at: string | null;
  variant_key: string;
  traffic_percentage: number;
  assigned_users: number;
  exposed_users: number;
}

export interface ExperimentSummaryFilters {
  status?: string;
  component_path?: string;
  created_after?: string;
  created_before?: string;
}

/**
 * Queries all experiment summaries from the governor dashboard view.
 * Each row is one variant of one experiment with assignment/exposure counts.
 */
export async function queryExperimentSummaries(
  store: FactoredStore,
  filters?: ExperimentSummaryFilters,
): Promise<ExperimentSummaryRow[]> {
  return store.queryExperimentSummaries(filters);
}

/**
 * Queries a single experiment's summary rows (one per variant).
 */
export async function queryExperimentSummary(
  store: FactoredStore,
  experimentId: string,
): Promise<ExperimentSummaryRow[]> {
  return store.queryExperimentSummary(experimentId);
}
