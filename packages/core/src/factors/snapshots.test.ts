import { describe, it, expect, vi } from "vitest";
import { queryFactorHistory, queryFactorDelta } from "./snapshots";

function createHistoryMock(response: { data: unknown; error: unknown }) {
  return {
    from: vi.fn().mockReturnValue({
      select: vi.fn().mockReturnValue({
        eq: vi.fn().mockReturnValue({
          eq: vi.fn().mockReturnValue({
            eq: vi.fn().mockReturnValue({
              gte: vi.fn().mockReturnValue({
                order: vi.fn().mockResolvedValue(response),
              }),
            }),
          }),
        }),
      }),
    }),
  } as never;
}

function createDeltaMock(responses: Array<{ data: unknown; error: unknown }>) {
  let callIndex = 0;
  return {
    from: vi.fn().mockReturnValue({
      select: vi.fn().mockReturnValue({
        eq: vi.fn().mockReturnValue({
          eq: vi.fn().mockReturnValue({
            eq: vi.fn().mockReturnValue({
              lte: vi.fn().mockReturnValue({
                order: vi.fn().mockReturnValue({
                  limit: vi.fn().mockReturnValue({
                    maybeSingle: vi.fn().mockImplementation(() => {
                      const resp = responses[callIndex] ?? responses[responses.length - 1];
                      callIndex++;
                      return Promise.resolve(resp);
                    }),
                  }),
                }),
              }),
            }),
          }),
        }),
      }),
    }),
  } as never;
}

describe("queryFactorHistory", () => {
  it("returns snapshots ordered by time", async () => {
    const snapshots = [
      { factor_name: "error_rate", factor_tier: "alarm", value: 0.3, snapshot_at: "2026-03-10T02:00:00Z" },
      { factor_name: "error_rate", factor_tier: "alarm", value: 0.2, snapshot_at: "2026-03-11T02:00:00Z" },
    ];
    const client = createHistoryMock({ data: snapshots, error: null });
    const since = new Date("2026-03-01");

    const result = await queryFactorHistory(client, "user-1", "checkout/form", "error_rate", since);

    expect(result).toEqual(snapshots);
    expect(client.from).toHaveBeenCalledWith("factor_snapshots");
  });

  it("throws on supabase error", async () => {
    const client = createHistoryMock({ data: null, error: { message: "denied" } });
    const since = new Date("2026-03-01");

    await expect(queryFactorHistory(client, "user-1", "checkout/form", "error_rate", since))
      .rejects.toThrow("queryFactorHistory failed: denied");
  });
});

describe("queryFactorDelta", () => {
  it("returns delta between two snapshots", async () => {
    const beforeSnapshot = { factor_name: "error_rate", factor_tier: "alarm", value: 0.3, snapshot_at: "2026-03-01T02:00:00Z" };
    const afterSnapshot = { factor_name: "error_rate", factor_tier: "alarm", value: 0.1, snapshot_at: "2026-04-01T02:00:00Z" };

    const client = createDeltaMock([
      { data: beforeSnapshot, error: null },
      { data: afterSnapshot, error: null },
    ]);

    const result = await queryFactorDelta(
      client, "user-1", "checkout/form", "error_rate",
      new Date("2026-03-01"), new Date("2026-04-01"),
    );

    expect(result).not.toBeNull();
    expect(result!.factor_name).toBe("error_rate");
    expect(result!.before).toBeCloseTo(0.3);
    expect(result!.after).toBeCloseTo(0.1);
    expect(result!.delta).toBeCloseTo(-0.2);
  });

  it("returns null when no before snapshot exists", async () => {
    const client = createDeltaMock([
      { data: null, error: null },
      { data: { factor_name: "error_rate", factor_tier: "alarm", value: 0.1, snapshot_at: "2026-04-01T02:00:00Z" }, error: null },
    ]);

    const result = await queryFactorDelta(
      client, "user-1", "checkout/form", "error_rate",
      new Date("2026-03-01"), new Date("2026-04-01"),
    );

    expect(result).toBeNull();
  });

  it("returns null when no after snapshot exists", async () => {
    const client = createDeltaMock([
      { data: { factor_name: "error_rate", factor_tier: "alarm", value: 0.3, snapshot_at: "2026-03-01T02:00:00Z" }, error: null },
      { data: null, error: null },
    ]);

    const result = await queryFactorDelta(
      client, "user-1", "checkout/form", "error_rate",
      new Date("2026-03-01"), new Date("2026-04-01"),
    );

    expect(result).toBeNull();
  });
});
