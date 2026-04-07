import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { createServiceClient } from "../testing/supabase-harness.js";
import {
  queryGovernanceLog,
  queryRecentGovernanceLog,
  queryGovernanceLogByVerdict,
} from "./governance-log.js";

describe("governance-log integration", () => {
  let serviceClient: ReturnType<typeof createServiceClient>;
  let experimentIdA: string;
  let experimentIdB: string;

  beforeAll(async () => {
    serviceClient = createServiceClient();

    const { data: expA } = await serviceClient
      .from("experiments")
      .insert({
        name: "gov-log-test-a",
        component_path: "test-gov-log/page",
        status: "running",
      })
      .select("id")
      .single();
    experimentIdA = expA!.id;

    const { data: expB } = await serviceClient
      .from("experiments")
      .insert({
        name: "gov-log-test-b",
        component_path: "test-gov-log/other",
        status: "running",
      })
      .select("id")
      .single();
    experimentIdB = expB!.id;

    // Insert governance_log rows with staggered timestamps for ordering tests
    await serviceClient.from("governance_log").insert([
      {
        experiment_id: experimentIdA,
        verdict: "continue",
        winning_variant: null,
        factor_verdicts: [{ factor_name: "scroll_depth", best_variant: "treatment", best_delta: 0.1, control_delta: 0.05, is_significant: false }],
        evaluated_at: "2026-01-01T00:00:00Z",
      },
      {
        experiment_id: experimentIdA,
        verdict: "conclude",
        winning_variant: "treatment",
        factor_verdicts: [{ factor_name: "scroll_depth", best_variant: "treatment", best_delta: 0.5, control_delta: 0.05, is_significant: true }],
        evaluated_at: "2026-01-02T00:00:00Z",
      },
      {
        experiment_id: experimentIdB,
        verdict: "flag_review",
        winning_variant: null,
        factor_verdicts: [{ factor_name: "error_rate", best_variant: "control", best_delta: 0.02, control_delta: 0.03, is_significant: false }],
        evaluated_at: "2026-01-03T00:00:00Z",
      },
    ]);
  });

  afterAll(async () => {
    await serviceClient.from("governance_log").delete().eq("experiment_id", experimentIdA);
    await serviceClient.from("governance_log").delete().eq("experiment_id", experimentIdB);
    await serviceClient.from("experiments").delete().eq("id", experimentIdA);
    await serviceClient.from("experiments").delete().eq("id", experimentIdB);
  });

  it("queries governance log for a single experiment ordered by evaluated_at desc", async () => {
    const rows = await queryGovernanceLog(serviceClient, experimentIdA);

    expect(rows).toHaveLength(2);
    expect(rows[0].verdict).toBe("conclude");
    expect(rows[0].winning_variant).toBe("treatment");
    expect(rows[1].verdict).toBe("continue");
    expect(rows[1].winning_variant).toBeNull();
  });

  it("returns empty array for experiment with no log entries", async () => {
    const rows = await queryGovernanceLog(serviceClient, crypto.randomUUID());

    expect(rows).toHaveLength(0);
  });

  it("queries recent governance log across all experiments", async () => {
    const rows = await queryRecentGovernanceLog(serviceClient, 10);

    const testRows = rows.filter(
      r => r.experiment_id === experimentIdA || r.experiment_id === experimentIdB,
    );
    expect(testRows).toHaveLength(3);
    // Most recent first
    expect(testRows[0].experiment_id).toBe(experimentIdB);
    expect(testRows[1].experiment_id).toBe(experimentIdA);
  });

  it("respects limit parameter on recent log", async () => {
    const rows = await queryRecentGovernanceLog(serviceClient, 1);

    expect(rows).toHaveLength(1);
  });

  it("filters governance log by verdict", async () => {
    const concludeRows = await queryGovernanceLogByVerdict(serviceClient, "conclude");
    const testConclude = concludeRows.filter(r => r.experiment_id === experimentIdA);

    expect(testConclude).toHaveLength(1);
    expect(testConclude[0].winning_variant).toBe("treatment");

    const flagRows = await queryGovernanceLogByVerdict(serviceClient, "flag_review");
    const testFlag = flagRows.filter(r => r.experiment_id === experimentIdB);

    expect(testFlag).toHaveLength(1);
  });

  it("preserves factor_verdicts jsonb structure", async () => {
    const rows = await queryGovernanceLog(serviceClient, experimentIdA);
    const concludeRow = rows.find(r => r.verdict === "conclude");

    expect(concludeRow).toBeDefined();
    expect(concludeRow!.factor_verdicts).toHaveLength(1);
    expect(concludeRow!.factor_verdicts[0].factor_name).toBe("scroll_depth");
    expect(concludeRow!.factor_verdicts[0].is_significant).toBe(true);
  });
});
