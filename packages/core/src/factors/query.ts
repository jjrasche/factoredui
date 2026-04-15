import type { FactoredStore } from "../store.js";
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
  store: FactoredStore,
  userId: string,
  componentPath: string,
): Promise<Factor[]> {
  return store.queryFactors(userId, componentPath);
}

export async function queryComponentFactors(
  store: FactoredStore,
  componentPath: string,
): Promise<ComponentFactorAggregate[]> {
  return store.queryComponentFactors(componentPath);
}
