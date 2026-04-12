import { describe, it, expect, beforeAll, afterAll } from "vitest";
import {
  createServiceClient,
  createTestUser,
  deleteTestUser,
} from "../testing/supabase-harness.js";
import { queryExperimentSummaries, queryExperimentSummary } from "./dashboard.js";

describe("dashboard integration", () => {
  let serviceClient: ReturnType<typeof createServiceClient>;
  let userAId: string;
  let userBId: string;
  let runningExpId: string;
  let concludedExpId: string;

  beforeAll(async () => {
    serviceClient = createServiceClient();
    const userA = await createTestUser(serviceClient);
    const userB = await createTestUser(serviceClient);
    userAId = userA.id;
    userBId = userB.id;

    // Running experiment with 2 variants
    const { data: exp1 } = await serviceClient
      .from("experiments")
      .insert({
        name: "dashboard-running",
        component_path: "test-dash/page",
        status: "running",
      })
      .select("id")
      .single();
    runningExpId = exp1!.id;

    await serviceClient.from("experiment_variants").insert([
      { experiment_id: runningExpId, variant_key: "control", config: {}, traffic_percentage: 50 },
      { experiment_id: runningExpId, variant_key: "treatment", config: { layout: "new" }, traffic_percentage: 50 },
    ]);

    await serviceClient.from("experiment_assignments").insert([
      { user_id: userAId, experiment_id: runningExpId, variant_key: "control" },
      { user_id: userBId, experiment_id: runningExpId, variant_key: "treatment" },
    ]);

    await serviceClient.from("experiment_exposures").insert([
      { user_id: userBId, experiment_id: runningExpId, variant_key: "treatment" },
    ]);

    // Concluded experiment
    const { data: exp2 } = await serviceClient
      .from("experiments")
      .insert({
        name: "dashboard-concluded",
        component_path: "test-dash/other",
        status: "concluded",
        concluded_at: new Date().toISOString(),
        winning_variant: "treatment",
      })
      .select("id")
      .single();
    concludedExpId = exp2!.id;

    await serviceClient.from("experiment_variants").insert([
      { experiment_id: concludedExpId, variant_key: "control", config: {}, traffic_percentage: 50 },
      { experiment_id: concludedExpId, variant_key: "treatment", config: { layout: "v2" }, traffic_percentage: 50 },
    ]);
  });

  afterAll(async () => {
    // Clean up in dependency order
    await serviceClient.from("experiment_exposures").delete().eq("experiment_id", runningExpId);
    await serviceClient.from("experiment_assignments").delete().eq("experiment_id", runningExpId);
    for (const expId of [runningExpId, concludedExpId]) {
      await serviceClient.from("experiment_variants").delete().eq("experiment_id", expId);
    }
    await serviceClient.from("experiments").delete().eq("id", runningExpId);
    await serviceClient.from("experiments").delete().eq("id", concludedExpId);
    await deleteTestUser(serviceClient, userAId);
    await deleteTestUser(serviceClient, userBId);
  });

  it("queries all experiment summaries", async () => {
    const rows = await queryExperimentSummaries(serviceClient);

    const runningRows = rows.filter(r => r.experiment_id === runningExpId);
    expect(runningRows).toHaveLength(2);

    const controlRow = runningRows.find(r => r.variant_key === "control");
    expect(controlRow).toBeDefined();
    expect(controlRow!.assigned_users).toBe(1);
    expect(controlRow!.exposed_users).toBe(0);
    expect(controlRow!.status).toBe("running");

    const treatmentRow = runningRows.find(r => r.variant_key === "treatment");
    expect(treatmentRow).toBeDefined();
    expect(treatmentRow!.assigned_users).toBe(1);
    expect(treatmentRow!.exposed_users).toBe(1);
  });

  it("queries single experiment summary by id", async () => {
    const rows = await queryExperimentSummary(serviceClient, runningExpId);

    expect(rows).toHaveLength(2);
    expect(rows.every(r => r.experiment_id === runningExpId)).toBe(true);
    expect(rows.every(r => r.name === "dashboard-running")).toBe(true);
  });

  it("filters by status", async () => {
    const runningRows = await queryExperimentSummaries(serviceClient, { status: "running" });
    const hasRunning = runningRows.some(r => r.experiment_id === runningExpId);
    const hasConcluded = runningRows.some(r => r.experiment_id === concludedExpId);

    expect(hasRunning).toBe(true);
    expect(hasConcluded).toBe(false);
  });

  it("filters by component_path", async () => {
    const rows = await queryExperimentSummaries(serviceClient, {
      component_path: "test-dash/page",
    });

    expect(rows.some(r => r.experiment_id === runningExpId)).toBe(true);
    expect(rows.some(r => r.experiment_id === concludedExpId)).toBe(false);
  });

  it("concluded experiment shows winning_variant", async () => {
    const rows = await queryExperimentSummary(serviceClient, concludedExpId);

    expect(rows.length).toBeGreaterThan(0);
    expect(rows[0].status).toBe("concluded");
    expect(rows[0].winning_variant).toBe("treatment");
    expect(rows[0].concluded_at).not.toBeNull();
  });
});
