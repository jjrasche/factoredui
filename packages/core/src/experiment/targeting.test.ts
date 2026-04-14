import { describe, it, expect } from "vitest";
import { evaluateTargeting } from "./targeting.js";
import type { Factor } from "../types.js";
import type { TargetingRule, DeviceMetadata } from "./targeting.js";

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

  // --- Legacy factor rules (backward compat, no type field) ---

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

  // --- Typed factor rules ---

  it("matches typed factor rule with type='factor'", () => {
    const rules: TargetingRule[] = [
      { type: "factor", factor: "error_rate", operator: "gt", threshold: 0.1 },
    ];
    expect(evaluateTargeting(factors, rules)).toBe(true);
  });

  // --- Metadata targeting rules ---

  const device: DeviceMetadata = {
    os_name: "android",
    os_version: "14",
    manufacturer: "Samsung",
    model: "Galaxy S24",
    app_version: "2.1.0",
    platform: "android",
  };

  it("matches metadata eq (case-insensitive)", () => {
    const rules: TargetingRule[] = [
      { type: "metadata", field: "os_name", operator: "eq", value: "Android" },
    ];
    expect(evaluateTargeting([], rules, device)).toBe(true);
  });

  it("rejects metadata eq when value differs", () => {
    const rules: TargetingRule[] = [
      { type: "metadata", field: "os_name", operator: "eq", value: "ios" },
    ];
    expect(evaluateTargeting([], rules, device)).toBe(false);
  });

  it("matches metadata neq", () => {
    const rules: TargetingRule[] = [
      { type: "metadata", field: "os_name", operator: "neq", value: "ios" },
    ];
    expect(evaluateTargeting([], rules, device)).toBe(true);
  });

  it("matches metadata contains", () => {
    const rules: TargetingRule[] = [
      { type: "metadata", field: "model", operator: "contains", value: "Galaxy" },
    ];
    expect(evaluateTargeting([], rules, device)).toBe(true);
  });

  it("matches metadata gte for version comparison", () => {
    const rules: TargetingRule[] = [
      { type: "metadata", field: "os_version", operator: "gte", value: "14" },
    ];
    expect(evaluateTargeting([], rules, device)).toBe(true);
  });

  it("rejects metadata gte when version is lower", () => {
    const rules: TargetingRule[] = [
      { type: "metadata", field: "os_version", operator: "gte", value: "15" },
    ];
    expect(evaluateTargeting([], rules, device)).toBe(false);
  });

  it("returns false when metadata field is missing from device", () => {
    const rules: TargetingRule[] = [
      { type: "metadata", field: "app_build", operator: "eq", value: "100" },
    ];
    expect(evaluateTargeting([], rules, device)).toBe(false);
  });

  it("returns false for metadata rule when no device metadata provided", () => {
    const rules: TargetingRule[] = [
      { type: "metadata", field: "os_name", operator: "eq", value: "android" },
    ];
    expect(evaluateTargeting([], rules)).toBe(false);
  });

  // --- Mixed rules (factor + metadata) ---

  it("combines factor and metadata rules with AND logic", () => {
    const rules: TargetingRule[] = [
      { factor: "error_rate", operator: "gt", threshold: 0.1 },
      { type: "metadata", field: "os_name", operator: "eq", value: "android" },
    ];
    expect(evaluateTargeting(factors, rules, device)).toBe(true);
  });

  it("rejects when factor matches but metadata does not", () => {
    const rules: TargetingRule[] = [
      { factor: "error_rate", operator: "gt", threshold: 0.1 },
      { type: "metadata", field: "os_name", operator: "eq", value: "ios" },
    ];
    expect(evaluateTargeting(factors, rules, device)).toBe(false);
  });
});
