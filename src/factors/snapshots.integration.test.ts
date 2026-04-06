import { describe, it, expect, beforeAll, afterAll } from "vitest";
import {
  createServiceClient,
  createTestUser,
  deleteTestUser,
} from "../testing/supabase-harness.js";
import { queryFactorHistory, queryFactorDelta } from "./snapshots.js";

describe("factor snapshot queries", () => {
  let serviceClient: ReturnType<typeof createServiceClient>;
  let testUserId: string;
  const componentPath = "test-snapshots/dashboard";

  beforeAll(async () => {
    serviceClient = createServiceClient();
    const user = await createTestUser(serviceClient);
    testUserId = user.id;

    const now = new Date();
    const dayAgo = new Date(now.getTime() - 24 * 60 * 60 * 1000);
    const twoDaysAgo = new Date(now.getTime() - 2 * 24 * 60 * 60 * 1000);

    const snapshots = [
      {
        user_id: testUserId,
        component_path: componentPath,
        factor_name: "error_rate",
        factor_tier: "alarm",
        value: 0.1,
        computed_at: twoDaysAgo.toISOString(),
        snapshot_at: twoDaysAgo.toISOString(),
      },
      {
        user_id: testUserId,
        component_path: componentPath,
        factor_name: "error_rate",
        factor_tier: "alarm",
        value: 0.25,
        computed_at: dayAgo.toISOString(),
        snapshot_at: dayAgo.toISOString(),
      },
      {
        user_id: testUserId,
        component_path: componentPath,
        factor_name: "error_rate",
        factor_tier: "alarm",
        value: 0.05,
        computed_at: now.toISOString(),
        snapshot_at: now.toISOString(),
      },
    ];

    const { error } = await serviceClient
      .from("factor_snapshots")
      .insert(snapshots);

    if (error) throw new Error(`Snapshot insert failed: ${error.message}`);
  });

  afterAll(async () => {
    await serviceClient
      .from("factor_snapshots")
      .delete()
      .eq("user_id", testUserId);
    await deleteTestUser(serviceClient, testUserId);
  });

  it("returns factor history in ascending order since a given date", async () => {
    const threeDaysAgo = new Date(Date.now() - 3 * 24 * 60 * 60 * 1000);

    const history = await queryFactorHistory(
      serviceClient,
      testUserId,
      componentPath,
      "error_rate",
      threeDaysAgo,
    );

    expect(history).toHaveLength(3);
    expect(history[0].value).toBeCloseTo(0.1, 2);
    expect(history[1].value).toBeCloseTo(0.25, 2);
    expect(history[2].value).toBeCloseTo(0.05, 2);
  });

  it("filters history by since date", async () => {
    const thirtyHoursAgo = new Date(Date.now() - 30 * 60 * 60 * 1000);

    const history = await queryFactorHistory(
      serviceClient,
      testUserId,
      componentPath,
      "error_rate",
      thirtyHoursAgo,
    );

    expect(history).toHaveLength(2);
    expect(history[0].value).toBeCloseTo(0.25, 2);
  });

  it("computes delta between two points in time", async () => {
    // 36h ago falls between twoDaysAgo (0.1) and dayAgo (0.25) snapshots
    const thirtyHoursAgo = new Date(Date.now() - 36 * 60 * 60 * 1000);
    const now = new Date();

    const delta = await queryFactorDelta(
      serviceClient,
      testUserId,
      componentPath,
      "error_rate",
      thirtyHoursAgo,
      now,
    );

    expect(delta).not.toBeNull();
    // before: closest snapshot <= 36h ago is the twoDaysAgo snapshot (0.1)
    expect(delta!.before).toBeCloseTo(0.1, 2);
    // after: closest snapshot <= now is the current snapshot (0.05)
    expect(delta!.after).toBeCloseTo(0.05, 2);
    expect(delta!.delta).toBeCloseTo(-0.05, 2);
  });

  it("returns null delta when no snapshots exist before the range", async () => {
    const farPast = new Date("2020-01-01T00:00:00Z");

    const delta = await queryFactorDelta(
      serviceClient,
      testUserId,
      componentPath,
      "error_rate",
      farPast,
      farPast,
    );

    expect(delta).toBeNull();
  });
});
