import { describe, it, expect, beforeAll, afterAll } from "vitest";
import {
  createServiceClient,
  createAnonClient,
  createTestUser,
  deleteTestUser,
  signInTestUser,
} from "../testing/supabase-harness.js";
import { evaluateFlag } from "./flags.js";
import type { TargetingRule } from "./targeting.js";

describe("evaluateFlag integration", () => {
  let serviceClient: ReturnType<typeof createServiceClient>;
  let authedClient: ReturnType<typeof createAnonClient>;
  let testUserId: string;
  let experimentId: string;

  beforeAll(async () => {
    serviceClient = createServiceClient();
    const user = await createTestUser(serviceClient);
    testUserId = user.id;

    const anonClient = createAnonClient();
    authedClient = await signInTestUser(anonClient, user);

    const { data: experiment, error: expError } = await serviceClient
      .from("experiments")
      .insert({
        name: "integration-cta-test",
        description: "Integration test experiment",
        component_path: "test-page/cta",
        status: "running",
      })
      .select("id")
      .single();

    if (expError) throw new Error(`Failed to create experiment: ${expError.message}`);
    experimentId = experiment!.id;

    const { error: varError } = await serviceClient
      .from("experiment_variants")
      .insert([
        {
          experiment_id: experimentId,
          variant_key: "control",
          config: { text: "Submit" },
          traffic_percentage: 50,
        },
        {
          experiment_id: experimentId,
          variant_key: "variant-a",
          config: { text: "Go!" },
          traffic_percentage: 50,
        },
      ]);

    if (varError) throw new Error(`Failed to create variants: ${varError.message}`);
  });

  afterAll(async () => {
    await serviceClient
      .from("experiment_exposures")
      .delete()
      .eq("experiment_id", experimentId);
    await serviceClient
      .from("experiment_assignments")
      .delete()
      .eq("experiment_id", experimentId);
    await serviceClient
      .from("experiment_variants")
      .delete()
      .eq("experiment_id", experimentId);
    await serviceClient
      .from("experiments")
      .delete()
      .eq("id", experimentId);
    await deleteTestUser(serviceClient, testUserId);
  });

  it("assigns user to a variant and records exposure", async () => {
    const result = await evaluateFlag(authedClient, "integration-cta-test");

    expect(result).not.toBeNull();
    expect(result!.experiment_id).toBe(experimentId);
    expect(["control", "variant-a"]).toContain(result!.variant_key);
    expect(result!.config).toBeDefined();

    const { data: assignments } = await serviceClient
      .from("experiment_assignments")
      .select("variant_key")
      .eq("user_id", testUserId)
      .eq("experiment_id", experimentId);

    expect(assignments).toHaveLength(1);
    expect(assignments![0].variant_key).toBe(result!.variant_key);

    const { data: exposures } = await serviceClient
      .from("experiment_exposures")
      .select("variant_key")
      .eq("user_id", testUserId)
      .eq("experiment_id", experimentId);

    expect(exposures!.length).toBeGreaterThanOrEqual(1);
    expect(exposures![0].variant_key).toBe(result!.variant_key);
  });

  it("returns existing assignment on re-evaluation", async () => {
    const first = await evaluateFlag(authedClient, "integration-cta-test");
    const second = await evaluateFlag(authedClient, "integration-cta-test");

    expect(second).not.toBeNull();
    expect(second!.variant_key).toBe(first!.variant_key);
    expect(second!.experiment_id).toBe(first!.experiment_id);

    const { data: assignments } = await serviceClient
      .from("experiment_assignments")
      .select("id")
      .eq("user_id", testUserId)
      .eq("experiment_id", experimentId);

    expect(assignments).toHaveLength(1);
  });

  it("returns null for nonexistent experiment", async () => {
    const result = await evaluateFlag(authedClient, "nonexistent-experiment");
    expect(result).toBeNull();
  });
});

