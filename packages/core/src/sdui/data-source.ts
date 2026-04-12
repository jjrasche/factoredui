import type { DataSourceRegistry, DataSourceConfig } from "./spec-types.js";

/**
 * Resolves data sources for spec rendering.
 * Each source has a fetch function, optional cache policy, and optional realtime flag.
 * Offline: serves cached data if available, empty object if not.
 */

export interface ResolvedSources {
  sources: Record<string, unknown>;
  errors: Record<string, string>;
}

export interface DataSourceCache {
  load(key: string): Promise<unknown | null>;
  save(key: string, data: unknown): Promise<void>;
}

export async function resolveAllSources(
  registry: DataSourceRegistry,
  cache: DataSourceCache,
): Promise<ResolvedSources> {
  const sourceEntries = Object.entries(registry);
  const results = await Promise.allSettled(
    sourceEntries.map(([name, config]) => resolveSource(name, config, cache)),
  );

  return assembleResults(sourceEntries, results);
}

async function resolveSource(
  name: string,
  config: DataSourceConfig,
  cache: DataSourceCache,
): Promise<{ name: string; data: unknown }> {
  const cached = config.cache === "local" ? await cache.load(name) : null;

  try {
    const fresh = await config.fetch();
    const data = applyMaxItems(fresh, config.maxItems);

    if (config.cache === "local") {
      await cache.save(name, data);
    }

    return { name, data };
  } catch {
    if (cached != null) {
      return { name, data: cached };
    }
    throw new Error(`source "${name}" fetch failed and no cache available`);
  }
}

function applyMaxItems(data: unknown, maxItems?: number): unknown {
  if (!maxItems || !Array.isArray(data)) return data;
  return data.slice(0, maxItems);
}

function assembleResults(
  entries: [string, DataSourceConfig][],
  results: PromiseSettledResult<{ name: string; data: unknown }>[],
): ResolvedSources {
  const sources: Record<string, unknown> = {};
  const errors: Record<string, string> = {};

  for (let i = 0; i < entries.length; i++) {
    const [name] = entries[i];
    const result = results[i];

    if (result.status === "fulfilled") {
      sources[name] = result.value.data;
    } else {
      errors[name] = result.reason?.message ?? "unknown error";
      sources[name] = null;
    }
  }

  return { sources, errors };
}
