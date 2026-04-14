import type { SupabaseClient } from "@supabase/supabase-js";
import type { CaptureEvent } from "../types.js";
import type { CaptureAdapter } from "./adapter.js";

const DEFAULT_FLUSH_INTERVAL_MS = 2000;
const DEFAULT_FLUSH_BATCH_SIZE = 50;

export interface EventWriter {
  enqueue: (sessionId: string, userId: string, event: CaptureEvent) => void;
  flush: () => Promise<void>;
  startAutoFlush: () => void;
  stopAutoFlush: () => void;
  drainPersistedQueue: () => void;
}

interface QueuedEvent {
  user_id: string;
  session_id: string;
  event_type: string;
  component_path: string;
  payload: Record<string, unknown>;
}

export function createEventWriter(
  supabase: SupabaseClient,
  flushIntervalMs: number = DEFAULT_FLUSH_INTERVAL_MS,
  flushBatchSize: number = DEFAULT_FLUSH_BATCH_SIZE,
  adapter?: CaptureAdapter,
): EventWriter {
  let queue: QueuedEvent[] = [];
  let flushTimer: ReturnType<typeof setInterval> | null = null;
  let isFlushing = false;

  function enqueue(
    sessionId: string,
    userId: string,
    event: CaptureEvent,
  ): void {
    queue.push({
      user_id: userId,
      session_id: sessionId,
      event_type: event.event_type,
      component_path: event.component_path,
      payload: event.payload,
    });

    if (queue.length >= flushBatchSize) {
      flush();
    }
  }

  async function flush(): Promise<void> {
    if (isFlushing || queue.length === 0) return;

    isFlushing = true;
    const batch = queue.splice(0, flushBatchSize);

    try {
      const { error } = await supabase.from("events").insert(batch);
      if (error) {
        queue.unshift(...batch);
        persistQueueToAdapter();
        console.error("factoredui: flush failed:", error.message);
      }
    } catch (err) {
      queue.unshift(...batch);
      persistQueueToAdapter();
      console.error("factoredui: flush error:", err);
    } finally {
      isFlushing = false;
    }
  }

  function persistQueueToAdapter(): void {
    if (!adapter?.persistQueue || queue.length === 0) return;
    try {
      adapter.persistQueue(JSON.stringify(queue));
    } catch {
      // Storage full or unavailable — events stay in memory only
    }
  }

  function drainPersistedQueue(): void {
    if (!adapter?.loadQueue) return;
    try {
      const serialized = adapter.loadQueue();
      if (!serialized) return;

      const persisted = JSON.parse(serialized) as QueuedEvent[];
      if (persisted.length > 0) {
        queue.unshift(...persisted);
        // Clear persisted queue now that events are in memory
        adapter.persistQueue?.("");
      }
    } catch {
      // Corrupt data — discard
    }
  }

  function startAutoFlush(): void {
    if (flushTimer) return;
    flushTimer = setInterval(flush, flushIntervalMs);
  }

  function stopAutoFlush(): void {
    if (flushTimer) {
      clearInterval(flushTimer);
      flushTimer = null;
    }
  }

  return { enqueue, flush, startAutoFlush, stopAutoFlush, drainPersistedQueue };
}
