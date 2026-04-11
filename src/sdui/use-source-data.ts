import { useState, useCallback, useMemo } from "react";
import type { DataSourceRegistry } from "./spec-types.js";
import type { DataSourceCache, ResolvedSources } from "./data-source.js";
import { resolveAllSources } from "./data-source.js";

/**
 * Generic hook for resolving SDUI data sources.
 * Wraps resolveAllSources with React state management.
 * Any SDUI consumer can use this — no app-specific logic.
 */

export interface SourceDataState {
  sourceData: Record<string, unknown>;
  errors: Record<string, string>;
  sourcesLoaded: boolean;
  refreshSources: () => Promise<void>;
}

export function useSourceData(
  buildRegistry: () => DataSourceRegistry,
  cache: DataSourceCache,
): SourceDataState {
  const [sourceData, setSourceData] = useState<Record<string, unknown>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [sourcesLoaded, setSourcesLoaded] = useState(false);

  const registry = useMemo(buildRegistry, [buildRegistry]);

  const refreshSources = useCallback(async () => {
    const resolved: ResolvedSources = await resolveAllSources(registry, cache);
    setSourceData(resolved.sources);
    setErrors(resolved.errors);
    setSourcesLoaded(true);
  }, [registry, cache]);

  return { sourceData, errors, sourcesLoaded, refreshSources };
}
