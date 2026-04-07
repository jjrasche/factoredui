import type { SupabaseClient } from "@supabase/supabase-js";
import type { GovernanceAction, FactorVerdict } from "./governance.js";

export interface GovernanceLogRow {
  id: string;
  experiment_id: string;
  verdict: GovernanceAction;
  winning_variant: string | null;
  factor_verdicts: FactorVerdict[];
  evaluated_at: string;
}

const GOVERNANCE_LOG_COLUMNS =
  "id, experiment_id, verdict, winning_variant, factor_verdicts, evaluated_at";

/**
 * Queries all governance evaluations for a single experiment,
 * ordered by most recent first.
 */
export async function queryGovernanceLog(
  client: SupabaseClient,
  experimentId: string,
): Promise<GovernanceLogRow[]> {
  const { data, error } = await client
    .from("governance_log")
    .select(GOVERNANCE_LOG_COLUMNS)
    .eq("experiment_id", experimentId)
    .order("evaluated_at", { ascending: false });

  if (error) throw new Error(`queryGovernanceLog failed: ${error.message}`);
  return (data ?? []) as GovernanceLogRow[];
}

/**
 * Queries the most recent governance evaluations across all experiments.
 * Defaults to 50 rows.
 */
export async function queryRecentGovernanceLog(
  client: SupabaseClient,
  limit = 50,
): Promise<GovernanceLogRow[]> {
  const { data, error } = await client
    .from("governance_log")
    .select(GOVERNANCE_LOG_COLUMNS)
    .order("evaluated_at", { ascending: false })
    .limit(limit);

  if (error) throw new Error(`queryRecentGovernanceLog failed: ${error.message}`);
  return (data ?? []) as GovernanceLogRow[];
}

/**
 * Queries governance evaluations filtered by verdict type
 * (conclude, flag_review, continue).
 */
export async function queryGovernanceLogByVerdict(
  client: SupabaseClient,
  verdict: GovernanceAction,
): Promise<GovernanceLogRow[]> {
  const { data, error } = await client
    .from("governance_log")
    .select(GOVERNANCE_LOG_COLUMNS)
    .eq("verdict", verdict)
    .order("evaluated_at", { ascending: false });

  if (error) throw new Error(`queryGovernanceLogByVerdict failed: ${error.message}`);
  return (data ?? []) as GovernanceLogRow[];
}
