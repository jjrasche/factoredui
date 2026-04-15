import type { FactoredStore } from "../store.js";
import type { DataSourceConfig } from "../sdui/spec-types.js";
import { queryFactors, queryComponentFactors } from "./query.js";
import { queryFactorHistory } from "./snapshots.js";

/**
 * SDUI data source configs for factor views.
 * Each returns a DataSourceConfig that specs reference by name in their registry.
 */

export function factorSource(
  store: FactoredStore,
  userId: string,
  componentPath: string,
): DataSourceConfig {
  return {
    fetch: () => queryFactors(store, userId, componentPath),
    cache: "local",
  };
}

export function componentFactorSource(
  store: FactoredStore,
  componentPath: string,
): DataSourceConfig {
  return {
    fetch: () => queryComponentFactors(store, componentPath),
    cache: "local",
  };
}

export function factorHistorySource(
  store: FactoredStore,
  userId: string,
  componentPath: string,
  factorName: string,
  since: Date,
): DataSourceConfig {
  return {
    fetch: () => queryFactorHistory(store, userId, componentPath, factorName, since),
    cache: "local",
  };
}
