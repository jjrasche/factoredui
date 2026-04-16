import { describe, it, expect, beforeAll, afterAll } from "vitest";
import {
  createServiceClient,
  createServiceStore,
  createTestUser,
  deleteTestUser,
} from "../../testing/supabase-harness.js";
import { queryExperimentResults } from "@factoredui/core";

describe("queryExperimentResults", () => {
  let serviceClient: ReturnType<typeof createServiceClient>;
  let store: ReturnType<typeof createServiceStore>;
  let userAId: string;
  let userBId: string;
  let experimentId: string;

  beforeAll(async () => {
    serviceClient = createServiceClient();
    store = createServiceStore();
    const userA = await createTestUser(serviceClient);
    const userB = await createTestUser(serviceClient);
    userAId = userA.id;
    userBId = userB.id;

    // Create experiment
    const { data: exp } = await serviceClient
      .from("experiments")
      .insert({
        name: "results-test",
        component_path: "test-results/page",
        status: "running",
      })
      .select("id")
      .single();
    experimentId = exp!.id;

    // Create variants
    await serviceClient.from("experiment_variants").insert([
      { experiment_id: experimentId, variant_key: "control", config: {}, traffic_percentage: 50 },
      { experiment_id: experimentId, variant_key: "treatment", config: {}, traffic_percentage: 50 },
    ]);

    // Assign users to variants
    await serviceClient.from("experiment_assignments").insert([
      { user_id: userAId, experiment_id: experimentId, variant_key: "control" },
      { user_id: userBId, experiment_id: experimentId, variant_key: "treatment" },
    ]);

    const experimentCreatedAt = new Date(Date.now() - 60 * 1000);

    // Seed factor snapshots: before experiment (error_rate)
    const beforeDate = new Date(experimentCreatedAt.getTime() - 1000);
    await serviceClient.from("factor_snapshots").insert([
      {
        user_id: userAId, component_path: "test-results/page",
        factor_name: "error_rate", factor_tier: "alarm",
        value: 0.3, computed_at: beforeDate.toISOString(), snapshot_at: beforeDate.toISOString(),
      },
      {
        user_id: userBId, component_path: "test-results/page",
        factor_name: "error_rate", factor_tier: "alarm",
        value: 0.3, computed_at: beforeDate.toISOString(), snapshot_at: beforeDate.toISOString(),
      },
    ]);

    // Seed factor snapshots: after experiment
    const afterDate = new Date();
    await serviceClient.from("factor_snapshots").insert([
      {
        user_id: userAId, component_path: "test-results/page",
        factor_name: "error_rate", factor_tier: "alarm",
        value: 0.25, computed_at: afterDate.toISOString(), snapshot_at: afterDate.toISOString(),
      },
      {
        user_id: userBId, component_path: "test-results/page",
        factor_name: "error_rate", factor_tier: "alarm",
        value: 0.1, computed_at: afterDate.toISOString(), snapshot_at: afterDate.toISOString(),
      },
    ]);
  });

  afterAll(async () => {
    for (const userId of [userAId, userBId]) {
      await serviceClient.from("factor_snapshots").delete().eq("user_id", userId);
    }
    await serviceClient.from("experiment_exposures").delete().eq("experiment_id", experimentId);
    await serviceClient.from("experiment_assignments").delete().eq("experiment_id", experimentId);
    await serviceClient.from("experiment_variants").delete().eq("experiment_id", experimentId);
    await serviceClient.from("experiments").delete().eq("id", experimentId);
    await deleteTestUser(serviceClient, userAId);
    await deleteTestUser(serviceClient, userBId);
  });

  it("returns per-variant factor deltas averaged across users", async () => {
    const results = await queryExperimentResults(
      store,
      experimentId,
      ["error_rate"],
    );

    expect(results).toHaveLength(2);

    const control = results.find((r) => r.variant_key === "control");
    const treatment = results.find((r) => r.variant_key === "treatment");

    expect(control).toBeDefined();
    expect(control!.user_count).toBe(1);
    expect(control!.factor_deltas).toHaveLength(1);
    // User A: 0.3 -> 0.25 = -0.05
    expect(control!.factor_deltas[0].before).toBeCloseTo(0.3, 2);
    expect(control!.factor_deltas[0].after).toBeCloseTo(0.25, 2);
    expect(control!.factor_deltas[0].delta).toBeCloseTo(-0.05, 2);

    expect(treatment).toBeDefined();
    expect(treatment!.user_count).toBe(1);
    expect(treatment!.factor_deltas).toHaveLength(1);
    // User B: 0.3 -> 0.1 = -0.2
    expect(treatment!.factor_deltas[0].before).toBeCloseTo(0.3, 2);
    expect(treatment!.factor_deltas[0].after).toBeCloseTo(0.1, 2);
    expect(treatment!.factor_deltas[0].delta).toBeCloseTo(-0.2, 2);
  });

  it("returns empty array for nonexistent experiment", async () => {
    const results = await queryExperimentResults(
      store,
      "00000000-0000-0000-0000-000000000000",
      ["error_rate"],
    );
    expect(results).toHaveLength(0);
  });
});
