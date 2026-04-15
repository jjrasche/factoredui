import { describe, it, expect, vi } from "vitest";
import { evaluateFlag } from "./flags.js";
import type { FactoredStore } from "../store.js";

function createMockStore(overrides: {
  userId?: string | null;
  existingAssignment?: { experiment_id: string; variant_key: string; config: Record<string, unknown> } | null;
  conflicting?: boolean;
  experiment?: { id: string; name: string; component_path: string; targeting_rules: unknown[]; platforms: string[] } | null;
  variants?: { variant_key: string; config: Record<string, unknown>; traffic_percentage: number }[];
} = {}) {
  const writeAssignment = vi.fn().mockResolvedValue(undefined);
  const recordExposure = vi.fn().mockResolvedValue(undefined);

  const store = {
    getCurrentUserId: vi.fn().mockResolvedValue(overrides.userId ?? null),
    getAssignment: vi.fn().mockResolvedValue(overrides.existingAssignment ?? null),
    getRunningExperiment: vi.fn().mockResolvedValue(overrides.experiment ?? null),
    hasConflictingAssignment: vi.fn().mockResolvedValue(overrides.conflicting ?? false),
    getVariants: vi.fn().mockResolvedValue(overrides.variants ?? []),
    writeAssignment,
    recordExposure,
    queryFactors: vi.fn().mockResolvedValue([]),
  } as unknown as FactoredStore;

  return { store, writeAssignment, recordExposure };
}

describe("evaluateFlag", () => {
  it("returns null when user is not authenticated", async () => {
    const { store } = createMockStore({ userId: null });

    const result = await evaluateFlag(store, "test-experiment");

    expect(result).toBeNull();
  });

  it("returns existing assignment without re-assigning", async () => {
    const { store, writeAssignment, recordExposure } = createMockStore({
      userId: "user-123",
      existingAssignment: {
        experiment_id: "exp-1",
        variant_key: "variant-b",
        config: { color: "blue" },
      },
    });

    const result = await evaluateFlag(store, "button-color-test");

    expect(result).toEqual({
      experiment_id: "exp-1",
      variant_key: "variant-b",
      config: { color: "blue" },
    });
    expect(writeAssignment).not.toHaveBeenCalled();
    expect(recordExposure).toHaveBeenCalledOnce();
  });

  it("assigns and returns variant when no prior assignment exists", async () => {
    const { store, writeAssignment, recordExposure } = createMockStore({
      userId: "user-456",
      existingAssignment: null,
      experiment: { id: "exp-2", name: "cta-text", component_path: "test/cta", targeting_rules: [], platforms: [] },
      variants: [
        { variant_key: "control", config: { text: "Submit" }, traffic_percentage: 50 },
        { variant_key: "variant-a", config: { text: "Go!" }, traffic_percentage: 50 },
      ],
    });

    const result = await evaluateFlag(store, "cta-text");

    expect(result).not.toBeNull();
    expect(["control", "variant-a"]).toContain(result!.variant_key);
    expect(writeAssignment).toHaveBeenCalledOnce();
    expect(recordExposure).toHaveBeenCalledOnce();
  });

  it("returns null when no running experiment matches", async () => {
    const { store } = createMockStore({
      userId: "user-789",
      existingAssignment: null,
      experiment: null,
    });

    const result = await evaluateFlag(store, "nonexistent");

    expect(result).toBeNull();
  });

  it("returns null when experiment targets a different platform", async () => {
    const { store } = createMockStore({
      userId: "user-456",
      existingAssignment: null,
      experiment: {
        id: "exp-3",
        name: "ios-only",
        component_path: "test/cta",
        targeting_rules: [],
        platforms: ["ios"],
      },
      variants: [
        { variant_key: "control", config: {}, traffic_percentage: 100 },
      ],
    });

    const result = await evaluateFlag(store, "ios-only", "android");

    expect(result).toBeNull();
  });

  it("returns assignment when experiment targets the current platform", async () => {
    const { store } = createMockStore({
      userId: "user-456",
      existingAssignment: null,
      experiment: {
        id: "exp-4",
        name: "mobile-test",
        component_path: "test/cta",
        targeting_rules: [],
        platforms: ["ios", "android"],
      },
      variants: [
        { variant_key: "control", config: { text: "ok" }, traffic_percentage: 100 },
      ],
    });

    const result = await evaluateFlag(store, "mobile-test", "ios");

    expect(result).not.toBeNull();
    expect(result!.variant_key).toBe("control");
  });

  it("returns assignment when experiment has empty platforms (all platforms)", async () => {
    const { store } = createMockStore({
      userId: "user-456",
      existingAssignment: null,
      experiment: {
        id: "exp-5",
        name: "all-platforms",
        component_path: "test/cta",
        targeting_rules: [],
        platforms: [],
      },
      variants: [
        { variant_key: "control", config: { text: "ok" }, traffic_percentage: 100 },
      ],
    });

    const result = await evaluateFlag(store, "all-platforms", "android");

    expect(result).not.toBeNull();
  });

  it("returns null when user has a conflicting assignment on the same component_path", async () => {
    const { store, writeAssignment } = createMockStore({
      userId: "user-conflict",
      existingAssignment: null,
      conflicting: true,
      experiment: {
        id: "exp-new",
        name: "new-cta-test",
        component_path: "test/cta",
        targeting_rules: [],
        platforms: [],
      },
      variants: [
        { variant_key: "control", config: {}, traffic_percentage: 50 },
        { variant_key: "treatment", config: {}, traffic_percentage: 50 },
      ],
    });

    const result = await evaluateFlag(store, "new-cta-test");

    expect(result).toBeNull();
    expect(writeAssignment).not.toHaveBeenCalled();
  });

  it("assigns when no conflicting assignment exists on the same component_path", async () => {
    const { store, writeAssignment } = createMockStore({
      userId: "user-clean",
      existingAssignment: null,
      conflicting: false,
      experiment: {
        id: "exp-clean",
        name: "clean-test",
        component_path: "test/cta",
        targeting_rules: [],
        platforms: [],
      },
      variants: [
        { variant_key: "control", config: { text: "ok" }, traffic_percentage: 100 },
      ],
    });

    const result = await evaluateFlag(store, "clean-test");

    expect(result).not.toBeNull();
    expect(writeAssignment).toHaveBeenCalledOnce();
  });
});
