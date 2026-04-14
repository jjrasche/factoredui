import { AppState, Platform, Dimensions } from "react-native";
import type { AppStateStatus } from "react-native";
import type { CaptureAdapter, CaptureEvent } from "@factoredui/core";

const SESSION_STORAGE_KEY = "factoredui:session_id";
const QUEUE_STORAGE_KEY = "factoredui:offline_queue";

interface AsyncStorageLike {
  getItem(key: string): Promise<string | null>;
  setItem(key: string, value: string): Promise<void>;
  removeItem(key: string): Promise<void>;
}

/**
 * React Native (Expo) implementation of CaptureAdapter.
 *
 * Uses AppState for lifecycle events, AsyncStorage for session persistence,
 * and Platform/Dimensions for device metadata.
 *
 * Factory is async because AsyncStorage.getItem is async but the
 * CaptureAdapter.loadSessionId contract is synchronous. The factory
 * preloads the cached session ID into memory at creation time.
 */
export async function createRnAdapter(
  asyncStorage: AsyncStorageLike,
): Promise<CaptureAdapter> {
  let cachedSessionId = await asyncStorage.getItem(SESSION_STORAGE_KEY);
  let cachedQueue = await asyncStorage.getItem(QUEUE_STORAGE_KEY);
  let appStateSubscription: ReturnType<typeof AppState.addEventListener> | null = null;
  let emitEvent: ((event: CaptureEvent) => void) | null = null;

  function startListening(onEvent: (event: CaptureEvent) => void): void {
    emitEvent = onEvent;

    appStateSubscription = AppState.addEventListener("change", (nextState: AppStateStatus) => {
      emitEvent?.({
        event_type: "visibility",
        component_path: "/",
        payload: { visibility_state: nextState },
      });
    });
  }

  function stopListening(): void {
    appStateSubscription?.remove();
    appStateSubscription = null;
    emitEvent = null;
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
    asyncStorage.setItem(SESSION_STORAGE_KEY, id).catch(() => {
      // Storage write failure — session lives in memory only
    });
  }

  function loadSessionId(): string | null {
    return cachedSessionId;
  }

  function clearSessionId(): void {
    cachedSessionId = null;
    asyncStorage.removeItem(SESSION_STORAGE_KEY).catch(() => {
      // Ignore storage errors
    });
  }

  function registerUnloadHandler(onUnload: () => void): void {
    AppState.addEventListener("change", (nextState: AppStateStatus) => {
      if (nextState === "background") onUnload();
    });
  }

  function persistQueue(serialized: string): void {
    cachedQueue = serialized || null;
    if (!serialized || serialized === "") {
      asyncStorage.removeItem(QUEUE_STORAGE_KEY).catch(() => {});
    } else {
      asyncStorage.setItem(QUEUE_STORAGE_KEY, serialized).catch(() => {});
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
