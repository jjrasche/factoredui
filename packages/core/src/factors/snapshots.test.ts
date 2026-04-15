import { describe, it, expect, vi } from "vitest";
import { queryFactorHistory, queryFactorDelta } from "./snapshots";
import type { FactoredStore } from "../store";

function createMockStore(overrides: Partial<FactoredStore> = {}): FactoredStore {
  return {
    queryFactorHistory: vi.fn().mockResolvedValue([]),
    findClosestSnapshot: vi.fn().mockResolvedValue(null),
    ...overrides,
  } as unknown as FactoredStore;
}

describe("queryFactorHistory", () => {
  it("returns snapshots ordered by time", async () => {
    const snapshots = [
      { factor_name: "error_rate", factor_tier: "alarm", value: 0.3, snapshot_at: "2026-03-10T02:00:00Z" },
      { factor_name: "error_rate", factor_tier: "alarm", value: 0.2, snapshot_at: "2026-03-11T02:00:00Z" },
    ];
    const store = createMockStore({
      queryFactorHistory: vi.fn().mockResolvedValue(snapshots),
    });
    const since = new Date("2026-03-01");

    const result = await queryFactorHistory(store, "user-1", "checkout/form", "error_rate", since);

    expect(result).toEqual(snapshots);
    expect(store.queryFactorHistory).toHaveBeenCalledWith("user-1", "checkout/form", "error_rate", since);
  });

  it("propagates store errors", async () => {
    const store = createMockStore({
      queryFactorHistory: vi.fn().mockRejectedValue(new Error("denied")),
    });
    const since = new Date("2026-03-01");

    await expect(queryFactorHistory(store, "user-1", "checkout/form", "error_rate", since))
      .rejects.toThrow("denied");
  });
});

describe("queryFactorDelta", () => {
  it("returns delta between two snapshots", async () => {
    const beforeSnapshot = { factor_name: "error_rate", factor_tier: "alarm", value: 0.3, snapshot_at: "2026-03-01T02:00:00Z" };
    const afterSnapshot = { factor_name: "error_rate", factor_tier: "alarm", value: 0.1, snapshot_at: "2026-04-01T02:00:00Z" };

    const findClosestSnapshot = vi.fn()
      .mockResolvedValueOnce(beforeSnapshot)
      .mockResolvedValueOnce(afterSnapshot);

    const store = createMockStore({ findClosestSnapshot });

    const result = await queryFactorDelta(
      store, "user-1", "checkout/form", "error_rate",
      new Date("2026-03-01"), new Date("2026-04-01"),
    );

    expect(result).not.toBeNull();
    expect(result!.factor_name).toBe("error_rate");
    expect(result!.before).toBeCloseTo(0.3);
    expect(result!.after).toBeCloseTo(0.1);
    expect(result!.delta).toBeCloseTo(-0.2);
  });

  it("returns null when no before snapshot exists", async () => {
    const afterSnapshot = { factor_name: "error_rate", factor_tier: "alarm", value: 0.1, snapshot_at: "2026-04-01T02:00:00Z" };
    const findClosestSnapshot = vi.fn()
      .mockResolvedValueOnce(null)
      .mockResolvedValueOnce(afterSnapshot);

    const store = createMockStore({ findClosestSnapshot });

    const result = await queryFactorDelta(
      store, "user-1", "checkout/form", "error_rate",
      new Date("2026-03-01"), new Date("2026-04-01"),
    );

    expect(result).toBeNull();
  });

  it("returns null when no after snapshot exists", async () => {
    const beforeSnapshot = { factor_name: "error_rate", factor_tier: "alarm", value: 0.3, snapshot_at: "2026-03-01T02:00:00Z" };
    const findClosestSnapshot = vi.fn()
      .mockResolvedValueOnce(beforeSnapshot)
      .mockResolvedValueOnce(null);

    const store = createMockStore({ findClosestSnapshot });

    const result = await queryFactorDelta(
      store, "user-1", "checkout/form", "error_rate",
      new Date("2026-03-01"), new Date("2026-04-01"),
    );

    expect(result).toBeNull();
  });
});
