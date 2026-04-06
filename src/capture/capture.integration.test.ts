import { describe, it, expect, beforeAll, afterAll } from "vitest";
import {
  createServiceClient,
  createTestUser,
  deleteTestUser,
} from "../testing/supabase-harness.js";
import { createEventWriter } from "./writer.js";
import { createSessionManager } from "./session.js";

describe("capture integration", () => {
  let serviceClient: ReturnType<typeof createServiceClient>;
  let testUserId: string;

  beforeAll(async () => {
    serviceClient = createServiceClient();
    const user = await createTestUser(serviceClient);
    testUserId = user.id;
  });

  afterAll(async () => {
    await serviceClient
      .from("events")
      .delete()
      .eq("user_id", testUserId);
    await serviceClient
      .from("sessions")
      .delete()
      .eq("user_id", testUserId);
    await deleteTestUser(serviceClient, testUserId);
  });

  describe("event writer", () => {
    let writerSessionId: string;

    beforeAll(async () => {
      const { data, error } = await serviceClient
        .from("sessions")
        .insert({ user_id: testUserId, metadata: {} })
        .select("id")
        .single();

      if (error) throw new Error(`Failed to create writer session: ${error.message}`);
      writerSessionId = data!.id;
    });

    it("flushes batched events to observe.events", async () => {
      const writer = createEventWriter(serviceClient, 5000, 50);

      writer.enqueue(writerSessionId, testUserId, {
        event_type: "navigation",
        component_path: "test-page/hero",
        payload: { action: "mount" },
      });
      writer.enqueue(writerSessionId, testUserId, {
        event_type: "impression",
        component_path: "test-page/hero/cta",
        payload: {},
      });

      await writer.flush();

      const { data, error } = await serviceClient
        .from("events")
        .select("event_type, component_path, payload")
        .eq("user_id", testUserId)
        .order("created_at");

      expect(error).toBeNull();
      expect(data).toHaveLength(2);
      expect(data![0].event_type).toBe("navigation");
      expect(data![0].component_path).toBe("test-page/hero");
      expect(data![0].payload).toEqual({ action: "mount" });
      expect(data![1].event_type).toBe("impression");
      expect(data![1].component_path).toBe("test-page/hero/cta");
    });
  });

  describe("session lifecycle", () => {
    it("creates a session row and sets ended_at on end", async () => {
      const sessionManager = createSessionManager(serviceClient);

      const sessionId = await sessionManager.ensureSession(testUserId);
      expect(sessionId).toBeTruthy();

      const { data: activeSession } = await serviceClient
        .from("sessions")
        .select("id, user_id, ended_at, metadata")
        .eq("id", sessionId)
        .single();

      expect(activeSession).not.toBeNull();
      expect(activeSession!.user_id).toBe(testUserId);
      expect(activeSession!.ended_at).toBeNull();
      expect(activeSession!.metadata).toBeDefined();

      await sessionManager.endSession();

      const { data: endedSession } = await serviceClient
        .from("sessions")
        .select("ended_at")
        .eq("id", sessionId)
        .single();

      expect(endedSession!.ended_at).not.toBeNull();
    });

    it("reuses session within timeout window", async () => {
      const sessionManager = createSessionManager(serviceClient);

      const firstId = await sessionManager.ensureSession(testUserId);
      const secondId = await sessionManager.ensureSession(testUserId);

      expect(secondId).toBe(firstId);

      await sessionManager.endSession();
    });
  });
});
