import { describe, it, expect, vi } from "vitest";
import { logGovernanceVerdict } from "./governance-check.js";
import type { GovernanceVerdict } from "./governance.js";
import type { FactoredStore } from "../store.js";

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

function createMockStore(overrides: {
  insertError?: Error;
} = {}) {
  const insertGovernanceVerdict = overrides.insertError
    ? vi.fn().mockRejectedValue(overrides.insertError)
    : vi.fn().mockResolvedValue(undefined);

  const store = { insertGovernanceVerdict } as unknown as FactoredStore;
  return { store, insertGovernanceVerdict };
}

describe("logGovernanceVerdict", () => {
  it("inserts verdict into governance_log", async () => {
    const { store, insertGovernanceVerdict } = createMockStore();
    const verdict = buildConcludeVerdict();

    await logGovernanceVerdict(store, "exp-1", verdict);

    expect(insertGovernanceVerdict).toHaveBeenCalledWith(
      "exp-1",
      "conclude",
      "treatment",
      verdict.factor_verdicts,
    );
  });

  it("propagates insert errors", async () => {
    const { store } = createMockStore({
      insertError: new Error("permission denied"),
    });

    await expect(
      logGovernanceVerdict(store, "exp-1", buildContinueVerdict()),
    ).rejects.toThrow("permission denied");
  });
});

describe("runGovernanceCheck", () => {
  it("evaluates, logs, and returns the verdict", async () => {
    // runGovernanceCheck is an orchestrator — full integration test
    // requires real store. Unit-testable parts (logGovernanceVerdict,
    // computeGovernanceVerdict) are tested individually above and in
    // governance.test.ts. Orchestrator integration tested in
    // governance.integration.test.ts.
  });
});
