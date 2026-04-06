import type { SupabaseClient } from "@supabase/supabase-js";

export type EventType =
  | "click"
  | "scroll"
  | "error"
  | "navigation"
  | "impression"
  | "input"
  | "focus"
  | "blur"
  | "submit"
  | "resize"
  | "visibility"
  | "rage_click"
  | "dead_click"
  | "scroll_reversal";

export type FactorTier = "alarm" | "diagnostic" | "structural";

export interface ObserveEvent {
  event_type: EventType;
  component_path: string;
  payload: Record<string, unknown>;
}

export interface ObserveSession {
  id: string;
  user_id: string;
  started_at: string;
  ended_at: string | null;
  metadata: Record<string, unknown>;
}

export interface Factor {
  user_id: string;
  component_path: string;
  factor_name: string;
  factor_tier: FactorTier;
  value: number;
  computed_at: string;
}

export interface ExperimentAssignment {
  experiment_id: string;
  variant_key: string;
  config: Record<string, unknown>;
}

export interface ObserveConfig {
  supabase: SupabaseClient;
  flushIntervalMs?: number;
  flushBatchSize?: number;
  sessionTimeoutMs?: number;
}

export interface CaptureHandle {
  stopCapture: () => void;
  flushEvents: () => Promise<void>;
  getSessionId: () => string | null;
  trackNavigation: (componentPath: string, action: string) => void;
  trackImpression: (componentPath: string) => void;
}
