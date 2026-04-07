import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { createServiceClient } from "../testing/supabase-harness.js";
import type { GovernanceLogRow } from "../experiment/governance-log.js";
import type { RealtimeChannel } from "@supabase/supabase-js";

/**
 * Realtime integration tests — requires `npx supabase start` with Realtime enabled.
 *
 * These test the subscription patterns used by React hooks against a live
 * Supabase Realtime instance, verifying that INSERT events are delivered
 * and payloads match the expected shape.
 */
describe("realtime subscriptions integration", () => {
  let serviceClient: ReturnType<typeof createServiceClient>;
  let experimentId: string;
  const channelsToCleanup: RealtimeChannel[] = [];

  beforeAll(async () => {
    serviceClient = createServiceClient();

    const { data: exp } = await serviceClient
      .from("experiments")
      .insert({
        name: "realtime-integration-test",
        component_path: "test-realtime/page",
        status: "running",
      })
      .select("id")
      .single();
    experimentId = exp!.id;
  });

  afterAll(async () => {
    for (const channel of channelsToCleanup) {
      await serviceClient.removeChannel(channel);
    }
    await serviceClient
      .from("governance_log")
      .delete()
      .eq("experiment_id", experimentId);
    await serviceClient
      .from("experiments")
      .delete()
      .eq("id", experimentId);
  });

  it("receives governance_log INSERT filtered by experiment_id", async () => {
    const received: GovernanceLogRow[] = [];

    const channel = serviceClient
      .channel(`test-gov-log:${experimentId}`)
      .on(
        "postgres_changes",
        {
          event: "INSERT",
          schema: "auxi",
          table: "governance_log",
          filter: `experiment_id=eq.${experimentId}`,
        },
        (payload) => received.push(payload.new as GovernanceLogRow),
      );

    channelsToCleanup.push(channel);

    await subscribeAndWait(channel);

    await serviceClient.from("governance_log").insert({
      experiment_id: experimentId,
      verdict: "continue",
      winning_variant: null,
      factor_verdicts: [
        {
          factor_name: "scroll_depth",
          best_variant: "treatment",
          best_delta: 0.15,
          control_delta: 0.05,
          is_significant: false,
        },
      ],
    });

    await waitFor(() => received.length >= 1, 5000);

    expect(received).toHaveLength(1);
    expect(received[0].experiment_id).toBe(experimentId);
    expect(received[0].verdict).toBe("continue");
    expect(received[0].factor_verdicts).toHaveLength(1);
  });

  it("receives unfiltered governance_log INSERTs across experiments", async () => {
    const received: GovernanceLogRow[] = [];

    const channel = serviceClient
      .channel("test-gov-log:all")
      .on(
        "postgres_changes",
        {
          event: "INSERT",
          schema: "auxi",
          table: "governance_log",
        },
        (payload) => received.push(payload.new as GovernanceLogRow),
      );

    channelsToCleanup.push(channel);

    await subscribeAndWait(channel);

    await serviceClient.from("governance_log").insert({
      experiment_id: experimentId,
      verdict: "flag_review",
      winning_variant: null,
      factor_verdicts: [],
    });

    await waitFor(() => received.length >= 1, 5000);

    expect(received.length).toBeGreaterThanOrEqual(1);
    const ours = received.find((r) => r.experiment_id === experimentId);
    expect(ours).toBeDefined();
    expect(ours!.verdict).toBe("flag_review");
  });

  it("receives experiments table changes for dashboard refetch pattern", async () => {
    const received: unknown[] = [];

    // The dashboard hook subscribes to experiment changes to trigger a refetch.
    // Use INSERT (not UPDATE) — Supabase local Realtime delivers INSERTs
    // reliably; UPDATE delivery depends on replication slot timing.
    const channel = serviceClient
      .channel("test-experiments:changes")
      .on(
        "postgres_changes",
        {
          event: "INSERT",
          schema: "auxi",
          table: "experiments",
        },
        (payload) => received.push(payload),
      );

    channelsToCleanup.push(channel);

    await subscribeAndWait(channel);

    const { data: tempExp } = await serviceClient
      .from("experiments")
      .insert({
        name: "realtime-dashboard-test",
        component_path: "test-realtime/dashboard",
        status: "running",
      })
      .select("id")
      .single();

    await waitFor(() => received.length >= 1, 5000);

    expect(received.length).toBeGreaterThanOrEqual(1);

    // Cleanup
    if (tempExp) {
      await serviceClient.from("experiments").delete().eq("id", tempExp.id);
    }
  });

  it("filtered subscription ignores inserts for other experiments", async () => {
    // Create a second experiment to insert against
    const { data: otherExp } = await serviceClient
      .from("experiments")
      .insert({
        name: "realtime-other-exp",
        component_path: "test-realtime/other",
        status: "running",
      })
      .select("id")
      .single();
    const otherExperimentId = otherExp!.id;

    const received: GovernanceLogRow[] = [];

    const channel = serviceClient
      .channel(`test-gov-log-filtered:${experimentId}`)
      .on(
        "postgres_changes",
        {
          event: "INSERT",
          schema: "auxi",
          table: "governance_log",
          filter: `experiment_id=eq.${experimentId}`,
        },
        (payload) => received.push(payload.new as GovernanceLogRow),
      );

    channelsToCleanup.push(channel);

    await subscribeAndWait(channel);

    // Insert for the OTHER experiment — should NOT trigger our filtered subscription
    await serviceClient.from("governance_log").insert({
      experiment_id: otherExperimentId,
      verdict: "continue",
      winning_variant: null,
      factor_verdicts: [],
    });

    // Give realtime a chance to deliver (if it were going to)
    await delay(1500);

    expect(received).toHaveLength(0);

    // Cleanup
    await serviceClient
      .from("governance_log")
      .delete()
      .eq("experiment_id", otherExperimentId);
    await serviceClient
      .from("experiments")
      .delete()
      .eq("id", otherExperimentId);
  });
});

// --- Helpers ---

function subscribeAndWait(channel: RealtimeChannel): Promise<void> {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(
      () => reject(new Error("Channel did not reach SUBSCRIBED within 5s")),
      5000,
    );

    channel.subscribe((status) => {
      if (status === "SUBSCRIBED") {
        clearTimeout(timeout);
        resolve();
      }
    });
  });
}

function waitFor(
  predicate: () => boolean,
  timeoutMs: number,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(
      () => reject(new Error(`waitFor timed out after ${timeoutMs}ms`)),
      timeoutMs,
    );

    const check = () => {
      if (predicate()) {
        clearTimeout(timeout);
        resolve();
      } else {
        setTimeout(check, 100);
      }
    };
    check();
  });
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
