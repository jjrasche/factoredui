import {
  createContext,
  useContext,
  useEffect,
  useRef,
  type ReactNode,
} from "react";
import type { SupabaseClient } from "@supabase/supabase-js";
import type { CaptureHandle, Config, Platform, CaptureAdapter, DeviceMetadata } from "@factoredui/core";
import { initCapture } from "@factoredui/core";
import type { ExperimentSummaryFilters } from "@factoredui/core";
import {
  useFlag as useFlagCore,
  useFactors as useFactorsCore,
  useGovernanceLog as useGovernanceLogCore,
  useRecentGovernanceLog as useRecentGovernanceLogCore,
  useExperimentDashboard as useExperimentDashboardCore,
  useComponentFactors as useComponentFactorsCore,
  useExperimentResults as useExperimentResultsCore,
} from "./hooks.js";

// --- Context ---

interface ContextValue {
  supabase: SupabaseClient;
  platform: Platform;
  deviceMetadata: DeviceMetadata;
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
  const deviceMetadata = useRef<DeviceMetadata>(buildDeviceMetadata(adapter, platform));

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
    deviceMetadata.current = buildDeviceMetadata(adapter, platform);

    return () => {
      captureRef.current?.flushEvents();
      captureRef.current?.stopCapture();
    };
  }, [supabase, adapter, platform, flushIntervalMs, flushBatchSize, sessionTimeoutMs]);

  return (
    <FactoredContext.Provider
      value={{ supabase, platform, deviceMetadata: deviceMetadata.current, captureHandle: captureRef.current }}
    >
      {children}
    </FactoredContext.Provider>
  );
}

// --- Context-aware hook wrappers ---

export function useFlag(experimentName: string) {
  const { supabase, platform, deviceMetadata } = useFactoredContext();
  return useFlagCore(supabase, experimentName, platform, deviceMetadata);
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

export function useComponentFactors(componentPath: string) {
  const { supabase } = useFactoredContext();
  return useComponentFactorsCore(supabase, componentPath);
}

export function useExperimentDashboard(filters?: ExperimentSummaryFilters) {
  const { supabase } = useFactoredContext();
  return useExperimentDashboardCore(supabase, filters);
}

export function useExperimentResults(experimentId: string, factorNames: string[]) {
  const { supabase } = useFactoredContext();
  return useExperimentResultsCore(supabase, experimentId, factorNames);
}

export function usePlatform(): Platform {
  return useFactoredContext().platform;
}

// --- Helpers ---

function buildDeviceMetadata(adapter: CaptureAdapter | undefined, platform: Platform): DeviceMetadata {
  const sessionMeta = adapter?.collectSessionMetadata() ?? {};
  return {
    platform,
    os_name: asString(sessionMeta.platform) ?? platform,
    os_version: asString(sessionMeta.platform_version),
    manufacturer: asString(sessionMeta.manufacturer),
    model: asString(sessionMeta.model),
    app_version: asString(sessionMeta.app_version),
    app_build: asString(sessionMeta.app_build),
  };
}

function asString(value: unknown): string | undefined {
  return typeof value === "string" ? value : undefined;
}
