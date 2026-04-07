import type { SupabaseClient } from "@supabase/supabase-js";
import type { AuxiSession } from "../types.js";

const SESSION_STORAGE_KEY = "auxi:session_id";
const DEFAULT_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes

export interface SessionManager {
  ensureSession: (userId: string) => Promise<string>;
  endSession: () => Promise<void>;
  getSessionId: () => string | null;
}

export function createSessionManager(
  supabase: SupabaseClient,
  timeoutMs: number = DEFAULT_TIMEOUT_MS,
): SessionManager {
  let currentSessionId: string | null = loadStoredSessionId();
  let lastActivityAt = Date.now();

  function loadStoredSessionId(): string | null {
    try {
      return sessionStorage.getItem(SESSION_STORAGE_KEY);
    } catch {
      return null;
    }
  }

  function storeSessionId(sessionId: string): void {
    try {
      sessionStorage.setItem(SESSION_STORAGE_KEY, sessionId);
    } catch {
      // SSR or restricted storage -- session lives in memory only
    }
  }

  function isSessionExpired(): boolean {
    return Date.now() - lastActivityAt > timeoutMs;
  }

  async function createSession(userId: string): Promise<string> {
    const { data, error } = await supabase
      .from("sessions")
      .insert({ user_id: userId, metadata: collectSessionMetadata() })
      .select("id")
      .single();

    if (error) throw new Error(`Failed to create session: ${error.message}`);
    return (data as AuxiSession).id;
  }

  function collectSessionMetadata(): Record<string, unknown> {
    if (typeof window === "undefined") return {};
    return {
      user_agent: navigator.userAgent,
      screen_width: screen.width,
      screen_height: screen.height,
      viewport_width: window.innerWidth,
      viewport_height: window.innerHeight,
      language: navigator.language,
      referrer: document.referrer || null,
      url: window.location.href,
    };
  }

  async function ensureSession(userId: string): Promise<string> {
    lastActivityAt = Date.now();

    if (currentSessionId && !isSessionExpired()) {
      return currentSessionId;
    }

    currentSessionId = await createSession(userId);
    storeSessionId(currentSessionId);
    return currentSessionId;
  }

  async function endSession(): Promise<void> {
    if (!currentSessionId) return;

    await supabase
      .from("sessions")
      .update({ ended_at: new Date().toISOString() })
      .eq("id", currentSessionId);

    currentSessionId = null;
    try {
      sessionStorage.removeItem(SESSION_STORAGE_KEY);
    } catch {
      // Ignore storage errors
    }
  }

  function getSessionId(): string | null {
    return currentSessionId;
  }

  return { ensureSession, endSession, getSessionId };
}
