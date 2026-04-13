import {
  createContext,
  useContext,
  useEffect,
  useRef,
  type ReactNode,
} from "react";
import type { SupabaseClient } from "@supabase/supabase-js";
import type { CaptureHandle, Config, Platform, CaptureAdapter } from "@factoredui/core";
import { initCapture } from "@factoredui/core";
import type { ExperimentSummaryFilters } from "@factoredui/core";
import {
  useFlag as useFlagCore,
  useFactors as useFactorsCore,
  useGovernanceLog as useGovernanceLogCore,
  useRecentGovernanceLog as useRecentGovernanceLogCore,
  useExperimentDashboard as useExperimentDashboardCore,
} from "./hooks.js";

// --- Context ---

interface ContextValue {
  supabase: SupabaseClient;
  platform: Platform;
  captureHandle: CaptureHandle | null;
}

const FactoredContext = createContext<ContextValue | null>(null);

function useFactoredContext(): ContextValue {
  const ctx = useContext(FactoredContext);
  if (!ctx) {
    throw new Error("useFactoredContext: must be used within <Provider>");
  }
  return ctx;
}

// --- Provider ---

interface ProviderProps {
  supabase: SupabaseClient;
  adapter?: CaptureAdapter;
  platform?: Platform;
  children: ReactNode;
  flushIntervalMs?: number;
  flushBatchSize?: number;
  sessionTimeoutMs?: number;
}

export function Provider({
  supabase,
  adapter,
  platform = "web",
  children,
  flushIntervalMs,
  flushBatchSize,
  sessionTimeoutMs,
}: ProviderProps): ReactNode {
  const captureRef = useRef<CaptureHandle | null>(null);

  useEffect(() => {
    const config: Config = {
      supabase,
      adapter,
      platform,
      flushIntervalMs,
      flushBatchSize,
      sessionTimeoutMs,
    };
    captureRef.current = initCapture(config);

    return () => {
      captureRef.current?.flushEvents();
      captureRef.current?.stopCapture();
    };
  }, [supabase, adapter, platform, flushIntervalMs, flushBatchSize, sessionTimeoutMs]);

  return (
    <FactoredContext.Provider
      value={{ supabase, platform, captureHandle: captureRef.current }}
    >
      {children}
    </FactoredContext.Provider>
  );
}

// --- Context-aware hook wrappers ---

export function useFlag(experimentName: string) {
  const { supabase } = useFactoredContext();
  return useFlagCore(supabase, experimentName);
}

export function useFactors(componentPath?: string) {
  const { supabase } = useFactoredContext();
  return useFactorsCore(supabase, componentPath);
}

export function useGovernanceLog(experimentId: string) {
  const { supabase } = useFactoredContext();
  return useGovernanceLogCore(supabase, experimentId);
}

export function useRecentGovernanceLog(limit?: number) {
  const { supabase } = useFactoredContext();
  return useRecentGovernanceLogCore(supabase, limit);
}

export function useExperimentDashboard(filters?: ExperimentSummaryFilters) {
  const { supabase } = useFactoredContext();
  return useExperimentDashboardCore(supabase, filters);
}

export function usePlatform(): Platform {
  return useFactoredContext().platform;
}
