import type { FactoredStore } from "../store.js";
import type { GovernanceAction, FactorVerdict } from "./governance.js";

export interface GovernanceLogRow {
  id: string;
  experiment_id: string;
  verdict: GovernanceAction;
  winning_variant: string | null;
  factor_verdicts: FactorVerdict[];
  evaluated_at: string;
}

/**
 * Queries all governance evaluations for a single experiment,
 * ordered by most recent first.
 */
export async function queryGovernanceLog(
  store: FactoredStore,
  experimentId: string,
): Promise<GovernanceLogRow[]> {
  return store.queryGovernanceLog(experimentId);
}

/**
 * Queries the most recent governance evaluations across all experiments.
 * Defaults to 50 rows.
 */
export async function queryRecentGovernanceLog(
  store: FactoredStore,
  limit = 50,
): Promise<GovernanceLogRow[]> {
  return store.queryRecentGovernanceLog(limit);
}

/**
 * Queries governance evaluations filtered by verdict type
 * (conclude, flag_review, continue).
 */
export async function queryGovernanceLogByVerdict(
  store: FactoredStore,
  verdict: GovernanceAction,
): Promise<GovernanceLogRow[]> {
  return store.queryGovernanceLogByVerdict(verdict);
}
