import type { SupabaseClient } from "@supabase/supabase-js";
import { queryExperimentResults } from "./results.js";
import type { VariantResult } from "./results.js";
import type { FactorDelta } from "../factors/snapshots.js";

export type ThresholdOperator = "gt" | "lt" | "gte" | "lte" | "eq";

export interface Threshold {
  id: string;
  factor_name: string;
  component_path: string | null;
  operator: ThresholdOperator;
  value: number;
  action: "alert" | "experiment";
}

export interface FactorVerdict {
  factor_name: string;
  best_variant: string;
  best_delta: number;
  control_delta: number;
  is_significant: boolean;
}

export type GovernanceAction = "conclude" | "flag_review" | "continue";

export interface GovernanceVerdict {
  action: GovernanceAction;
  winning_variant: string | null;
  factor_verdicts: FactorVerdict[];
}

/**
 * Orchestrator: evaluates whether an experiment should be concluded
 * based on factor deltas exceeding governance thresholds.
 */
export async function evaluateExperimentThresholds(
  client: SupabaseClient,
  experimentId: string,
  factorNames: string[],
): Promise<GovernanceVerdict> {
  const componentPath = await fetchExperimentComponentPath(client, experimentId);
  if (!componentPath) return buildEmptyVerdict();

  const results = await queryExperimentResults(client, experimentId, factorNames);
  const thresholds = await fetchThresholds(client, factorNames, componentPath);

  return computeGovernanceVerdict(results, thresholds);
}

/**
 * Concludes an experiment: sets status to 'concluded' and records the winner.
 */
export async function concludeExperiment(
  client: SupabaseClient,
  experimentId: string,
  winningVariant: string,
): Promise<void> {
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
}

async function fetchExperimentComponentPath(
  client: SupabaseClient,
  experimentId: string,
): Promise<string | null> {
  const { data, error } = await client
    .from("experiments")
    .select("component_path")
    .eq("id", experimentId)
    .maybeSingle();

  if (error || !data) return null;
  return data.component_path as string;
}

async function fetchThresholds(
  client: SupabaseClient,
  factorNames: string[],
  componentPath: string,
): Promise<Threshold[]> {
  const { data, error } = await client
    .from("thresholds")
    .select("id, factor_name, component_path, operator, value, action")
    .in("factor_name", factorNames)
    .or(`component_path.eq.${componentPath},component_path.is.null`);

  if (error) throw new Error(`fetchThresholds failed: ${error.message}`);
  return (data ?? []) as Threshold[];
}

/**
 * Pure logic: compares variant deltas against thresholds to produce a verdict.
 * SQL mirror: auxi.evaluate_governance() in migration 021 duplicates these rules.
 *
 * Conservative rules:
 * - At least one factor must have a significant delta to conclude
 * - All significant factors must agree on the same winning variant
 * - If any treatment delta is worse than control, flag for review
 * - Otherwise, continue running
 */
export function computeGovernanceVerdict(
  results: VariantResult[],
  thresholds: Threshold[],
): GovernanceVerdict {
  if (results.length < 2 || thresholds.length === 0) return buildEmptyVerdict();

  const controlResult = results.find(r => r.variant_key === "control");
  if (!controlResult) return buildEmptyVerdict();

  const treatmentResults = results.filter(r => r.variant_key !== "control");
  const factorVerdicts = buildFactorVerdicts(controlResult, treatmentResults, thresholds);

  return deriveVerdict(factorVerdicts);
}

function buildFactorVerdicts(
  controlResult: VariantResult,
  treatmentResults: VariantResult[],
  thresholds: Threshold[],
): FactorVerdict[] {
  const verdicts: FactorVerdict[] = [];

  for (const threshold of thresholds) {
    const controlDelta = findFactorDelta(controlResult.factor_deltas, threshold.factor_name);
    const bestTreatment = findBestTreatment(treatmentResults, threshold);

    if (!bestTreatment || controlDelta === null) continue;

    const improvement = Math.abs(bestTreatment.delta) - Math.abs(controlDelta);
    const isSignificant = isThresholdExceeded(improvement, threshold.operator, threshold.value);

    verdicts.push({
      factor_name: threshold.factor_name,
      best_variant: bestTreatment.variantKey,
      best_delta: bestTreatment.delta,
      control_delta: controlDelta,
      is_significant: isSignificant,
    });
  }

  return verdicts;
}

interface TreatmentCandidate {
  variantKey: string;
  delta: number;
}

function findBestTreatment(
  treatmentResults: VariantResult[],
  threshold: Threshold,
): TreatmentCandidate | null {
  let best: TreatmentCandidate | null = null;

  for (const result of treatmentResults) {
    const delta = findFactorDelta(result.factor_deltas, threshold.factor_name);
    if (delta === null) continue;

    if (!best || Math.abs(delta) > Math.abs(best.delta)) {
      best = { variantKey: result.variant_key, delta };
    }
  }

  return best;
}

function findFactorDelta(deltas: FactorDelta[], factorName: string): number | null {
  const match = deltas.find(d => d.factor_name === factorName);
  return match?.delta ?? null;
}

export function isThresholdExceeded(
  value: number,
  operator: ThresholdOperator,
  threshold: number,
): boolean {
  switch (operator) {
    case "gt": return value > threshold;
    case "lt": return value < threshold;
    case "gte": return value >= threshold;
    case "lte": return value <= threshold;
    case "eq": return value === threshold;
  }
}

function deriveVerdict(factorVerdicts: FactorVerdict[]): GovernanceVerdict {
  if (factorVerdicts.length === 0) return buildEmptyVerdict();

  const hasWorseningTreatment = factorVerdicts.some(
    v => Math.abs(v.best_delta) < Math.abs(v.control_delta),
  );
  if (hasWorseningTreatment) {
    return { action: "flag_review", winning_variant: null, factor_verdicts: factorVerdicts };
  }

  const significantVerdicts = factorVerdicts.filter(v => v.is_significant);
  if (significantVerdicts.length === 0) {
    return { action: "continue", winning_variant: null, factor_verdicts: factorVerdicts };
  }

  const winningVariants = new Set(significantVerdicts.map(v => v.best_variant));
  if (winningVariants.size !== 1) {
    return { action: "flag_review", winning_variant: null, factor_verdicts: factorVerdicts };
  }

  const winningVariant = [...winningVariants][0];
  return { action: "conclude", winning_variant: winningVariant, factor_verdicts: factorVerdicts };
}

function buildEmptyVerdict(): GovernanceVerdict {
  return { action: "continue", winning_variant: null, factor_verdicts: [] };
}
