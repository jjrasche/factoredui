import { describe, it, expect } from "vitest";
import { computeGovernanceVerdict, isThresholdExceeded } from "./governance.js";
import type { Threshold } from "./governance.js";
import type { VariantResult } from "./results.js";

function buildThreshold(overrides: Partial<Threshold> = {}): Threshold {
  return {
    id: "threshold-1",
    factor_name: "error_rate",
    component_path: "test/page",
    operator: "gt",
    value: 0.05,
    action: "experiment",
    ...overrides,
  };
}

function buildResults(
  controlDelta: number,
  treatmentDelta: number,
  factorName = "error_rate",
): VariantResult[] {
  return [
    {
      variant_key: "control",
      user_count: 10,
      factor_deltas: [{ factor_name: factorName, before: 0.3, after: 0.3 + controlDelta, delta: controlDelta }],
    },
    {
      variant_key: "treatment",
      user_count: 10,
      factor_deltas: [{ factor_name: factorName, before: 0.3, after: 0.3 + treatmentDelta, delta: treatmentDelta }],
    },
  ];
}

describe("isThresholdExceeded", () => {
  it("gt operator", () => {
    expect(isThresholdExceeded(0.1, "gt", 0.05)).toBe(true);
    expect(isThresholdExceeded(0.05, "gt", 0.05)).toBe(false);
  });

  it("gte operator", () => {
    expect(isThresholdExceeded(0.05, "gte", 0.05)).toBe(true);
    expect(isThresholdExceeded(0.04, "gte", 0.05)).toBe(false);
  });

  it("lt operator", () => {
    expect(isThresholdExceeded(0.03, "lt", 0.05)).toBe(true);
    expect(isThresholdExceeded(0.05, "lt", 0.05)).toBe(false);
  });

  it("lte operator", () => {
    expect(isThresholdExceeded(0.05, "lte", 0.05)).toBe(true);
    expect(isThresholdExceeded(0.06, "lte", 0.05)).toBe(false);
  });

  it("eq operator", () => {
    expect(isThresholdExceeded(0.05, "eq", 0.05)).toBe(true);
    expect(isThresholdExceeded(0.06, "eq", 0.05)).toBe(false);
  });
});

describe("computeGovernanceVerdict", () => {
  it("concludes when treatment clearly beats control", () => {
    // treatment delta -0.2 (larger magnitude) vs control -0.05
    // improvement = |−0.2| − |−0.05| = 0.15, threshold gt 0.05 → significant
    const results = buildResults(-0.05, -0.2);
    const thresholds = [buildThreshold()];

    const verdict = computeGovernanceVerdict(results, thresholds);

    expect(verdict.action).toBe("conclude");
    expect(verdict.winning_variant).toBe("treatment");
    expect(verdict.factor_verdicts).toHaveLength(1);
    expect(verdict.factor_verdicts[0].is_significant).toBe(true);
  });

  it("continues when deltas do not exceed threshold", () => {
    // treatment delta -0.06 vs control -0.05, improvement = 0.01 < 0.05
    const results = buildResults(-0.05, -0.06);
    const thresholds = [buildThreshold()];

    const verdict = computeGovernanceVerdict(results, thresholds);

    expect(verdict.action).toBe("continue");
    expect(verdict.winning_variant).toBeNull();
  });

  it("flags for review when treatment is worse than control", () => {
    // treatment delta -0.02, control delta -0.1
    // treatment magnitude < control magnitude → worsening
    const results = buildResults(-0.1, -0.02);
    const thresholds = [buildThreshold()];

    const verdict = computeGovernanceVerdict(results, thresholds);

    expect(verdict.action).toBe("flag_review");
    expect(verdict.winning_variant).toBeNull();
  });

  it("flags for review when factors disagree on winner", () => {
    const results: VariantResult[] = [
      {
        variant_key: "control",
        user_count: 10,
        factor_deltas: [
          { factor_name: "error_rate", before: 0.3, after: 0.25, delta: -0.05 },
          { factor_name: "engagement", before: 0.5, after: 0.7, delta: 0.2 },
        ],
      },
      {
        variant_key: "treatment_a",
        user_count: 10,
        factor_deltas: [
          { factor_name: "error_rate", before: 0.3, after: 0.1, delta: -0.2 },
          { factor_name: "engagement", before: 0.5, after: 0.55, delta: 0.05 },
        ],
      },
      {
        variant_key: "treatment_b",
        user_count: 10,
        factor_deltas: [
          { factor_name: "error_rate", before: 0.3, after: 0.28, delta: -0.02 },
          { factor_name: "engagement", before: 0.5, after: 0.9, delta: 0.4 },
        ],
      },
    ];

    const thresholds = [
      buildThreshold({ factor_name: "error_rate" }),
      buildThreshold({ factor_name: "engagement" }),
    ];

    const verdict = computeGovernanceVerdict(results, thresholds);

    // error_rate winner: treatment_a, engagement winner: treatment_b → disagree
    expect(verdict.action).toBe("flag_review");
  });

  it("returns continue when no thresholds exist", () => {
    const results = buildResults(-0.05, -0.2);
    const verdict = computeGovernanceVerdict(results, []);
    expect(verdict.action).toBe("continue");
  });

  it("returns continue with fewer than 2 variants", () => {
    const results: VariantResult[] = [
      { variant_key: "control", user_count: 10, factor_deltas: [] },
    ];
    const verdict = computeGovernanceVerdict(results, [buildThreshold()]);
    expect(verdict.action).toBe("continue");
  });
});
