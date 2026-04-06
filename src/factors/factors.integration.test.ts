import { describe, it, expect, beforeAll, afterAll } from "vitest";
import {
  createServiceClient,
  createTestUser,
  deleteTestUser,
} from "../testing/supabase-harness.js";

describe("factor materialized views", () => {
  let serviceClient: ReturnType<typeof createServiceClient>;
  let testUserId: string;
  let sessionId: string;
  const componentPath = "test-factors/checkout";

  beforeAll(async () => {
    serviceClient = createServiceClient();
    const user = await createTestUser(serviceClient);
    testUserId = user.id;

    const { data: session, error: sessionError } = await serviceClient
      .from("sessions")
      .insert({ user_id: testUserId, metadata: {} })
      .select("id")
      .single();

    if (sessionError) throw new Error(`Session creation failed: ${sessionError.message}`);
    sessionId = session!.id;
  });

  afterAll(async () => {
    await serviceClient.from("events").delete().eq("user_id", testUserId);
    await serviceClient.from("sessions").delete().eq("user_id", testUserId);
    await deleteTestUser(serviceClient, testUserId);
  });

  async function insertEvent(
    eventType: string,
    payload: Record<string, unknown> = {},
  ): Promise<void> {
    const { error } = await serviceClient.from("events").insert({
      user_id: testUserId,
      session_id: sessionId,
      event_type: eventType,
      component_path: componentPath,
      payload,
    });
    if (error) throw new Error(`Event insert failed: ${error.message}`);
  }

  async function refreshAllViews(): Promise<void> {
    const { error } = await serviceClient.rpc("refresh_factor_views");
    if (error) throw new Error(`Refresh factor views failed: ${error.message}`);
  }

  async function queryFactor(factorName: string): Promise<number | null> {
    const { data, error } = await serviceClient
      .from("v_factors_current")
      .select("value")
      .eq("user_id", testUserId)
      .eq("component_path", componentPath)
      .eq("factor_name", factorName)
      .maybeSingle();

    if (error) throw new Error(`Factor query failed: ${error.message}`);
    return data?.value ?? null;
  }

  it("computes error_rate from mixed events", async () => {
    await insertEvent("click");
    await insertEvent("click");
    await insertEvent("error");
    await insertEvent("click");

    await refreshAllViews();

    const errorRate = await queryFactor("error_rate");
    expect(errorRate).not.toBeNull();
    // 1 error / 4 total = 0.25
    expect(errorRate).toBeCloseTo(0.25, 2);
  });

  it("computes rage_click_count from rage_click events", async () => {
    await insertEvent("rage_click");
    await insertEvent("rage_click");
    await insertEvent("rage_click");

    await refreshAllViews();

    const rageClicks = await queryFactor("rage_click_count");
    expect(rageClicks).not.toBeNull();
    expect(rageClicks).toBeGreaterThanOrEqual(3);
  });

  it("computes dead_click_count from dead_click events", async () => {
    await insertEvent("dead_click");
    await insertEvent("dead_click");

    await refreshAllViews();

    const deadClicks = await queryFactor("dead_click_count");
    expect(deadClicks).not.toBeNull();
    expect(deadClicks).toBeGreaterThanOrEqual(2);
  });

  it("computes scroll_reversal_count from scroll_reversal events", async () => {
    await insertEvent("scroll_reversal");

    await refreshAllViews();

    const scrollReversals = await queryFactor("scroll_reversal_count");
    expect(scrollReversals).not.toBeNull();
    expect(scrollReversals).toBeGreaterThanOrEqual(1);
  });

  it("computes completion_rate from impression + navigation complete events", async () => {
    await insertEvent("impression");
    await insertEvent("navigation", { action: "complete" });

    await refreshAllViews();

    const completionRate = await queryFactor("completion_rate");
    expect(completionRate).not.toBeNull();
    // 1 session with completion / 1 session with impression = 1.0
    expect(completionRate).toBeCloseTo(1.0, 2);
  });

  it("computes drop_off_rate from sessions without navigation", async () => {
    // All events so far include navigation events, so drop_off_rate reflects
    // the mix of navigated vs non-navigated sessions. With one session that
    // has navigation events, drop_off should be 0.
    await refreshAllViews();

    const dropOff = await queryFactor("drop_off_rate");
    expect(dropOff).not.toBeNull();
    expect(dropOff).toBeCloseTo(0.0, 2);
  });

  it("computes hesitation_ms from impression to first interaction", async () => {
    // Hesitation requires a click AFTER an impression (created_at > impression.created_at).
    // Insert impression, wait briefly, then insert click to guarantee ordering.
    await insertEvent("impression");
    await new Promise((resolve) => setTimeout(resolve, 50));
    await insertEvent("click");

    await refreshAllViews();

    const hesitation = await queryFactor("hesitation_ms");
    expect(hesitation).not.toBeNull();
    // Just verify it's a non-negative number (timing depends on insert speed)
    expect(hesitation).toBeGreaterThanOrEqual(0);
  });

  it("exposes all factors through v_factors_current unified view", async () => {
    await refreshAllViews();

    const { data, error } = await serviceClient
      .from("v_factors_current")
      .select("factor_name, factor_tier, value")
      .eq("user_id", testUserId)
      .eq("component_path", componentPath);

    expect(error).toBeNull();
    expect(data!.length).toBeGreaterThanOrEqual(5);

    const factorNames = data!.map((row) => row.factor_name);
    expect(factorNames).toContain("error_rate");
    expect(factorNames).toContain("rage_click_count");
    expect(factorNames).toContain("dead_click_count");

    const tiers = [...new Set(data!.map((row) => row.factor_tier))];
    expect(tiers).toContain("alarm");
    expect(tiers).toContain("diagnostic");
  });

  it("exposes aggregated component factors through v_component_factors_agg", async () => {
    await refreshAllViews();

    const { data, error } = await serviceClient
      .from("v_component_factors_agg")
      .select("component_path, factor_name, user_count, avg_value")
      .eq("component_path", componentPath);

    expect(error).toBeNull();
    expect(data!.length).toBeGreaterThanOrEqual(1);
    expect(data![0].user_count).toBeGreaterThanOrEqual(1);
    expect(data![0].avg_value).not.toBeNull();
  });
});
