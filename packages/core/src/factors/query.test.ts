import { describe, it, expect, vi } from "vitest";
import { queryFactors, queryComponentFactors } from "./query";
import type { Factor } from "../types";
import type { ComponentFactorAggregate } from "./query";
import type { FactoredStore } from "../store";

function createMockStore(overrides: Partial<FactoredStore> = {}): FactoredStore {
  return {
    queryFactors: vi.fn().mockResolvedValue([]),
    queryComponentFactors: vi.fn().mockResolvedValue([]),
    ...overrides,
  } as unknown as FactoredStore;
}

describe("queryFactors", () => {
  it("returns factors for a user and component", async () => {
    const expectedFactors: Factor[] = [
      {
        user_id: "user-1",
        component_path: "checkout/form",
        factor_name: "error_rate",
        factor_tier: "alarm",
        value: 0.25,
        computed_at: "2026-04-13T00:00:00Z",
      },
      {
        user_id: "user-1",
        component_path: "checkout/form",
        factor_name: "rage_click_count",
        factor_tier: "diagnostic",
        value: 3,
        computed_at: "2026-04-13T00:00:00Z",
      },
    ];
    const store = createMockStore({
      queryFactors: vi.fn().mockResolvedValue(expectedFactors),
    });

    const result = await queryFactors(store, "user-1", "checkout/form");

    expect(result).toEqual(expectedFactors);
    expect(store.queryFactors).toHaveBeenCalledWith("user-1", "checkout/form");
  });

  it("propagates store errors", async () => {
    const store = createMockStore({
      queryFactors: vi.fn().mockRejectedValue(new Error("connection refused")),
    });

    await expect(queryFactors(store, "user-1", "checkout/form"))
      .rejects.toThrow("connection refused");
  });
});

describe("queryComponentFactors", () => {
  it("returns aggregated factors for a component", async () => {
    const expectedAggregates: ComponentFactorAggregate[] = [
      {
        component_path: "checkout/form",
        factor_name: "error_rate",
        factor_tier: "alarm",
        user_count: 230,
        avg_value: 0.12,
        median_value: 0.08,
        p95_value: 0.45,
        min_value: 0.0,
        max_value: 0.9,
        stddev_value: 0.15,
      },
    ];
    const store = createMockStore({
      queryComponentFactors: vi.fn().mockResolvedValue(expectedAggregates),
    });

    const result = await queryComponentFactors(store, "checkout/form");

    expect(result).toEqual(expectedAggregates);
  });

  it("propagates store errors", async () => {
    const store = createMockStore({
      queryComponentFactors: vi.fn().mockRejectedValue(new Error("timeout")),
    });

    await expect(queryComponentFactors(store, "checkout/form"))
      .rejects.toThrow("timeout");
  });
});
