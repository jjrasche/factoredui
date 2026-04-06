import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { createEventWriter } from "./writer.js";

function createMockSupabase(insertFn: ReturnType<typeof vi.fn>) {
  return {
    from: () => ({
      insert: insertFn,
    }),
  } as never;
}

describe("createEventWriter", () => {
  let insertMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    insertMock = vi.fn().mockResolvedValue({ error: null });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("batches events and flushes to supabase", async () => {
    const supabase = createMockSupabase(insertMock);
    const writer = createEventWriter(supabase, 5000, 50);

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
    const supabase = createMockSupabase(insertMock);
    const writer = createEventWriter(supabase, 60000, 3);

    writer.enqueue("s1", "u1", { event_type: "click", component_path: "a", payload: {} });
    writer.enqueue("s1", "u1", { event_type: "click", component_path: "b", payload: {} });

    expect(insertMock).not.toHaveBeenCalled();

    writer.enqueue("s1", "u1", { event_type: "click", component_path: "c", payload: {} });

    // flush is async — give it a tick
    await new Promise((r) => setTimeout(r, 10));
    expect(insertMock).toHaveBeenCalledOnce();
  });

  it("re-enqueues events on flush failure", async () => {
    insertMock.mockResolvedValueOnce({ error: { message: "network error" } });
    const supabase = createMockSupabase(insertMock);
    const writer = createEventWriter(supabase, 5000, 50);

    writer.enqueue("s1", "u1", { event_type: "click", component_path: "a", payload: {} });
    await writer.flush();

    // Event re-enqueued — second flush should retry
    insertMock.mockResolvedValueOnce({ error: null });
    await writer.flush();

    expect(insertMock).toHaveBeenCalledTimes(2);
    expect(insertMock.mock.calls[1][0]).toHaveLength(1);
  });

  it("does nothing when flushing an empty queue", async () => {
    const supabase = createMockSupabase(insertMock);
    const writer = createEventWriter(supabase, 5000, 50);

    await writer.flush();

    expect(insertMock).not.toHaveBeenCalled();
  });
});
