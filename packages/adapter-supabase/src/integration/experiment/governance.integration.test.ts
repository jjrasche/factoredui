import { describe, it, expect, beforeAll, afterAll } from "vitest";
import {
  createServiceClient,
  createServiceStore,
  createTestUser,
  deleteTestUser,
} from "../../testing/supabase-harness.js";
import { evaluateExperimentThresholds, concludeExperiment } from "@factoredui/core";

describe("governance integration", () => {
  let serviceClient: ReturnType<typeof createServiceClient>;
  let store: ReturnType<typeof createServiceStore>;
  let userAId: string;
  let userBId: string;
  let governorId: string;
  let experimentId: string;
  let thresholdId: string;

  beforeAll(async () => {
    serviceClient = createServiceClient();
    store = createServiceStore();
    const userA = await createTestUser(serviceClient);
    const userB = await createTestUser(serviceClient);
    const governor = await createTestUser(serviceClient);
    userAId = userA.id;
    userBId = userB.id;
    governorId = governor.id;

    // Create experiment
    const { data: exp } = await serviceClient
      .from("experiments")
      .insert({
        name: "governance-test",
        component_path: "test-gov/page",
        status: "running",
      })
      .select("id")
      .single();
    experimentId = exp!.id;

    // Create variants
    await serviceClient.from("experiment_variants").insert([
      { experiment_id: experimentId, variant_key: "control", config: {}, traffic_percentage: 50 },
      { experiment_id: experimentId, variant_key: "treatment", config: { layout: "new" }, traffic_percentage: 50 },
    ]);

    // Assign users
    await serviceClient.from("experiment_assignments").insert([
      { user_id: userAId, experiment_id: experimentId, variant_key: "control" },
      { user_id: userBId, experiment_id: experimentId, variant_key: "treatment" },
    ]);

    // Seed factor snapshots: before experiment
    const experimentCreatedAt = new Date(Date.now() - 60 * 1000);
    const beforeDate = new Date(experimentCreatedAt.getTime() - 1000);
    const afterDate = new Date();

    await serviceClient.from("factor_snapshots").insert([
      // Before: both users start at error_rate 0.3
      {
        user_id: userAId, component_path: "test-gov/page",
        factor_name: "error_rate", factor_tier: "alarm",
        value: 0.3, computed_at: beforeDate.toISOString(), snapshot_at: beforeDate.toISOString(),
      },
      {
        user_id: userBId, component_path: "test-gov/page",
        factor_name: "error_rate", factor_tier: "alarm",
        value: 0.3, computed_at: beforeDate.toISOString(), snapshot_at: beforeDate.toISOString(),
      },
      // After: control barely improves, treatment improves significantly
      {
        user_id: userAId, component_path: "test-gov/page",
        factor_name: "error_rate", factor_tier: "alarm",
        value: 0.28, computed_at: afterDate.toISOString(), snapshot_at: afterDate.toISOString(),
      },
      {
        user_id: userBId, component_path: "test-gov/page",
        factor_name: "error_rate", factor_tier: "alarm",
        value: 0.1, computed_at: afterDate.toISOString(), snapshot_at: afterDate.toISOString(),
      },
    ]);

    // Create threshold: error_rate improvement > 0.05 is significant
    const { data: thr } = await serviceClient
      .from("thresholds")
      .insert({
        factor_name: "error_rate",
        component_path: "test-gov/page",
        operator: "gt",
        value: 0.05,
        action: "experiment",
        created_by: governorId,
      })
      .select("id")
      .single();
    thresholdId = thr!.id;
  });

  afterAll(async () => {
    await serviceClient.from("thresholds").delete().eq("id", thresholdId);
    for (const userId of [userAId, userBId]) {
      await serviceClient.from("factor_snapshots").delete().eq("user_id", userId);
    }
    await serviceClient.from("experiment_exposures").delete().eq("experiment_id", experimentId);
    await serviceClient.from("experiment_assignments").delete().eq("experiment_id", experimentId);
    await serviceClient.from("experiment_variants").delete().eq("experiment_id", experimentId);
    await serviceClient.from("experiments").delete().eq("id", experimentId);
    await deleteTestUser(serviceClient, userAId);
    await deleteTestUser(serviceClient, userBId);
    await deleteTestUser(serviceClient, governorId);
  });

  it("evaluates thresholds and recommends conclude for clear winner", async () => {
    const verdict = await evaluateExperimentThresholds(
      store,
      experimentId,
      ["error_rate"],
    );

    expect(verdict.action).toBe("conclude");
    expect(verdict.winning_variant).toBe("treatment");
    expect(verdict.factor_verdicts).toHaveLength(1);
    expect(verdict.factor_verdicts[0].is_significant).toBe(true);
    expect(verdict.factor_verdicts[0].best_variant).toBe("treatment");
  });

  it("concludes experiment and records winning variant", async () => {
    await concludeExperiment(store, experimentId, "treatment");

    const { data } = await serviceClient
      .from("experiments")
      .select("status, concluded_at, winning_variant")
      .eq("id", experimentId)
      .single();

    expect(data!.status).toBe("concluded");
    expect(data!.concluded_at).not.toBeNull();
    expect(data!.winning_variant).toBe("treatment");
  });

  it("returns continue when experiment has no matching thresholds", async () => {
    // Create a second experiment with a different component path (no thresholds match)
    const { data: exp2 } = await serviceClient
      .from("experiments")
      .insert({
        name: "governance-no-threshold",
        component_path: "test-gov/other",
        status: "running",
      })
      .select("id")
      .single();

    const verdict = await evaluateExperimentThresholds(
      store,
      exp2!.id,
      ["error_rate"],
    );

    expect(verdict.action).toBe("continue");

    // Cleanup
    await serviceClient.from("experiments").delete().eq("id", exp2!.id);
  });
});
