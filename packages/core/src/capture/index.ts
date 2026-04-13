import type { SupabaseClient } from "@supabase/supabase-js";
import type { CaptureHandle, Config, CaptureEvent } from "../types.js";
import { createSessionManager } from "./session.js";
import { createEventWriter } from "./writer.js";
import { createWebAdapter } from "./web-adapter.js";

/**
 * Orchestrator: initializes capture pipeline.
 * Coordinates adapter, session manager, and batched writer.
 * Defaults to web adapter when no adapter provided.
 */
export function initCapture(config: Config): CaptureHandle {
  const adapter = config.adapter ?? createWebAdapter();
  const platform = config.platform ?? "web";

  const sessionManager = createSessionManager(
    config.supabase,
    adapter,
    platform,
    config.sessionTimeoutMs,
  );
  const writer = createEventWriter(
    config.supabase,
    config.flushIntervalMs,
    config.flushBatchSize,
  );

  const enqueueEvent = createEventEnqueuer(
    config.supabase,
    sessionManager,
    writer,
  );

  adapter.startListening(enqueueEvent);
  writer.startAutoFlush();

  adapter.registerUnloadHandler(() => {
    writer.flush();
    sessionManager.endSession();
  });

  return {
    stopCapture: () => {
      adapter.stopListening();
      writer.stopAutoFlush();
    },
    flushEvents: () => writer.flush(),
    getSessionId: () => sessionManager.getSessionId(),
    trackNavigation: (componentPath, action) =>
      enqueueEvent({
        event_type: "navigation",
        component_path: componentPath,
        payload: { action },
      }),
    trackImpression: (componentPath) =>
      enqueueEvent({
        event_type: "impression",
        component_path: componentPath,
        payload: {},
      }),
  };
}

function createEventEnqueuer(
  supabase: SupabaseClient,
  sessionManager: ReturnType<typeof createSessionManager>,
  writer: ReturnType<typeof createEventWriter>,
): (event: CaptureEvent) => void {
  let cachedUserId: string | null = null;

  return (event: CaptureEvent) => {
    resolveUserId(supabase, cachedUserId).then((userId) => {
      cachedUserId = userId;
      return sessionManager.ensureSession(userId).then((sessionId) => {
        writer.enqueue(sessionId, userId, event);
      });
    }).catch((err) => {
      console.error("factoredui: failed to enqueue event:", err);
    });
  };
}

async function resolveUserId(
  supabase: SupabaseClient,
  cachedUserId: string | null,
): Promise<string> {
  if (cachedUserId) return cachedUserId;
  const { data: { user } } = await supabase.auth.getUser();
  if (!user) throw new Error("factoredui: user not authenticated");
  return user.id;
}
