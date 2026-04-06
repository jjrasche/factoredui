import type { SupabaseClient } from "@supabase/supabase-js";
import type { ObserveEvent } from "../types.js";

const DEFAULT_FLUSH_INTERVAL_MS = 2000;
const DEFAULT_FLUSH_BATCH_SIZE = 50;

export interface EventWriter {
  enqueue: (sessionId: string, userId: string, event: ObserveEvent) => void;
  flush: () => Promise<void>;
  startAutoFlush: () => void;
  stopAutoFlush: () => void;
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
): EventWriter {
  let queue: QueuedEvent[] = [];
  let flushTimer: ReturnType<typeof setInterval> | null = null;
  let isFlushing = false;

  function enqueue(
    sessionId: string,
    userId: string,
    event: ObserveEvent,
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
        // Re-enqueue on failure — front of queue to preserve order
        queue.unshift(...batch);
        console.error("observe: flush failed:", error.message);
      }
    } catch (err) {
      queue.unshift(...batch);
      console.error("observe: flush error:", err);
    } finally {
      isFlushing = false;
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

  return { enqueue, flush, startAutoFlush, stopAutoFlush };
}
