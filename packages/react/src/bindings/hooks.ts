import {
  useCallback,
  useEffect,
  useState,
} from "react";
import type { FactoredStore, ExperimentAssignment, Factor, ComponentFactorAggregate, GovernanceLogRow, ExperimentSummaryRow, ExperimentSummaryFilters, VariantResult, Platform, DeviceMetadata } from "@factoredui/core";
import { evaluateFlag, queryGovernanceLog, queryRecentGovernanceLog, queryExperimentSummaries, queryComponentFactors, queryExperimentResults } from "@factoredui/core";

// --- useFlag ---

export interface UseFlagResult {
  variantKey: string | null;
  config: Record<string, unknown>;
  isLoading: boolean;
}

export function useFlag(store: FactoredStore, experimentName: string, platform?: Platform, deviceMetadata?: DeviceMetadata): UseFlagResult {
  const [assignment, setAssignment] = useState<ExperimentAssignment | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isCancelled = false;

    evaluateFlag(store, experimentName, platform, deviceMetadata).then((result) => {
      if (!isCancelled) {
        setAssignment(result);
        setIsLoading(false);
      }
    }).catch(() => {
      if (!isCancelled) setIsLoading(false);
    });

    return () => { isCancelled = true; };
  }, [store, experimentName, platform, deviceMetadata]);

  return {
    variantKey: assignment?.variant_key ?? null,
    config: assignment?.config ?? {},
    isLoading,
  };
}

// --- useFactors ---

export interface UseFactorsResult {
  factors: Factor[];
  isLoading: boolean;
}

export function useFactors(store: FactoredStore, componentPath?: string): UseFactorsResult {
  const [factors, setFactors] = useState<Factor[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isCancelled = false;

    fetchUserFactors(store, componentPath).then((result) => {
      if (!isCancelled) {
        setFactors(result);
        setIsLoading(false);
      }
    }).catch(() => {
      if (!isCancelled) setIsLoading(false);
    });

    return () => { isCancelled = true; };
  }, [store, componentPath]);

  return { factors, isLoading };
}

async function fetchUserFactors(
  store: FactoredStore,
  componentPath?: string,
): Promise<Factor[]> {
  const userId = await store.getCurrentUserId();
  if (!userId) return [];

  return store.queryFactors(userId, componentPath);
}

// --- useComponentFactors ---

export interface UseComponentFactorsResult {
  aggregates: ComponentFactorAggregate[];
  isLoading: boolean;
}

export function useComponentFactors(store: FactoredStore, componentPath: string): UseComponentFactorsResult {
  const [aggregates, setAggregates] = useState<ComponentFactorAggregate[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isCancelled = false;

    queryComponentFactors(store, componentPath).then((result) => {
      if (!isCancelled) {
        setAggregates(result);
        setIsLoading(false);
      }
    }).catch(() => {
      if (!isCancelled) setIsLoading(false);
    });

    return () => { isCancelled = true; };
  }, [store, componentPath]);

  return { aggregates, isLoading };
}

// --- useGovernanceLog ---

export interface UseGovernanceLogResult {
  log: GovernanceLogRow[];
  isLoading: boolean;
}

export function useGovernanceLog(store: FactoredStore, experimentId: string): UseGovernanceLogResult {
  const [log, setLog] = useState<GovernanceLogRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const prependRow = useCallback((row: GovernanceLogRow) => {
    setLog((prev) => [row, ...prev]);
  }, []);

  useEffect(() => {
    let isCancelled = false;

    queryGovernanceLog(store, experimentId).then((rows) => {
      if (!isCancelled) {
        setLog(rows);
        setIsLoading(false);
      }
    }).catch(() => {
      if (!isCancelled) setIsLoading(false);
    });

    const unsubscribe = store.subscribe?.(
      `governance-log:${experimentId}`,
      "governance_log",
      `experiment_id=eq.${experimentId}`,
      (row) => prependRow(row as GovernanceLogRow),
    );

    return () => {
      isCancelled = true;
      unsubscribe?.();
    };
  }, [store, experimentId, prependRow]);

  return { log, isLoading };
}

// --- useRecentGovernanceLog ---

export function useRecentGovernanceLog(store: FactoredStore, limit?: number): UseGovernanceLogResult {
  const [log, setLog] = useState<GovernanceLogRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const effectiveLimit = limit ?? 50;

  const prependRow = useCallback((row: GovernanceLogRow) => {
    setLog((prev) => [row, ...prev].slice(0, effectiveLimit));
  }, [effectiveLimit]);

  useEffect(() => {
    let isCancelled = false;

    queryRecentGovernanceLog(store, effectiveLimit).then((rows) => {
      if (!isCancelled) {
        setLog(rows);
        setIsLoading(false);
      }
    }).catch(() => {
      if (!isCancelled) setIsLoading(false);
    });

    const unsubscribe = store.subscribe?.(
      "governance-log:all",
      "governance_log",
      null,
      (row) => prependRow(row as GovernanceLogRow),
    );

    return () => {
      isCancelled = true;
      unsubscribe?.();
    };
  }, [store, effectiveLimit, prependRow]);

  return { log, isLoading };
}

// --- useExperimentDashboard ---

export interface UseExperimentDashboardResult {
  summaries: ExperimentSummaryRow[];
  isLoading: boolean;
}

export function useExperimentDashboard(
  store: FactoredStore,
  filters?: ExperimentSummaryFilters,
): UseExperimentDashboardResult {
  const [summaries, setSummaries] = useState<ExperimentSummaryRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const filterKey = JSON.stringify(filters ?? {});

  const refetchSummaries = useCallback(() => {
    queryExperimentSummaries(store, filters).then((rows) => {
      setSummaries(rows);
    }).catch(() => {});
  }, [store, filterKey]);

  useEffect(() => {
    let isCancelled = false;

    queryExperimentSummaries(store, filters).then((rows) => {
      if (!isCancelled) {
        setSummaries(rows);
        setIsLoading(false);
      }
    }).catch(() => {
      if (!isCancelled) setIsLoading(false);
    });

    const unsubscribe = store.subscribe?.(
      "experiments:changes",
      "experiments",
      null,
      () => refetchSummaries(),
    );

    return () => {
      isCancelled = true;
      unsubscribe?.();
    };
  }, [store, filterKey, refetchSummaries]);

  return { summaries, isLoading };
}

// --- useExperimentResults ---

export interface UseExperimentResultsResult {
  results: VariantResult[];
  isLoading: boolean;
  refetch: () => void;
}

export function useExperimentResults(
  store: FactoredStore,
  experimentId: string,
  factorNames: string[],
): UseExperimentResultsResult {
  const [results, setResults] = useState<VariantResult[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const factorKey = JSON.stringify(factorNames);

  const refetch = useCallback(() => {
    setIsLoading(true);
    queryExperimentResults(store, experimentId, factorNames).then((rows) => {
      setResults(rows);
      setIsLoading(false);
    }).catch(() => {
      setIsLoading(false);
    });
  }, [store, experimentId, factorKey]);

  useEffect(() => {
    let isCancelled = false;

    queryExperimentResults(store, experimentId, factorNames).then((rows) => {
      if (!isCancelled) {
        setResults(rows);
        setIsLoading(false);
      }
    }).catch(() => {
      if (!isCancelled) setIsLoading(false);
    });

    return () => { isCancelled = true; };
  }, [store, experimentId, factorKey]);

  return { results, isLoading, refetch };
}
