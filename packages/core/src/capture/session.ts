import type { FactoredStore } from "../store.js";
import type { Platform } from "../types.js";
import type { CaptureAdapter } from "./adapter.js";

const DEFAULT_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes

export interface SessionManager {
  ensureSession: (userId: string) => Promise<string>;
  endSession: () => Promise<void>;
  getSessionId: () => string | null;
}

export function createSessionManager(
  store: FactoredStore,
  adapter: CaptureAdapter,
  platform: Platform,
  timeoutMs: number = DEFAULT_TIMEOUT_MS,
): SessionManager {
  let currentSessionId: string | null = adapter.loadSessionId();
  let lastActivityAt = Date.now();

  function isSessionExpired(): boolean {
    return Date.now() - lastActivityAt > timeoutMs;
  }

  async function createSession(userId: string): Promise<string> {
    const metadata = {
      ...adapter.collectSessionMetadata(),
      platform,
    };

    const { id } = await store.insertSession(userId, metadata);
    return id;
  }

  async function ensureSession(userId: string): Promise<string> {
    lastActivityAt = Date.now();

    if (currentSessionId && !isSessionExpired()) {
      return currentSessionId;
    }

    currentSessionId = await createSession(userId);
    adapter.storeSessionId(currentSessionId);
    return currentSessionId;
  }

  async function endSession(): Promise<void> {
    if (!currentSessionId) return;

    await store.endSession(currentSessionId);

    currentSessionId = null;
    adapter.clearSessionId();
  }

  function getSessionId(): string | null {
    return currentSessionId;
  }

  return { ensureSession, endSession, getSessionId };
}