describe("evaluateFlag with targeting rules", () => {
  let serviceClient: ReturnType<typeof createServiceClient>;
  let authedClient: ReturnType<typeof createAnonClient>;
  let testUserId: string;
  let sessionId: string;
  let targetedExpId: string;
  let strictExpId: string;
  const componentPath = "test-targeting/checkout";

  beforeAll(async () => {
    serviceClient = createServiceClient();
    const user = await createTestUser(serviceClient);
    testUserId = user.id;

    const anonClient = createAnonClient();
    authedClient = await signInTestUser(anonClient, user);

    // Create session + events so factor views have data
    const { data: session } = await serviceClient
      .from("sessions")
      .insert({ user_id: testUserId, metadata: {} })
      .select("id")
      .single();
    sessionId = session!.id;

    // Insert error events: 1 error + 3 clicks = 25% error_rate
    await serviceClient.from("events").insert([
      { user_id: testUserId, session_id: sessionId, event_type: "error", component_path: componentPath, payload: {} },
      { user_id: testUserId, session_id: sessionId, event_type: "click", component_path: componentPath, payload: {} },
      { user_id: testUserId, session_id: sessionId, event_type: "click", component_path: componentPath, payload: {} },
      { user_id: testUserId, session_id: sessionId, event_type: "click", component_path: componentPath, payload: {} },
    ]);

    await serviceClient.rpc("refresh_factor_views");

    // Experiment with targeting: error_rate > 0.1 (user qualifies at 0.25)
    const matchingRules: TargetingRule[] = [
      { factor: "error_rate", operator: "gt", threshold: 0.1 },
    ];

    const { data: targetedExp } = await serviceClient
      .from("experiments")
      .insert({
        name: "targeted-error-test",
        component_path: componentPath,
        status: "running",
        targeting_rules: matchingRules,
      })
      .select("id")
      .single();
    targetedExpId = targetedExp!.id;

    await serviceClient.from("experiment_variants").insert([
      { experiment_id: targetedExpId, variant_key: "control", config: {}, traffic_percentage: 50 },
      { experiment_id: targetedExpId, variant_key: "treatment", config: { cta: "Fix Now" }, traffic_percentage: 50 },
    ]);

    // Experiment with strict targeting: error_rate > 0.5 (user does NOT qualify)
    const strictRules: TargetingRule[] = [
      { factor: "error_rate", operator: "gt", threshold: 0.5 },
    ];

    const { data: strictExp } = await serviceClient
      .from("experiments")
      .insert({
        name: "strict-error-test",
        component_path: componentPath,
        status: "running",
        targeting_rules: strictRules,
      })
      .select("id")
      .single();
    strictExpId = strictExp!.id;

    await serviceClient.from("experiment_variants").insert([
      { experiment_id: strictExpId, variant_key: "control", config: {}, traffic_percentage: 50 },
      { experiment_id: strictExpId, variant_key: "treatment", config: {}, traffic_percentage: 50 },
    ]);
  });

  afterAll(async () => {
    for (const expId of [targetedExpId, strictExpId]) {
      await serviceClient.from("experiment_exposures").delete().eq("experiment_id", expId);
      await serviceClient.from("experiment_assignments").delete().eq("experiment_id", expId);
      await serviceClient.from("experiment_variants").delete().eq("experiment_id", expId);
      await serviceClient.from("experiments").delete().eq("id", expId);
    }
    await serviceClient.from("events").delete().eq("user_id", testUserId);
    await serviceClient.from("sessions").delete().eq("user_id", testUserId);
    await deleteTestUser(serviceClient, testUserId);
  });

  it("assigns user when targeting rules match their factors", async () => {
    const result = await evaluateFlag(authedClient, "targeted-error-test");

    expect(result).not.toBeNull();
    expect(result!.experiment_id).toBe(targetedExpId);
    expect(["control", "treatment"]).toContain(result!.variant_key);
  });

  it("returns null when targeting rules do not match", async () => {
    const result = await evaluateFlag(authedClient, "strict-error-test");

    expect(result).toBeNull();

    const { data: assignments } = await serviceClient
      .from("experiment_assignments")
      .select("id")
      .eq("user_id", testUserId)
      .eq("experiment_id", strictExpId);

    expect(assignments).toHaveLength(0);
  });
});
