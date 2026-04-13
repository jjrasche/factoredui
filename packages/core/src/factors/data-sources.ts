import type { SupabaseClient } from "@supabase/supabase-js";
import type { DataSourceConfig } from "../sdui/spec-types.js";
import { queryFactors, queryComponentFactors } from "./query.js";
import { queryFactorHistory } from "./snapshots.js";

/**
 * SDUI data source configs for factor views.
 * Each returns a DataSourceConfig that specs reference by name in their registry.
 */

export function factorSource(
  supabase: SupabaseClient,
  userId: string,
  componentPath: string,
): DataSourceConfig {
  return {
    fetch: () => queryFactors(supabase, userId, componentPath),
    cache: "local",
  };
}

export function componentFactorSource(
  supabase: SupabaseClient,
  componentPath: string,
): DataSourceConfig {
  return {
    fetch: () => queryComponentFactors(supabase, componentPath),
    cache: "local",
  };
}

export function factorHistorySource(
  supabase: SupabaseClient,
  userId: string,
  componentPath: string,
  factorName: string,
  since: Date,
): DataSourceConfig {
  return {
    fetch: () => queryFactorHistory(supabase, userId, componentPath, factorName, since),
    cache: "local",
  };
}
