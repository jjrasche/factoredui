import {
  createContext,
  useContext,
  useEffect,
  useRef,
  type ReactNode,
} from "react";
import type { SupabaseClient } from "@supabase/supabase-js";
import type { CaptureHandle, AuxiConfig, Platform } from "../types.js";
import type { CaptureAdapter } from "../capture/adapter.js";
import { initCapture } from "../capture/index.js";

/**
 * React Native bindings for Auxi.
 * Requires a CaptureAdapter implementation from the host app
 * (e.g. an Expo adapter using expo-device, AsyncStorage, ErrorUtils).
 *
 * Platform-agnostic hooks (useFlag, useFactors, etc.) are re-exported
 * from the shared react bindings — they only need a Supabase client.
 */

// --- Context ---

interface AuxiNativeContextValue {
  supabase: SupabaseClient;
  platform: Platform;
  captureHandle: CaptureHandle | null;
}

const AuxiNativeContext = createContext<AuxiNativeContextValue | null>(null);

export function useAuxiNativeContext(): AuxiNativeContextValue {
  const ctx = useContext(AuxiNativeContext);
  if (!ctx) {
    throw new Error("useAuxiNativeContext: must be used within <AuxiNativeProvider>");
  }
  return ctx;
}

// --- Provider ---

interface AuxiNativeProviderProps {
  supabase: SupabaseClient;
  adapter: CaptureAdapter;
  platform: "ios" | "android";
  children: ReactNode;
  flushIntervalMs?: number;
  flushBatchSize?: number;
  sessionTimeoutMs?: number;
}

export function AuxiNativeProvider({
  supabase,
  adapter,
  platform,
  children,
  flushIntervalMs,
  flushBatchSize,
  sessionTimeoutMs,
}: AuxiNativeProviderProps): ReactNode {
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
    <AuxiNativeContext.Provider
      value={{ supabase, platform, captureHandle: captureRef.current }}
    >
      {children}
    </AuxiNativeContext.Provider>
  );
}

// Re-export contract layer (context-based paths)
export {
  AuxiFlow,
  AuxiPage,
  AuxiComponent,
  AuxiElement,
  useComponentPath,
} from "../capture/path-context.js";

// Re-export CaptureAdapter interface for implementors
export type { CaptureAdapter } from "../capture/adapter.js";

// Re-export platform-agnostic types
export type {
  AuxiConfig,
  AuxiEvent,
  AuxiSession,
  CaptureHandle,
  EventType,
  FactorTier,
  Factor,
  ExperimentAssignment,
  Platform,
} from "../types.js";
