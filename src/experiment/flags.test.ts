import { describe, it, expect, vi } from "vitest";
import { evaluateFlag } from "./flags.js";

function createMockSupabase(overrides: {
  userId?: string | null;
  existingAssignment?: Record<string, unknown> | null;
  experiment?: Record<string, unknown> | null;
  variants?: Record<string, unknown>[];
}) {
  const assignmentInsertMock = vi.fn().mockResolvedValue({ error: null });
  const exposureInsertMock = vi.fn().mockResolvedValue({ error: null });

  return {
    client: {
      auth: {
        getUser: vi.fn().mockResolvedValue({
          data: { user: overrides.userId ? { id: overrides.userId } : null },
        }),
      },
      from: (table: string) => {
        if (table === "experiment_assignments") {
          return {
            select: () => ({
              eq: () => ({
                eq: () => ({
                  eq: () => ({
                    maybeSingle: () =>
                      Promise.resolve({
                        data: overrides.existingAssignment ?? null,
                        error: null,
                      }),
                  }),
                }),
              }),
            }),
            insert: assignmentInsertMock,
          };
        }
        if (table === "experiments") {
          return {
            select: () => ({
              eq: () => ({
                eq: () => ({
                  maybeSingle: () =>
                    Promise.resolve({
                      data: overrides.experiment ?? null,
                      error: null,
                    }),
                }),
              }),
            }),
          };
        }
        if (table === "experiment_variants") {
          return {
            select: () => ({
              eq: () => ({
                order: () =>
                  Promise.resolve({
                    data: overrides.variants ?? [],
                    error: null,
                  }),
              }),
            }),
          };
        }
        if (table === "experiment_exposures") {
          return { insert: exposureInsertMock };
        }
        return {};
      },
    } as never,
    assignmentInsertMock,
    exposureInsertMock,
  };
}

describe("evaluateFlag", () => {
  it("returns null when user is not authenticated", async () => {
    const { client } = createMockSupabase({ userId: null });

    const result = await evaluateFlag(client, "test-experiment");

    expect(result).toBeNull();
  });

  it("returns existing assignment without re-assigning", async () => {
    const { client, assignmentInsertMock, exposureInsertMock } = createMockSupabase({
      userId: "user-123",
      existingAssignment: {
        experiment_id: "exp-1",
        variant_key: "variant-b",
        experiment_variants: { config: { color: "blue" } },
      },
    });

    const result = await evaluateFlag(client, "button-color-test");

    expect(result).toEqual({
      experiment_id: "exp-1",
      variant_key: "variant-b",
      config: { color: "blue" },
    });
    expect(assignmentInsertMock).not.toHaveBeenCalled();
    expect(exposureInsertMock).toHaveBeenCalledOnce();
  });

  it("assigns and returns variant when no prior assignment exists", async () => {
    const { client, assignmentInsertMock, exposureInsertMock } = createMockSupabase({
      userId: "user-456",
      existingAssignment: null,
      experiment: { id: "exp-2", name: "cta-text" },
      variants: [
        { variant_key: "control", config: { text: "Submit" }, traffic_percentage: 50 },
        { variant_key: "variant-a", config: { text: "Go!" }, traffic_percentage: 50 },
      ],
    });

    const result = await evaluateFlag(client, "cta-text");

    expect(result).not.toBeNull();
    expect(["control", "variant-a"]).toContain(result!.variant_key);
    expect(assignmentInsertMock).toHaveBeenCalledOnce();
    expect(exposureInsertMock).toHaveBeenCalledOnce();
  });

  it("returns null when no running experiment matches", async () => {
    const { client } = createMockSupabase({
      userId: "user-789",
      existingAssignment: null,
      experiment: null,
    });

    const result = await evaluateFlag(client, "nonexistent");

    expect(result).toBeNull();
  });
});
