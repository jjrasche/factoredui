import type { FactoredStore } from "../store.js";
import { evaluateExperimentThresholds, concludeExperiment } from "./governance.js";
import type { GovernanceVerdict } from "./governance.js";

/**
 * Logs a governance verdict to the append-only audit log.
 */
export async function logGovernanceVerdict(
  store: FactoredStore,
  experimentId: string,
  verdict: GovernanceVerdict,
): Promise<void> {
  await store.insertGovernanceVerdict(
    experimentId,
    verdict.action,
    verdict.winning_variant,
    verdict.factor_verdicts,
  );
}

/**
 * Orchestrator: evaluates governance thresholds, logs the verdict,
 * and auto-concludes if there's a clear winner.
 * Mirrors SQL cron evaluate_governance() but callable from TypeScript.
 */
export async function runGovernanceCheck(
  store: FactoredStore,
  experimentId: string,
  factorNames: string[],
): Promise<GovernanceVerdict> {
  const verdict = await evaluateExperimentThresholds(store, experimentId, factorNames);
  await logGovernanceVerdict(store, experimentId, verdict);
  await concludeIfWinner(store, experimentId, verdict);

  return verdict;
}

async function concludeIfWinner(
  store: FactoredStore,
  experimentId: string,
  verdict: GovernanceVerdict,
): Promise<void> {
  if (verdict.action === "conclude" && verdict.winning_variant) {
    await concludeExperiment(store, experimentId, verdict.winning_variant);
  }
}
