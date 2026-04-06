import type { SupabaseClient } from "@supabase/supabase-js";
import type { ExperimentAssignment } from "../types.js";

/**
 * Flag evaluation: reads experiment assignments for the current user.
 * If not yet assigned to a running experiment, assigns deterministically via hash bucketing.
 */

export async function evaluateFlag(
  supabase: SupabaseClient,
  experimentName: string,
): Promise<ExperimentAssignment | null> {
  const userId = await resolveUserId(supabase);
  if (!userId) return null;

  const existingAssignment = await fetchAssignment(supabase, userId, experimentName);
  if (existingAssignment) {
    await recordExposure(supabase, userId, existingAssignment);
    return existingAssignment;
  }

  const newAssignment = await assignToExperiment(supabase, userId, experimentName);
  if (newAssignment) {
    await recordExposure(supabase, userId, newAssignment);
  }
  return newAssignment;
}

async function resolveUserId(supabase: SupabaseClient): Promise<string | null> {
  const { data: { user } } = await supabase.auth.getUser();
  return user?.id ?? null;
}

interface AssignmentJoinRow {
  experiment_id: string;
  variant_key: string;
  experiment_variants: { config: Record<string, unknown> } | null;
}

async function fetchAssignment(
  supabase: SupabaseClient,
  userId: string,
  experimentName: string,
): Promise<ExperimentAssignment | null> {
  const { data, error } = await supabase
    .from("experiment_assignments")
    .select(`
      experiment_id,
      variant_key,
      experiments!inner ( name, status ),
      experiment_variants!inner ( config )
    `)
    .eq("user_id", userId)
    .eq("experiments.name", experimentName)
    .eq("experiments.status", "running")
    .maybeSingle();

  if (error || !data) return null;

  const row = data as unknown as AssignmentJoinRow;
  return {
    experiment_id: row.experiment_id,
    variant_key: row.variant_key,
    config: row.experiment_variants?.config ?? {},
  };
}

async function assignToExperiment(
  supabase: SupabaseClient,
  userId: string,
  experimentName: string,
): Promise<ExperimentAssignment | null> {
  const experiment = await fetchRunningExperiment(supabase, experimentName);
  if (!experiment) return null;

  const variants = await fetchExperimentVariants(supabase, experiment.id);
  if (variants.length === 0) return null;

  const selectedVariant = selectVariantByHash(userId, experiment.id, variants);
  if (!selectedVariant) return null;

  const { error } = await supabase
    .from("experiment_assignments")
    .insert({
      user_id: userId,
      experiment_id: experiment.id,
      variant_key: selectedVariant.variant_key,
    });

  if (error) return null;

  return {
    experiment_id: experiment.id,
    variant_key: selectedVariant.variant_key,
    config: selectedVariant.config,
  };
}

async function fetchRunningExperiment(
  supabase: SupabaseClient,
  experimentName: string,
): Promise<{ id: string; name: string } | null> {
  const { data, error } = await supabase
    .from("experiments")
    .select("id, name")
    .eq("name", experimentName)
    .eq("status", "running")
    .maybeSingle();

  if (error || !data) return null;
  return data as { id: string; name: string };
}

interface VariantWithTraffic {
  variant_key: string;
  config: Record<string, unknown>;
  traffic_percentage: number;
}

async function fetchExperimentVariants(
  supabase: SupabaseClient,
  experimentId: string,
): Promise<VariantWithTraffic[]> {
  const { data, error } = await supabase
    .from("experiment_variants")
    .select("variant_key, config, traffic_percentage")
    .eq("experiment_id", experimentId)
    .order("variant_key");

  if (error || !data) return [];
  return data as VariantWithTraffic[];
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

async function recordExposure(
  supabase: SupabaseClient,
  userId: string,
  assignment: ExperimentAssignment,
): Promise<void> {
  await supabase.from("experiment_exposures").insert({
    user_id: userId,
    experiment_id: assignment.experiment_id,
    variant_key: assignment.variant_key,
  });
}
