import type { SupabaseClient } from "@supabase/supabase-js";
import type { Factor, FactorTier } from "../types.js";

export interface ComponentFactorAggregate {
  component_path: string;
  factor_name: string;
  factor_tier: FactorTier;
  user_count: number;
  avg_value: number;
  median_value: number;
  p95_value: number;
  min_value: number;
  max_value: number;
  stddev_value: number | null;
}

export async function queryFactors(
  client: SupabaseClient,
  userId: string,
  componentPath: string,
): Promise<Factor[]> {
  const { data, error } = await client
    .from("v_factors_current")
    .select("user_id, component_path, factor_name, factor_tier, value, computed_at")
    .eq("user_id", userId)
    .eq("component_path", componentPath);

  if (error) throw new Error(`queryFactors failed: ${error.message}`);
  return data as Factor[];
}

export async function queryComponentFactors(
  client: SupabaseClient,
  componentPath: string,
): Promise<ComponentFactorAggregate[]> {
  const { data, error } = await client
    .from("v_component_factors_agg")
    .select(
      "component_path, factor_name, factor_tier, user_count, avg_value, median_value, p95_value, min_value, max_value, stddev_value",
    )
    .eq("component_path", componentPath);

  if (error) throw new Error(`queryComponentFactors failed: ${error.message}`);
  return data as ComponentFactorAggregate[];
}
