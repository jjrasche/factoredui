import type { AuxiEvent } from "../types.js";

/**
 * Platform abstraction for capture infrastructure.
 * Web implements this with DOM APIs. React Native implements with RN/Expo APIs.
 * The adapter lives with its platform — auxi only defines the contract.
 */

export interface CaptureAdapter {
  startListening(onEvent: (event: AuxiEvent) => void): void;
  stopListening(): void;
  collectSessionMetadata(): Record<string, unknown>;
  storeSessionId(id: string): void;
  loadSessionId(): string | null;
  clearSessionId(): void;
  registerUnloadHandler(onUnload: () => void): void;
}
