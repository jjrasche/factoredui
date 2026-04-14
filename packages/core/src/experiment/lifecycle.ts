import type { SupabaseClient } from "@supabase/supabase-js";
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
  client: SupabaseClient,
  definition: ExperimentDefinition,
): Promise<CreatedExperiment> {
  validateDefinition(definition);

  const experiment = await insertExperiment(client, definition);
  await insertVariants(client, experiment.id, definition.variants);

  return experiment;
}

/**
 * Transitions an experiment from draft to running.
 * Fails if the experiment is not in draft status.
 */
export async function startExperiment(
  client: SupabaseClient,
  experimentId: string,
): Promise<void> {
  const { data, error } = await client
    .from("experiments")
    .update({ status: "running" })
    .eq("id", experimentId)
    .eq("status", "draft")
    .select("id");

  if (error) throw new Error(`startExperiment failed: ${error.message}`);
  if (!data || data.length === 0) {
    throw new Error(`startExperiment: experiment ${experimentId} not found or not in draft status`);
  }
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

async function insertExperiment(
  client: SupabaseClient,
  definition: ExperimentDefinition,
): Promise<CreatedExperiment> {
  const { data, error } = await client
    .from("experiments")
    .insert({
      name: definition.name,
      description: definition.description ?? null,
      component_path: definition.component_path,
      targeting_rules: definition.targeting_rules ?? [],
      platforms: definition.platforms ?? [],
    })
    .select("id, name, status, component_path")
    .single();

  if (error) throw new Error(`insertExperiment failed: ${error.message}`);
  return data as CreatedExperiment;
}

async function insertVariants(
  client: SupabaseClient,
  experimentId: string,
  variants: VariantDefinition[],
): Promise<void> {
  const rows = variants.map(v => ({
    experiment_id: experimentId,
    variant_key: v.variant_key,
    config: v.config,
    traffic_percentage: v.traffic_percentage,
  }));

  const { error } = await client
    .from("experiment_variants")
    .insert(rows);

  if (error) throw new Error(`insertVariants failed: ${error.message}`);
}
