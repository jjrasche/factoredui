import type { CaptureEvent } from "../types.js";

/**
 * Platform abstraction for capture infrastructure.
 * Web implements this with DOM APIs. React Native implements with RN/Expo APIs.
 * The adapter lives with its platform — factoredui only defines the contract.
 */

export interface CaptureAdapter {
  startListening(onEvent: (event: CaptureEvent) => void): void;
  stopListening(): void;
  collectSessionMetadata(): Record<string, unknown>;
  storeSessionId(id: string): void;
  loadSessionId(): string | null;
  clearSessionId(): void;
  registerUnloadHandler(onUnload: () => void): void;
  /** Persist queued events to survive app restarts. JSON string of event array. */
  persistQueue?(serialized: string): void;
  /** Load previously persisted queue. Returns null if empty. */
  loadQueue?(): string | null;
}
