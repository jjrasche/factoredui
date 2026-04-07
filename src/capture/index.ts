import type { SupabaseClient } from "@supabase/supabase-js";
import type { CaptureHandle, AuxiConfig, AuxiEvent } from "../types.js";
import { createSessionManager } from "./session.js";
import { createEventListener } from "./listener.js";
import { createBehavioralDetector } from "./behavioral.js";
import { createEventWriter } from "./writer.js";

/**
 * Orchestrator: initializes capture pipeline.
 * Coordinates session, listeners, behavioral detectors, and batched writer.
 */
export function initCapture(config: AuxiConfig): CaptureHandle {
  const sessionManager = createSessionManager(
    config.supabase,
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

  const listener = createEventListener(enqueueEvent);
  const behavioral = createBehavioralDetector(enqueueEvent);

  startCapturePipeline(listener, behavioral, writer);
  registerPageUnloadFlush(writer, sessionManager);

  return {
    stopCapture: () => stopCapturePipeline(listener, behavioral, writer),
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
): (event: AuxiEvent) => void {
  let cachedUserId: string | null = null;

  return (event: AuxiEvent) => {
    resolveUserId(supabase, cachedUserId).then((userId) => {
      cachedUserId = userId;
      return sessionManager.ensureSession(userId).then((sessionId) => {
        writer.enqueue(sessionId, userId, event);
      });
    }).catch((err) => {
      console.error("auxi: failed to enqueue event:", err);
    });
  };
}

async function resolveUserId(
  supabase: SupabaseClient,
  cachedUserId: string | null,
): Promise<string> {
  if (cachedUserId) return cachedUserId;
  const { data: { user } } = await supabase.auth.getUser();
  if (!user) throw new Error("auxi: user not authenticated");
  return user.id;
}

function startCapturePipeline(
  listener: ReturnType<typeof createEventListener>,
  behavioral: ReturnType<typeof createBehavioralDetector>,
  writer: ReturnType<typeof createEventWriter>,
): void {
  listener.startListening();
  behavioral.startDetecting();
  writer.startAutoFlush();
}

function stopCapturePipeline(
  listener: ReturnType<typeof createEventListener>,
  behavioral: ReturnType<typeof createBehavioralDetector>,
  writer: ReturnType<typeof createEventWriter>,
): void {
  listener.stopListening();
  behavioral.stopDetecting();
  writer.stopAutoFlush();
}

function registerPageUnloadFlush(
  writer: ReturnType<typeof createEventWriter>,
  sessionManager: ReturnType<typeof createSessionManager>,
): void {
  if (typeof window === "undefined") return;

  window.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "hidden") {
      writer.flush();
      sessionManager.endSession();
    }
  });
}
