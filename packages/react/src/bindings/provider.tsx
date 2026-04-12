import {
  createContext,
  useContext,
  useEffect,
  useRef,
  type ReactNode,
} from "react";
import type { SupabaseClient } from "@supabase/supabase-js";
import type { CaptureHandle, AuxiConfig, Platform, CaptureAdapter } from "@factoredui/core";
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

interface AuxiContextValue {
  supabase: SupabaseClient;
  platform: Platform;
  captureHandle: CaptureHandle | null;
}

const AuxiContext = createContext<AuxiContextValue | null>(null);

function useAuxiContext(): AuxiContextValue {
  const ctx = useContext(AuxiContext);
  if (!ctx) {
    throw new Error("useAuxiContext: must be used within <AuxiProvider>");
  }
  return ctx;
}

// --- Provider ---

interface AuxiProviderProps {
  supabase: SupabaseClient;
  adapter: CaptureAdapter;
  platform: Platform;
  children: ReactNode;
  flushIntervalMs?: number;
  flushBatchSize?: number;
  sessionTimeoutMs?: number;
}

export function AuxiProvider({
  supabase,
  adapter,
  platform,
  children,
  flushIntervalMs,
  flushBatchSize,
  sessionTimeoutMs,
}: AuxiProviderProps): ReactNode {
  const captureRef = useRef<CaptureHandle | null>(null);

  useEffect(() => {
    const config: AuxiConfig = {
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
    <AuxiContext.Provider
      value={{ supabase, platform, captureHandle: captureRef.current }}
    >
      {children}
    </AuxiContext.Provider>
  );
}

// --- Context-aware hook wrappers ---

export function useFlag(experimentName: string) {
  const { supabase } = useAuxiContext();
  return useFlagCore(supabase, experimentName);
}

export function useFactors(componentPath?: string) {
  const { supabase } = useAuxiContext();
  return useFactorsCore(supabase, componentPath);
}

export function useGovernanceLog(experimentId: string) {
  const { supabase } = useAuxiContext();
  return useGovernanceLogCore(supabase, experimentId);
}

export function useRecentGovernanceLog(limit?: number) {
  const { supabase } = useAuxiContext();
  return useRecentGovernanceLogCore(supabase, limit);
}

export function useExperimentDashboard(filters?: ExperimentSummaryFilters) {
  const { supabase } = useAuxiContext();
  return useExperimentDashboardCore(supabase, filters);
}

export function useAuxiPlatform(): Platform {
  return useAuxiContext().platform;
}
