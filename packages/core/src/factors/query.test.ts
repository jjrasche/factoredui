import { describe, it, expect, vi } from "vitest";
import { queryFactors, queryComponentFactors } from "./query";
import type { Factor } from "../types";
import type { ComponentFactorAggregate } from "./query";

function createMockSupabase(response: { data: unknown; error: unknown }) {
  return {
    from: vi.fn().mockReturnValue({
      select: vi.fn().mockReturnValue({
        eq: vi.fn().mockReturnValue({
          eq: vi.fn().mockResolvedValue(response),
        }),
      }),
    }),
  } as never;
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
    const client = createMockSupabase({ data: expectedFactors, error: null });

    const result = await queryFactors(client, "user-1", "checkout/form");

    expect(result).toEqual(expectedFactors);
    expect(client.from).toHaveBeenCalledWith("v_factors_current");
  });

  it("throws on supabase error", async () => {
    const client = createMockSupabase({
      data: null,
      error: { message: "connection refused" },
    });

    await expect(queryFactors(client, "user-1", "checkout/form"))
      .rejects.toThrow("queryFactors failed: connection refused");
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
    const client = {
      from: vi.fn().mockReturnValue({
        select: vi.fn().mockReturnValue({
          eq: vi.fn().mockResolvedValue({ data: expectedAggregates, error: null }),
        }),
      }),
    } as never;

    const result = await queryComponentFactors(client, "checkout/form");

    expect(result).toEqual(expectedAggregates);
  });

  it("throws on supabase error", async () => {
    const client = {
      from: vi.fn().mockReturnValue({
        select: vi.fn().mockReturnValue({
          eq: vi.fn().mockResolvedValue({
            data: null,
            error: { message: "timeout" },
          }),
        }),
      }),
    } as never;

    await expect(queryComponentFactors(client, "checkout/form"))
      .rejects.toThrow("queryComponentFactors failed: timeout");
  });
});
