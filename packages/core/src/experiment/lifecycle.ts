import type { FactoredStore } from "../store.js";
import type { Platform } from "../types.js";
import type { TargetingRule } from "./targeting.js";

export interface VariantDefinition {
  variant_key: string;
  config: Record<string, unknown>;
  traffic_percentage: number;
}

export interface ExperimentDefinition {
  name: string;
  description?: string;
  component_path: string;
  variants: VariantDefinition[];
  targeting_rules?: TargetingRule[];
  platforms?: Platform[];
}

export interface CreatedExperiment {
  id: string;
  name: string;
  status: string;
  component_path: string;
}

/**
 * Creates a new experiment in draft status with its variants.
 * Validates traffic percentages sum to 100 and requires a control variant.
 */
export async function createExperiment(
  store: FactoredStore,
  definition: ExperimentDefinition,
): Promise<CreatedExperiment> {
  validateDefinition(definition);

  const experiment = await store.insertExperiment({
    name: definition.name,
    description: definition.description ?? null,
    component_path: definition.component_path,
    targeting_rules: definition.targeting_rules ?? [],
    platforms: definition.platforms ?? [],
  });
  await store.insertVariants(experiment.id, definition.variants);

  return experiment;
}

/**
 * Transitions an experiment from draft to running.
 * Fails if the experiment is not in draft status.
 */
export async function startExperiment(
  store: FactoredStore,
  experimentId: string,
): Promise<void> {
  await store.startExperiment(experimentId);
}

function validateDefinition(definition: ExperimentDefinition): void {
  if (definition.variants.length < 2) {
    throw new Error("Experiment requires at least 2 variants");
  }

  const hasControl = definition.variants.some(v => v.variant_key === "control");
  if (!hasControl) {
    throw new Error("Experiment requires a 'control' variant");
  }

  const totalTraffic = definition.variants.reduce((sum, v) => sum + v.traffic_percentage, 0);
  if (totalTraffic !== 100) {
    throw new Error(`Traffic percentages must sum to 100, got ${totalTraffic}`);
  }
}
