import type { SupabaseClient } from "@supabase/supabase-js";
import { evaluateExperimentThresholds, concludeExperiment } from "./governance.js";
import type { GovernanceVerdict } from "./governance.js";

/**
 * Logs a governance verdict to the append-only audit log.
 */
export async function logGovernanceVerdict(
  client: SupabaseClient,
  experimentId: string,
  verdict: GovernanceVerdict,
): Promise<void> {
  const { error } = await client
    .from("governance_log")
    .insert({
      experiment_id: experimentId,
      verdict: verdict.action,
      winning_variant: verdict.winning_variant,
      factor_verdicts: verdict.factor_verdicts,
    });

  if (error) throw new Error(`logGovernanceVerdict failed: ${error.message}`);
}

/**
 * Orchestrator: evaluates governance thresholds, logs the verdict,
 * and auto-concludes if there's a clear winner.
 * Mirrors SQL cron evaluate_governance() but callable from TypeScript.
 */
export async function runGovernanceCheck(
  client: SupabaseClient,
  experimentId: string,
  factorNames: string[],
): Promise<GovernanceVerdict> {
  const verdict = await evaluateExperimentThresholds(client, experimentId, factorNames);
  await logGovernanceVerdict(client, experimentId, verdict);
  await concludeIfWinner(client, experimentId, verdict);

  return verdict;
}

async function concludeIfWinner(
  client: SupabaseClient,
  experimentId: string,
  verdict: GovernanceVerdict,
): Promise<void> {
  if (verdict.action === "conclude" && verdict.winning_variant) {
    await concludeExperiment(client, experimentId, verdict.winning_variant);
  }
}
