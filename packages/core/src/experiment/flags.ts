import type { FactoredStore, RunningExperiment, VariantWithTraffic } from "../store.js";
import type { ExperimentAssignment, Platform } from "../types.js";
import { queryFactors } from "../factors/query.js";
import { evaluateTargeting } from "./targeting.js";
import type { DeviceMetadata } from "./targeting.js";

/**
 * Flag evaluation: reads experiment assignments for the current user.
 * If not yet assigned to a running experiment, assigns deterministically via hash bucketing.
 * Optional platform parameter filters experiments to those targeting the current platform.
 */

export async function evaluateFlag(
  store: FactoredStore,
  experimentName: string,
  platform?: Platform,
  deviceMetadata?: DeviceMetadata,
): Promise<ExperimentAssignment | null> {
  const userId = await store.getCurrentUserId();
  if (!userId) return null;

  const existingAssignment = await store.getAssignment(userId, experimentName);
  if (existingAssignment) {
    await store.recordExposure(userId, existingAssignment.experiment_id, existingAssignment.variant_key);
    return existingAssignment;
  }

  const newAssignment = await assignToExperiment(store, userId, experimentName, platform, deviceMetadata);
  if (newAssignment) {
    await store.recordExposure(userId, newAssignment.experiment_id, newAssignment.variant_key);
  }
  return newAssignment;
}

async function assignToExperiment(
  store: FactoredStore,
  userId: string,
  experimentName: string,
  platform?: Platform,
  deviceMetadata?: DeviceMetadata,
): Promise<ExperimentAssignment | null> {
  const experiment = await store.getRunningExperiment(experimentName);
  if (!experiment) return null;

  if (platform && experiment.platforms.length > 0 && !experiment.platforms.includes(platform)) {
    return null;
  }

  const hasConflict = await store.hasConflictingAssignment(
    userId, experiment.component_path, experiment.id,
  );
  if (hasConflict) return null;

  const isTargeted = await checkTargeting(store, userId, experiment, deviceMetadata);
  if (!isTargeted) return null;

  const variants = await store.getVariants(experiment.id);
  if (variants.length === 0) return null;

  const selectedVariant = selectVariantByHash(userId, experiment.id, variants);
  if (!selectedVariant) return null;

  try {
    await store.writeAssignment(userId, experiment.id, selectedVariant.variant_key);
  } catch {
    return null;
  }

  return {
    experiment_id: experiment.id,
    variant_key: selectedVariant.variant_key,
    config: selectedVariant.config,
  };
}

async function checkTargeting(
  store: FactoredStore,
  userId: string,
  experiment: RunningExperiment,
  deviceMetadata?: DeviceMetadata,
): Promise<boolean> {
  if (!experiment.targeting_rules || experiment.targeting_rules.length === 0) return true;

  const factors = await queryFactors(store, userId, experiment.component_path);
  return evaluateTargeting(factors, experiment.targeting_rules, deviceMetadata);
}

function selectVariantByHash(
  userId: string,
  experimentId: string,
  variants: VariantWithTraffic[],
): VariantWithTraffic | null {
  const hashValue = simpleHash(`${userId}:${experimentId}`);
  const bucket = hashValue % 100;

  let cumulativeTraffic = 0;
  for (const variant of variants) {
    cumulativeTraffic += variant.traffic_percentage;
    if (bucket < cumulativeTraffic) {
      return variant;
    }
  }

  return null;
}

// DJB2 hash — deterministic, fast, sufficient for traffic bucketing
function simpleHash(input: string): number {
  let hash = 5381;
  for (let i = 0; i < input.length; i++) {
    hash = ((hash << 5) + hash + input.charCodeAt(i)) >>> 0;
  }
  return hash;
}
