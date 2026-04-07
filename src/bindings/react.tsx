import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import type { SupabaseClient } from "@supabase/supabase-js";
import type {
  CaptureHandle,
  ExperimentAssignment,
  Factor,
  ObserveConfig,
} from "../types.js";
import { initCapture } from "../capture/index.js";
import { evaluateFlag } from "../experiment/flags.js";
import {
  queryGovernanceLog,
  queryRecentGovernanceLog,
  type GovernanceLogRow,
} from "../experiment/governance-log.js";
import {
  queryExperimentSummaries,
  type ExperimentSummaryRow,
  type ExperimentSummaryFilters,
} from "../experiment/dashboard.js";

// --- Context ---

interface ObserveContextValue {
  supabase: SupabaseClient;
  captureHandle: CaptureHandle | null;
}

const ObserveContext = createContext<ObserveContextValue | null>(null);

function useObserveContext(): ObserveContextValue {
  const ctx = useContext(ObserveContext);
  if (!ctx) {
    throw new Error("useObserveContext: must be used within <ObserveProvider>");
  }
  return ctx;
}

// --- Provider ---

interface ObserveProviderProps {
  supabase: SupabaseClient;
  children: ReactNode;
  flushIntervalMs?: number;
  flushBatchSize?: number;
  sessionTimeoutMs?: number;
}

export function ObserveProvider({
  supabase,
  children,
  flushIntervalMs,
  flushBatchSize,
  sessionTimeoutMs,
}: ObserveProviderProps): ReactNode {
  const captureRef = useRef<CaptureHandle | null>(null);

  useEffect(() => {
    const config: ObserveConfig = {
      supabase,
      flushIntervalMs,
      flushBatchSize,
      sessionTimeoutMs,
    };
    captureRef.current = initCapture(config);

    return () => {
      captureRef.current?.flushEvents();
      captureRef.current?.stopCapture();
    };
  }, [supabase, flushIntervalMs, flushBatchSize, sessionTimeoutMs]);

  return (
    <ObserveContext.Provider
      value={{ supabase, captureHandle: captureRef.current }}
    >
      {children}
    </ObserveContext.Provider>
  );
}

// --- useFlag ---

interface UseFlagResult {
  variantKey: string | null;
  config: Record<string, unknown>;
  isLoading: boolean;
}

export function useFlag(experimentName: string): UseFlagResult {
  const { supabase } = useObserveContext();
  const [assignment, setAssignment] = useState<ExperimentAssignment | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isCancelled = false;

    evaluateFlag(supabase, experimentName).then((result) => {
      if (!isCancelled) {
        setAssignment(result);
        setIsLoading(false);
      }
    }).catch(() => {
      if (!isCancelled) setIsLoading(false);
    });

    return () => { isCancelled = true; };
  }, [supabase, experimentName]);

  return {
    variantKey: assignment?.variant_key ?? null,
    config: assignment?.config ?? {},
    isLoading,
  };
}

// --- useFactors ---

interface UseFactorsResult {
  factors: Factor[];
  isLoading: boolean;
}

export function useFactors(componentPath?: string): UseFactorsResult {
  const { supabase } = useObserveContext();
  const [factors, setFactors] = useState<Factor[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isCancelled = false;

    fetchUserFactors(supabase, componentPath).then((result) => {
      if (!isCancelled) {
        setFactors(result);
        setIsLoading(false);
      }
    }).catch(() => {
      if (!isCancelled) setIsLoading(false);
    });

    return () => { isCancelled = true; };
  }, [supabase, componentPath]);

  return { factors, isLoading };
}

async function fetchUserFactors(
  supabase: SupabaseClient,
  componentPath?: string,
): Promise<Factor[]> {
  const { data: { user } } = await supabase.auth.getUser();
  if (!user) return [];

  let query = supabase
    .from("v_factors_current")
    .select("*")
    .eq("user_id", user.id);

  if (componentPath) {
    query = query.eq("component_path", componentPath);
  }

  const { data, error } = await query;
  if (error || !data) return [];
  return data as Factor[];
}

// --- useGovernanceLog ---

interface UseGovernanceLogResult {
  log: GovernanceLogRow[];
  isLoading: boolean;
}

export function useGovernanceLog(experimentId: string): UseGovernanceLogResult {
  const { supabase } = useObserveContext();
  const [log, setLog] = useState<GovernanceLogRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isCancelled = false;

    queryGovernanceLog(supabase, experimentId).then((rows) => {
      if (!isCancelled) {
        setLog(rows);
        setIsLoading(false);
      }
    }).catch(() => {
      if (!isCancelled) setIsLoading(false);
    });

    return () => { isCancelled = true; };
  }, [supabase, experimentId]);

  return { log, isLoading };
}

// --- useRecentGovernanceLog ---

export function useRecentGovernanceLog(limit?: number): UseGovernanceLogResult {
  const { supabase } = useObserveContext();
  const [log, setLog] = useState<GovernanceLogRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isCancelled = false;

    queryRecentGovernanceLog(supabase, limit).then((rows) => {
      if (!isCancelled) {
        setLog(rows);
        setIsLoading(false);
      }
    }).catch(() => {
      if (!isCancelled) setIsLoading(false);
    });

    return () => { isCancelled = true; };
  }, [supabase, limit]);

  return { log, isLoading };
}

// --- useExperimentDashboard ---

interface UseExperimentDashboardResult {
  summaries: ExperimentSummaryRow[];
  isLoading: boolean;
}

export function useExperimentDashboard(
  filters?: ExperimentSummaryFilters,
): UseExperimentDashboardResult {
  const { supabase } = useObserveContext();
  const [summaries, setSummaries] = useState<ExperimentSummaryRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const filterKey = JSON.stringify(filters ?? {});

  useEffect(() => {
    let isCancelled = false;

    queryExperimentSummaries(supabase, filters).then((rows) => {
      if (!isCancelled) {
        setSummaries(rows);
        setIsLoading(false);
      }
    }).catch(() => {
      if (!isCancelled) setIsLoading(false);
    });

    return () => { isCancelled = true; };
  }, [supabase, filterKey]);

  return { summaries, isLoading };
}
