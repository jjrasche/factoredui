import { describe, it, expect, vi, beforeEach } from "vitest";
import { createRnAdapter } from "./rn-adapter.js";
import { AppState, ErrorUtils } from "react-native";
import type { CaptureEvent } from "@factoredui/core";

function createMockStorage(initial: Record<string, string> = {}) {
  const store = new Map(Object.entries(initial));
  return {
    getItem: vi.fn((key: string) => Promise.resolve(store.get(key) ?? null)),
    setItem: vi.fn((key: string, value: string) => { store.set(key, value); return Promise.resolve(); }),
    removeItem: vi.fn((key: string) => { store.delete(key); return Promise.resolve(); }),
  };
}

describe("createRnAdapter", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (ErrorUtils as unknown as { _reset: () => void })._reset();
  });

  it("preloads session ID from AsyncStorage", async () => {
    const storage = createMockStorage({ "factoredui:session_id": "existing-session" });
    const adapter = await createRnAdapter({ storage });

    expect(adapter.loadSessionId()).toBe("existing-session");
    expect(storage.getItem).toHaveBeenCalledWith("factoredui:session_id");
  });

  it("returns null when no stored session ID", async () => {
    const storage = createMockStorage();
    const adapter = await createRnAdapter({ storage });

    expect(adapter.loadSessionId()).toBeNull();
  });

  it("stores session ID to memory and AsyncStorage", async () => {
    const storage = createMockStorage();
    const adapter = await createRnAdapter({ storage });

    adapter.storeSessionId("new-session-456");

    expect(adapter.loadSessionId()).toBe("new-session-456");
    expect(storage.setItem).toHaveBeenCalledWith("factoredui:session_id", "new-session-456");
  });

  it("clears session ID from memory and AsyncStorage", async () => {
    const storage = createMockStorage({ "factoredui:session_id": "to-clear" });
    const adapter = await createRnAdapter({ storage });

    adapter.clearSessionId();

    expect(adapter.loadSessionId()).toBeNull();
    expect(storage.removeItem).toHaveBeenCalledWith("factoredui:session_id");
  });

  it("collects device metadata from Platform and Dimensions", async () => {
    const storage = createMockStorage();
    const adapter = await createRnAdapter({ storage });

    const metadata = adapter.collectSessionMetadata();

    expect(metadata).toEqual({
      platform: "ios",
      platform_version: "18.0",
      screen_width: 375,
      screen_height: 812,
      window_width: 375,
      window_height: 812,
    });
  });

  it("emits visibility events on AppState changes", async () => {
    const storage = createMockStorage();
    const adapter = await createRnAdapter({ storage });
    const events: CaptureEvent[] = [];

    adapter.startListening((event) => events.push(event));

    (AppState as unknown as { _fireChange: (s: string) => void })._fireChange("background");

    expect(events).toHaveLength(1);
    expect(events[0].event_type).toBe("visibility");
    expect(events[0].payload.visibility_state).toBe("background");

    adapter.stopListening();
  });

  it("preloads persisted queue from AsyncStorage", async () => {
    const queueData = JSON.stringify([{ event_type: "click" }]);
    const storage = createMockStorage({ "factoredui:offline_queue": queueData });
    const adapter = await createRnAdapter({ storage });

    expect(adapter.loadQueue!()).toBe(queueData);
  });

  it("persists queue to AsyncStorage and updates cache", async () => {
    const storage = createMockStorage();
    const adapter = await createRnAdapter({ storage });

    const serialized = JSON.stringify([{ event_type: "scroll" }]);
    adapter.persistQueue!(serialized);

    expect(adapter.loadQueue!()).toBe(serialized);
    expect(storage.setItem).toHaveBeenCalledWith("factoredui:offline_queue", serialized);
  });

  it("clears persisted queue when given empty string", async () => {
    const storage = createMockStorage({ "factoredui:offline_queue": "[{}]" });
    const adapter = await createRnAdapter({ storage });

    adapter.persistQueue!("");

    expect(adapter.loadQueue!()).toBeNull();
    expect(storage.removeItem).toHaveBeenCalledWith("factoredui:offline_queue");
  });

  it("stops emitting after stopListening", async () => {
    const storage = createMockStorage();
    const adapter = await createRnAdapter({ storage });
    const events: CaptureEvent[] = [];

    adapter.startListening((event) => events.push(event));
    adapter.stopListening();

    (AppState as unknown as { _fireChange: (s: string) => void })._fireChange("active");

    expect(events).toHaveLength(0);
  });

  it("emits error events when captureErrors is true", async () => {
    const storage = createMockStorage();
    const adapter = await createRnAdapter({ storage, captureErrors: true });
    const events: CaptureEvent[] = [];

    adapter.startListening((event) => events.push(event));

    const testError = new Error("test crash");
    (ErrorUtils as unknown as { _simulateError: (e: Error, f?: boolean) => void })
      ._simulateError(testError, true);

    expect(events).toHaveLength(1);
    expect(events[0].event_type).toBe("error");
    expect(events[0].payload.error_message).toBe("test crash");
    expect(events[0].payload.is_fatal).toBe(true);

    adapter.stopListening();
  });

  it("chains to previous error handler when captureErrors is true", async () => {
    const previousHandler = vi.fn();
    (ErrorUtils as unknown as { setGlobalHandler: (h: Function) => void })
      .setGlobalHandler(previousHandler);

    const storage = createMockStorage();
    const adapter = await createRnAdapter({ storage, captureErrors: true });
    const events: CaptureEvent[] = [];

    adapter.startListening((event) => events.push(event));

    const testError = new Error("chained error");
    (ErrorUtils as unknown as { _simulateError: (e: Error, f?: boolean) => void })
      ._simulateError(testError, false);

    expect(events).toHaveLength(1);
    expect(previousHandler).toHaveBeenCalledWith(testError, false);

    adapter.stopListening();
  });

  it("restores previous error handler on stopListening", async () => {
    const originalHandler = vi.fn();
    (ErrorUtils as unknown as { setGlobalHandler: (h: Function) => void })
      .setGlobalHandler(originalHandler);

    const storage = createMockStorage();
    const adapter = await createRnAdapter({ storage, captureErrors: true });

    adapter.startListening(() => {});
    adapter.stopListening();

    const currentHandler = (ErrorUtils as unknown as { getGlobalHandler: () => Function })
      .getGlobalHandler();
    expect(currentHandler).toBe(originalHandler);
  });
});
