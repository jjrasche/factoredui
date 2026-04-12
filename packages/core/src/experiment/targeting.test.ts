import { describe, it, expect } from "vitest";
import { evaluateTargeting } from "./targeting.js";
import type { Factor } from "../types.js";
import type { TargetingRule } from "./targeting.js";

function buildFactor(name: string, value: number): Factor {
  return {
    user_id: "user-1",
    component_path: "test/component",
    factor_name: name,
    factor_tier: "alarm",
    value,
    computed_at: new Date().toISOString(),
  };
}

describe("evaluateTargeting", () => {
  const factors: Factor[] = [
    buildFactor("error_rate", 0.25),
    buildFactor("rage_click_count", 5),
    buildFactor("completion_rate", 0.8),
  ];

  it("returns true when no rules are defined", () => {
    expect(evaluateTargeting(factors, [])).toBe(true);
  });

  it("matches gt operator", () => {
    const rules: TargetingRule[] = [
      { factor: "error_rate", operator: "gt", threshold: 0.1 },
    ];
    expect(evaluateTargeting(factors, rules)).toBe(true);
  });

  it("rejects gt when value equals threshold", () => {
    const rules: TargetingRule[] = [
      { factor: "error_rate", operator: "gt", threshold: 0.25 },
    ];
    expect(evaluateTargeting(factors, rules)).toBe(false);
  });

  it("matches gte when value equals threshold", () => {
    const rules: TargetingRule[] = [
      { factor: "error_rate", operator: "gte", threshold: 0.25 },
    ];
    expect(evaluateTargeting(factors, rules)).toBe(true);
  });

  it("matches lt operator", () => {
    const rules: TargetingRule[] = [
      { factor: "completion_rate", operator: "lt", threshold: 0.9 },
    ];
    expect(evaluateTargeting(factors, rules)).toBe(true);
  });

  it("matches lte when value equals threshold", () => {
    const rules: TargetingRule[] = [
      { factor: "completion_rate", operator: "lte", threshold: 0.8 },
    ];
    expect(evaluateTargeting(factors, rules)).toBe(true);
  });

  it("matches eq operator", () => {
    const rules: TargetingRule[] = [
      { factor: "rage_click_count", operator: "eq", threshold: 5 },
    ];
    expect(evaluateTargeting(factors, rules)).toBe(true);
  });

  it("requires all rules to match (AND logic)", () => {
    const rules: TargetingRule[] = [
      { factor: "error_rate", operator: "gt", threshold: 0.1 },
      { factor: "completion_rate", operator: "lt", threshold: 0.5 },
    ];
    expect(evaluateTargeting(factors, rules)).toBe(false);
  });

  it("returns false when referenced factor is missing", () => {
    const rules: TargetingRule[] = [
      { factor: "nonexistent_factor", operator: "gt", threshold: 0 },
    ];
    expect(evaluateTargeting(factors, rules)).toBe(false);
  });
});
