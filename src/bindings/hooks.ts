import {
  useCallback,
  useEffect,
  useState,
} from "react";
import type { SupabaseClient, RealtimeChannel } from "@supabase/supabase-js";
import type { ExperimentAssignment, Factor } from "../types.js";
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

// --- useFlag ---

export interface UseFlagResult {
  variantKey: string | null;
  config: Record<string, unknown>;
  isLoading: boolean;
}

export function useFlag(supabase: SupabaseClient, experimentName: string): UseFlagResult {
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

export interface UseFactorsResult {
  factors: Factor[];
  isLoading: boolean;
}

export function useFactors(supabase: SupabaseClient, componentPath?: string): UseFactorsResult {
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

export interface UseGovernanceLogResult {
  log: GovernanceLogRow[];
  isLoading: boolean;
}

export function useGovernanceLog(supabase: SupabaseClient, experimentId: string): UseGovernanceLogResult {
  const [log, setLog] = useState<GovernanceLogRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const prependRow = useCallback((row: GovernanceLogRow) => {
    setLog((prev) => [row, ...prev]);
  }, []);

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

    const channel = subscribeToGovernanceInserts(supabase, experimentId, prependRow);

    return () => {
      isCancelled = true;
      supabase.removeChannel(channel);
    };
  }, [supabase, experimentId, prependRow]);

  return { log, isLoading };
}

// --- useRecentGovernanceLog ---

export function useRecentGovernanceLog(supabase: SupabaseClient, limit?: number): UseGovernanceLogResult {
  const [log, setLog] = useState<GovernanceLogRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const effectiveLimit = limit ?? 50;

  const prependRow = useCallback((row: GovernanceLogRow) => {
    setLog((prev) => [row, ...prev].slice(0, effectiveLimit));
  }, [effectiveLimit]);

  useEffect(() => {
    let isCancelled = false;

    queryRecentGovernanceLog(supabase, effectiveLimit).then((rows) => {
      if (!isCancelled) {
        setLog(rows);
        setIsLoading(false);
      }
    }).catch(() => {
      if (!isCancelled) setIsLoading(false);
    });

    const channel = subscribeToAllGovernanceInserts(supabase, prependRow);

    return () => {
      isCancelled = true;
      supabase.removeChannel(channel);
    };
  }, [supabase, effectiveLimit, prependRow]);

  return { log, isLoading };
}

// --- useExperimentDashboard ---

export interface UseExperimentDashboardResult {
  summaries: ExperimentSummaryRow[];
  isLoading: boolean;
}

export function useExperimentDashboard(
  supabase: SupabaseClient,
  filters?: ExperimentSummaryFilters,
): UseExperimentDashboardResult {
  const [summaries, setSummaries] = useState<ExperimentSummaryRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const filterKey = JSON.stringify(filters ?? {});

  const refetchSummaries = useCallback(() => {
    queryExperimentSummaries(supabase, filters).then((rows) => {
      setSummaries(rows);
    }).catch(() => {});
  }, [supabase, filterKey]);

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

    const channel = subscribeToExperimentChanges(supabase, refetchSummaries);

    return () => {
      isCancelled = true;
      supabase.removeChannel(channel);
    };
  }, [supabase, filterKey, refetchSummaries]);

  return { summaries, isLoading };
}

// --- Realtime subscription helpers ---

function subscribeToGovernanceInserts(
  supabase: SupabaseClient,
  experimentId: string,
  onInsert: (row: GovernanceLogRow) => void,
): RealtimeChannel {
  return supabase
    .channel(`governance-log:${experimentId}`)
    .on(
      "postgres_changes",
      {
        event: "INSERT",
        schema: "auxi",
        table: "governance_log",
        filter: `experiment_id=eq.${experimentId}`,
      },
      (payload) => onInsert(payload.new as GovernanceLogRow),
    )
    .subscribe();
}

function subscribeToAllGovernanceInserts(
  supabase: SupabaseClient,
  onInsert: (row: GovernanceLogRow) => void,
): RealtimeChannel {
  return supabase
    .channel("governance-log:all")
    .on(
      "postgres_changes",
      {
        event: "INSERT",
        schema: "auxi",
        table: "governance_log",
      },
      (payload) => onInsert(payload.new as GovernanceLogRow),
    )
    .subscribe();
}

function subscribeToExperimentChanges(
  supabase: SupabaseClient,
  onChange: () => void,
): RealtimeChannel {
  return supabase
    .channel("experiments:changes")
    .on(
      "postgres_changes",
      {
        event: "*",
        schema: "auxi",
        table: "experiments",
      },
      () => onChange(),
    )
    .subscribe();
}
