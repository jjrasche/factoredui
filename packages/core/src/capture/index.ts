import type { FactoredStore } from "../store.js";
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
    config.store,
    adapter,
    platform,
    config.sessionTimeoutMs,
  );
  const writer = createEventWriter(
    config.store,
    config.flushIntervalMs,
    config.flushBatchSize,
  );

  const enqueueEvent = createEventEnqueuer(
    config.store,
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
  store: FactoredStore,
  sessionManager: ReturnType<typeof createSessionManager>,
  writer: ReturnType<typeof createEventWriter>,
): (event: CaptureEvent) => void {
  let cachedUserId: string | null = null;

  return (event: CaptureEvent) => {
    resolveUserId(store, cachedUserId).then((userId) => {
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
  store: FactoredStore,
  cachedUserId: string | null,
): Promise<string> {
  if (cachedUserId) return cachedUserId;
  const userId = await store.getCurrentUserId();
  if (!userId) throw new Error("factoredui: user not authenticated");
  return userId;
}
