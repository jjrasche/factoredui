import { describe, it, expect, beforeAll, afterAll } from "vitest";
import {
  createServiceClient,
  createAnonClient,
  createTestUser,
  deleteTestUser,
  signInTestUser,
} from "../testing/supabase-harness.js";
import { createExperiment, startExperiment } from "./lifecycle.js";
import type { ExperimentDefinition } from "./lifecycle.js";
import { evaluateFlag } from "./flags.js";
import { runGovernanceCheck } from "./governance-check.js";
import { queryGovernanceLog } from "./governance-log.js";

/**
 * Full experiment lifecycle integration test.
 *
 * Exercises the entire loop:
 *   createExperiment (draft)
 *   → startExperiment (running)
 *   → evaluateFlag (assigns user, records exposure)
 *   → factor snapshots inserted (before/after deltas)
 *   → runGovernanceCheck (evaluates thresholds, logs verdict, concludes)
 *   → verify experiment concluded with correct winner + governance audit log
 *
 * Requires local Supabase: `npx supabase start`
 */
describe("full experiment loop", () => {
  const serviceClient = createServiceClient();
  const componentPath = "full-loop-test/cta";
  const factorName = "engagement";

  interface TestUserEntry {
    id: string;
    email: string;
    password: string;
  }

  let primaryUser: TestUserEntry;
  let secondaryUser: TestUserEntry;
  let authedClient: ReturnType<typeof createAnonClient>;
  let experimentId: string;
  let primaryVariant: string;

  beforeAll(async () => {
    primaryUser = await createTestUser(serviceClient);
    secondaryUser = await createTestUser(serviceClient);

    const anonClient = createAnonClient();
    authedClient = await signInTestUser(anonClient, primaryUser);
  });

  afterAll(async () => {
    // Clean up in dependency order
    if (experimentId) {
      await serviceClient.from("governance_log").delete().eq("experiment_id", experimentId);
      await serviceClient.from("experiment_exposures").delete().eq("experiment_id", experimentId);
      await serviceClient.from("experiment_assignments").delete().eq("experiment_id", experimentId);
      await serviceClient.from("experiment_variants").delete().eq("experiment_id", experimentId);
      await serviceClient.from("experiments").delete().eq("id", experimentId);
    }

    for (const user of [primaryUser, secondaryUser]) {
      await serviceClient.from("factor_snapshots").delete().eq("user_id", user.id);
      await serviceClient.from("thresholds").delete().eq("created_by", user.id);
      await deleteTestUser(serviceClient, user.id);
    }
  });

  it("runs the full lifecycle: create → start → assign → govern → conclude", async () => {
    // --- Step 1: Create experiment (draft) ---
    const definition: ExperimentDefinition = {
      name: `full-loop-${Date.now()}`,
      description: "Full lifecycle integration test",
      component_path: componentPath,
      variants: [
        { variant_key: "control", config: { color: "blue" }, traffic_percentage: 50 },
        { variant_key: "treatment", config: { color: "green" }, traffic_percentage: 50 },
      ],
    };

    const created = await createExperiment(serviceClient, definition);
    experimentId = created.id;

    expect(created.status).toBe("draft");
    expect(created.name).toBe(definition.name);

    // --- Step 2: Start experiment (draft → running) ---
    await startExperiment(serviceClient, experimentId);

    const { data: running } = await serviceClient
      .from("experiments")
      .select("status")
      .eq("id", experimentId)
      .single();

    expect(running!.status).toBe("running");

    // --- Step 3: Evaluate flag (assigns primary user, records exposure) ---
    const assignment = await evaluateFlag(authedClient, definition.name);

    expect(assignment).not.toBeNull();
    expect(assignment!.experiment_id).toBe(experimentId);
    expect(["control", "treatment"]).toContain(assignment!.variant_key);
    primaryVariant = assignment!.variant_key;

    // Verify assignment persisted
    const { data: assignments } = await serviceClient
      .from("experiment_assignments")
      .select("variant_key")
      .eq("user_id", primaryUser.id)
      .eq("experiment_id", experimentId);

    expect(assignments).toHaveLength(1);

    // Verify exposure recorded
    const { data: exposures } = await serviceClient
      .from("experiment_exposures")
      .select("variant_key")
      .eq("user_id", primaryUser.id)
      .eq("experiment_id", experimentId);

    expect(exposures!.length).toBeGreaterThanOrEqual(1);

    // --- Step 4: Assign secondary user to the OTHER variant ---
    const secondaryVariant = primaryVariant === "control" ? "treatment" : "control";

    await serviceClient.from("experiment_assignments").insert({
      user_id: secondaryUser.id,
      experiment_id: experimentId,
      variant_key: secondaryVariant,
    });

    // --- Step 5: Insert factor snapshots (before + after) for both users ---
    // "before" snapshots: timestamped before experiment creation
    const { data: experimentRow } = await serviceClient
      .from("experiments")
      .select("created_at")
      .eq("id", experimentId)
      .single();

    const experimentCreatedAt = new Date(experimentRow!.created_at);
    const beforeTime = new Date(experimentCreatedAt.getTime() - 60_000);
    const afterTime = new Date();

    // Determine which user is control vs treatment
    const controlUserId = primaryVariant === "control" ? primaryUser.id : secondaryUser.id;
    const treatmentUserId = primaryVariant === "treatment" ? primaryUser.id : secondaryUser.id;

    const snapshotRows = [
      // Control user: before=0.50, after=0.55 → delta=0.05
      {
        user_id: controlUserId,
        component_path: componentPath,
        factor_name: factorName,
        factor_tier: "diagnostic",
        value: 0.50,
        computed_at: beforeTime.toISOString(),
        snapshot_at: beforeTime.toISOString(),
      },
      {
        user_id: controlUserId,
        component_path: componentPath,
        factor_name: factorName,
        factor_tier: "diagnostic",
        value: 0.55,
        computed_at: afterTime.toISOString(),
        snapshot_at: afterTime.toISOString(),
      },
      // Treatment user: before=0.50, after=0.90 → delta=0.40
      {
        user_id: treatmentUserId,
        component_path: componentPath,
        factor_name: factorName,
        factor_tier: "diagnostic",
        value: 0.50,
        computed_at: beforeTime.toISOString(),
        snapshot_at: beforeTime.toISOString(),
      },
      {
        user_id: treatmentUserId,
        component_path: componentPath,
        factor_name: factorName,
        factor_tier: "diagnostic",
        value: 0.90,
        computed_at: afterTime.toISOString(),
        snapshot_at: afterTime.toISOString(),
      },
    ];

    const { error: snapshotError } = await serviceClient
      .from("factor_snapshots")
      .insert(snapshotRows);

    if (snapshotError) throw new Error(`factor_snapshots insert failed: ${snapshotError.message}`);

    // --- Step 6: Insert governance threshold ---
    // improvement = |treatment_delta| - |control_delta| = 0.40 - 0.05 = 0.35
    // threshold: improvement > 0.1 → significant → conclude
    const { error: thresholdError } = await serviceClient
      .from("thresholds")
      .insert({
        factor_name: factorName,
        component_path: componentPath,
        operator: "gt",
        value: 0.1,
        action: "experiment",
        created_by: primaryUser.id,
      });

    if (thresholdError) throw new Error(`thresholds insert failed: ${thresholdError.message}`);

    // --- Step 7: Run governance check ---
    const verdict = await runGovernanceCheck(serviceClient, experimentId, [factorName]);

    expect(verdict.action).toBe("conclude");
    expect(verdict.winning_variant).toBe("treatment");
    expect(verdict.factor_verdicts).toHaveLength(1);
    expect(verdict.factor_verdicts[0].factor_name).toBe(factorName);
    expect(verdict.factor_verdicts[0].is_significant).toBe(true);
    expect(verdict.factor_verdicts[0].best_variant).toBe("treatment");

    // --- Step 8: Verify experiment concluded ---
    const { data: concluded } = await serviceClient
      .from("experiments")
      .select("status, winning_variant")
      .eq("id", experimentId)
      .single();

    expect(concluded!.status).toBe("concluded");
    expect(concluded!.winning_variant).toBe("treatment");

    // --- Step 9: Verify governance audit log ---
    const log = await queryGovernanceLog(serviceClient, experimentId);

    expect(log.length).toBeGreaterThanOrEqual(1);
    expect(log[0].verdict).toBe("conclude");
    expect(log[0].winning_variant).toBe("treatment");
    expect(log[0].factor_verdicts).toHaveLength(1);
  });
});
