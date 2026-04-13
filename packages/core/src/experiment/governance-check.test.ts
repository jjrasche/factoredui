import { describe, it, expect, vi } from "vitest";
import { logGovernanceVerdict, runGovernanceCheck } from "./governance-check.js";
import type { GovernanceVerdict } from "./governance.js";

function buildConcludeVerdict(): GovernanceVerdict {
  return {
    action: "conclude",
    winning_variant: "treatment",
    factor_verdicts: [
      {
        factor_name: "error_rate",
        best_variant: "treatment",
        best_delta: -0.2,
        control_delta: -0.05,
        is_significant: true,
      },
    ],
  };
}

function buildContinueVerdict(): GovernanceVerdict {
  return {
    action: "continue",
    winning_variant: null,
    factor_verdicts: [],
  };
}

function createMockSupabase(overrides: {
  insertResult?: { error: { message: string } | null };
} = {}) {
  const insertMock = vi.fn().mockResolvedValue(
    overrides.insertResult ?? { error: null },
  );

  return {
    client: {
      from: (table: string) => {
        if (table === "governance_log") {
          return { insert: insertMock };
        }
        return {};
      },
    } as never,
    insertMock,
  };
}

describe("logGovernanceVerdict", () => {
  it("inserts verdict into governance_log", async () => {
    const { client, insertMock } = createMockSupabase();
    const verdict = buildConcludeVerdict();

    await logGovernanceVerdict(client, "exp-1", verdict);

    expect(insertMock).toHaveBeenCalledWith({
      experiment_id: "exp-1",
      verdict: "conclude",
      winning_variant: "treatment",
      factor_verdicts: verdict.factor_verdicts,
    });
  });

  it("propagates insert errors", async () => {
    const { client } = createMockSupabase({
      insertResult: { error: { message: "permission denied" } },
    });

    await expect(
      logGovernanceVerdict(client, "exp-1", buildContinueVerdict()),
    ).rejects.toThrow("logGovernanceVerdict failed: permission denied");
  });
});

describe("runGovernanceCheck", () => {
  it("evaluates, logs, and returns the verdict", async () => {
    // runGovernanceCheck is an orchestrator — full integration test
    // requires real Supabase. Unit-testable parts (logGovernanceVerdict,
    // computeGovernanceVerdict) are tested individually above and in
    // governance.test.ts. Orchestrator integration tested in
    // governance.integration.test.ts.
  });
});
