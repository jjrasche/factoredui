import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { createEventWriter } from "./writer.js";
import type { FactoredStore } from "../store.js";

function createMockStore(insertEvents: ReturnType<typeof vi.fn>): FactoredStore {
  return { insertEvents } as unknown as FactoredStore;
}

describe("createEventWriter", () => {
  let insertMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    insertMock = vi.fn().mockResolvedValue(undefined);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("batches events and flushes to store", async () => {
    const store = createMockStore(insertMock);
    const writer = createEventWriter(store, 5000, 50);

    writer.enqueue("session-1", "user-1", {
      event_type: "click",
      component_path: "page/button",
      payload: { x: 10 },
    });
    writer.enqueue("session-1", "user-1", {
      event_type: "scroll",
      component_path: "page",
      payload: { scroll_y: 100 },
    });

    await writer.flush();

    expect(insertMock).toHaveBeenCalledOnce();
    const batch = insertMock.mock.calls[0][0];
    expect(batch).toHaveLength(2);
    expect(batch[0].event_type).toBe("click");
    expect(batch[1].event_type).toBe("scroll");
  });

  it("auto-flushes when batch size reached", async () => {
    const store = createMockStore(insertMock);
    const writer = createEventWriter(store, 60000, 3);

    writer.enqueue("s1", "u1", { event_type: "click", component_path: "a", payload: {} });
    writer.enqueue("s1", "u1", { event_type: "click", component_path: "b", payload: {} });

    expect(insertMock).not.toHaveBeenCalled();

    writer.enqueue("s1", "u1", { event_type: "click", component_path: "c", payload: {} });

    // flush is async — give it a tick
    await new Promise((r) => setTimeout(r, 10));
    expect(insertMock).toHaveBeenCalledOnce();
  });

  it("re-enqueues events on flush failure", async () => {
    insertMock.mockRejectedValueOnce(new Error("network error"));
    const store = createMockStore(insertMock);
    const writer = createEventWriter(store, 5000, 50);

    writer.enqueue("s1", "u1", { event_type: "click", component_path: "a", payload: {} });
    await writer.flush();

    // Event re-enqueued — second flush should retry
    insertMock.mockResolvedValueOnce(undefined);
    await writer.flush();

    expect(insertMock).toHaveBeenCalledTimes(2);
    expect(insertMock.mock.calls[1][0]).toHaveLength(1);
  });

  it("does nothing when flushing an empty queue", async () => {
    const store = createMockStore(insertMock);
    const writer = createEventWriter(store, 5000, 50);

    await writer.flush();

    expect(insertMock).not.toHaveBeenCalled();
  });

  it("persists queue to adapter on flush failure", async () => {
    insertMock.mockRejectedValueOnce(new Error("offline"));
    const store = createMockStore(insertMock);
    const persistQueue = vi.fn();
    const adapter = { persistQueue, loadQueue: vi.fn() } as never;
    const writer = createEventWriter(store, 5000, 50, adapter);

    writer.enqueue("s1", "u1", { event_type: "click", component_path: "a", payload: {} });
    await writer.flush();

    expect(persistQueue).toHaveBeenCalledOnce();
    const serialized = JSON.parse(persistQueue.mock.calls[0][0]);
    expect(serialized).toHaveLength(1);
    expect(serialized[0].event_type).toBe("click");
  });

  it("drains persisted queue into memory on startup", async () => {
    const store = createMockStore(insertMock);
    const persistedEvents = [
      { user_id: "u1", session_id: "s1", event_type: "click", component_path: "a", payload: {} },
    ];
    const persistQueue = vi.fn();
    const loadQueue = vi.fn().mockReturnValue(JSON.stringify(persistedEvents));
    const adapter = { persistQueue, loadQueue } as never;
    const writer = createEventWriter(store, 5000, 50, adapter);

    writer.drainPersistedQueue();
    await writer.flush();

    expect(loadQueue).toHaveBeenCalledOnce();
    expect(insertMock).toHaveBeenCalledOnce();
    expect(insertMock.mock.calls[0][0]).toHaveLength(1);
    expect(insertMock.mock.calls[0][0][0].event_type).toBe("click");
    // Persisted queue cleared after drain
    expect(persistQueue).toHaveBeenCalledWith("");
  });

  it("ignores corrupt persisted queue data", async () => {
    const store = createMockStore(insertMock);
    const loadQueue = vi.fn().mockReturnValue("not-valid-json{{{");
    const adapter = { loadQueue } as never;
    const writer = createEventWriter(store, 5000, 50, adapter);

    // Should not throw
    writer.drainPersistedQueue();
    await writer.flush();

    expect(insertMock).not.toHaveBeenCalled();
  });
});
