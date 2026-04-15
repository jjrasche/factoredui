import {
  createContext,
  useContext,
  useEffect,
  useRef,
  type ReactNode,
} from "react";
import type { FactoredStore, CaptureHandle, Config, Platform, CaptureAdapter, DeviceMetadata } from "@factoredui/core";
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
  store: FactoredStore;
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
  store: FactoredStore;
  adapter?: CaptureAdapter;
  platform?: Platform;
  children: ReactNode;
  flushIntervalMs?: number;
  flushBatchSize?: number;
  sessionTimeoutMs?: number;
}

export function Provider({
  store,
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
      store,
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
  }, [store, adapter, platform, flushIntervalMs, flushBatchSize, sessionTimeoutMs]);

  return (
    <FactoredContext.Provider
      value={{ store, platform, deviceMetadata: deviceMetadata.current, captureHandle: captureRef.current }}
    >
      {children}
    </FactoredContext.Provider>
  );
}

// --- Context-aware hook wrappers ---

export function useFlag(experimentName: string) {
  const { store, platform, deviceMetadata } = useFactoredContext();
  return useFlagCore(store, experimentName, platform, deviceMetadata);
}

export function useFactors(componentPath?: string) {
  const { store } = useFactoredContext();
  return useFactorsCore(store, componentPath);
}

export function useGovernanceLog(experimentId: string) {
  const { store } = useFactoredContext();
  return useGovernanceLogCore(store, experimentId);
}

export function useRecentGovernanceLog(limit?: number) {
  const { store } = useFactoredContext();
  return useRecentGovernanceLogCore(store, limit);
}

export function useComponentFactors(componentPath: string) {
  const { store } = useFactoredContext();
  return useComponentFactorsCore(store, componentPath);
}

export function useExperimentDashboard(filters?: ExperimentSummaryFilters) {
  const { store } = useFactoredContext();
  return useExperimentDashboardCore(store, filters);
}

export function useExperimentResults(experimentId: string, factorNames: string[]) {
  const { store } = useFactoredContext();
  return useExperimentResultsCore(store, experimentId, factorNames);
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
