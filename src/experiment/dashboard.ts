import type { SupabaseClient } from "@supabase/supabase-js";

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
  client: SupabaseClient,
  filters?: ExperimentSummaryFilters,
): Promise<ExperimentSummaryRow[]> {
  let query = client
    .from("v_experiment_summary")
    .select("*");

  query = applyFilters(query, filters);

  const { data, error } = await query;
  if (error) throw new Error(`queryExperimentSummaries failed: ${error.message}`);
  return (data ?? []) as ExperimentSummaryRow[];
}

/**
 * Queries a single experiment's summary rows (one per variant).
 */
export async function queryExperimentSummary(
  client: SupabaseClient,
  experimentId: string,
): Promise<ExperimentSummaryRow[]> {
  const { data, error } = await client
    .from("v_experiment_summary")
    .select("*")
    .eq("experiment_id", experimentId);

  if (error) throw new Error(`queryExperimentSummary failed: ${error.message}`);
  return (data ?? []) as ExperimentSummaryRow[];
}

type SupabaseQuery = ReturnType<ReturnType<SupabaseClient["from"]>["select"]>;

function applyFilters(
  query: SupabaseQuery,
  filters?: ExperimentSummaryFilters,
): SupabaseQuery {
  if (!filters) return query;

  if (filters.status) {
    query = query.eq("status", filters.status);
  }
  if (filters.component_path) {
    query = query.eq("component_path", filters.component_path);
  }
  if (filters.created_after) {
    query = query.gte("created_at", filters.created_after);
  }
  if (filters.created_before) {
    query = query.lte("created_at", filters.created_before);
  }

  return query;
}
