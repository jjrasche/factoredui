import { AppState, Platform, Dimensions } from "react-native";
import type { AppStateStatus } from "react-native";
import type { CaptureAdapter, CaptureEvent, KVStorage } from "@factoredui/core";

declare const ErrorUtils: {
  getGlobalHandler(): (error: Error, isFatal?: boolean) => void;
  setGlobalHandler(handler: (error: Error, isFatal?: boolean) => void): void;
};

const SESSION_STORAGE_KEY = "factoredui:session_id";
const QUEUE_STORAGE_KEY = "factoredui:offline_queue";

export interface RnAdapterOptions {
  storage: KVStorage;
  captureErrors?: boolean;
}

/**
 * React Native (Expo) implementation of CaptureAdapter.
 *
 * Uses AppState for lifecycle events, KVStorage for session persistence,
 * and Platform/Dimensions for device metadata.
 *
 * Factory is async because storage.getItem may be async but the
 * CaptureAdapter.loadSessionId contract is synchronous. The factory
 * preloads the cached session ID into memory at creation time.
 */
export async function createRnAdapter(
  options: RnAdapterOptions,
): Promise<CaptureAdapter> {
  const { storage, captureErrors = false } = options;
  let cachedSessionId = await storage.getItem(SESSION_STORAGE_KEY);
  let cachedQueue = await storage.getItem(QUEUE_STORAGE_KEY);
  let appStateSubscription: ReturnType<typeof AppState.addEventListener> | null = null;
  let emitEvent: ((event: CaptureEvent) => void) | null = null;
  let previousErrorHandler: ((error: Error, isFatal?: boolean) => void) | null = null;

  function startListening(onEvent: (event: CaptureEvent) => void): void {
    emitEvent = onEvent;

    appStateSubscription = AppState.addEventListener("change", (nextState: AppStateStatus) => {
      emitEvent?.({
        event_type: "visibility",
        component_path: "/",
        payload: { visibility_state: nextState },
      });
    });

    if (captureErrors) {
      previousErrorHandler = ErrorUtils.getGlobalHandler();
      ErrorUtils.setGlobalHandler((error: Error, isFatal?: boolean) => {
        emitEvent?.({
          event_type: "error",
          component_path: "/",
          payload: {
            error_message: error.message,
            error_stack: error.stack,
            is_fatal: isFatal ?? false,
          },
        });
        previousErrorHandler?.(error, isFatal);
      });
    }
  }

  function stopListening(): void {
    appStateSubscription?.remove();
    appStateSubscription = null;
    emitEvent = null;
    if (captureErrors && previousErrorHandler) {
      ErrorUtils.setGlobalHandler(previousErrorHandler);
      previousErrorHandler = null;
    }
  }

  function collectSessionMetadata(): Record<string, unknown> {
    const window = Dimensions.get("window");
    const screen = Dimensions.get("screen");
    return {
      platform: Platform.OS,
      platform_version: String(Platform.Version),
      screen_width: screen.width,
      screen_height: screen.height,
      window_width: window.width,
      window_height: window.height,
    };
  }

  function storeSessionId(id: string): void {
    cachedSessionId = id;
    Promise.resolve(storage.setItem(SESSION_STORAGE_KEY, id)).catch(() => {});
  }

  function loadSessionId(): string | null {
    return cachedSessionId;
  }

  function clearSessionId(): void {
    cachedSessionId = null;
    Promise.resolve(storage.removeItem(SESSION_STORAGE_KEY)).catch(() => {});
  }

  function registerUnloadHandler(onUnload: () => void): void {
    AppState.addEventListener("change", (nextState: AppStateStatus) => {
      if (nextState === "background") onUnload();
    });
  }

  function persistQueue(serialized: string): void {
    cachedQueue = serialized || null;
    if (!serialized || serialized === "") {
      Promise.resolve(storage.removeItem(QUEUE_STORAGE_KEY)).catch(() => {});
    } else {
      Promise.resolve(storage.setItem(QUEUE_STORAGE_KEY, serialized)).catch(() => {});
    }
  }

  function loadQueue(): string | null {
    return cachedQueue;
  }

  return {
    startListening,
    stopListening,
    collectSessionMetadata,
    storeSessionId,
    loadSessionId,
    clearSessionId,
    registerUnloadHandler,
    persistQueue,
    loadQueue,
  };
}
