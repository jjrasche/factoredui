import { describe, it, expect, vi } from "vitest";
import { evaluateFlag } from "./flags.js";

function createMockSupabase(overrides: {
  userId?: string | null;
  existingAssignment?: Record<string, unknown> | null;
  conflictingAssignments?: Record<string, unknown>[];
  experiment?: Record<string, unknown> | null;
  variants?: Record<string, unknown>[];
}) {
  const assignmentInsertMock = vi.fn().mockResolvedValue({ error: null });
  const exposureInsertMock = vi.fn().mockResolvedValue({ error: null });

  // Builds a chainable query mock where every method returns the same chain
  // and terminal methods (maybeSingle, limit) resolve with the given data.
  function buildChain(terminalData: { single?: unknown; list?: unknown[] }) {
    const chain: Record<string, unknown> = {};
    const self = () => chain;
    chain.eq = self;
    chain.neq = self;
    chain.maybeSingle = () => Promise.resolve({ data: terminalData.single ?? null, error: null });
    chain.limit = () => Promise.resolve({ data: terminalData.list ?? [], error: null });
    return chain;
  }

  // Track which select call on experiment_assignments we're serving:
  // first = fetchAssignment (maybeSingle), second = hasConflictingAssignment (limit)
  let assignmentSelectCount = 0;

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
            select: () => {
              assignmentSelectCount++;
              if (assignmentSelectCount === 1) {
                return buildChain({ single: overrides.existingAssignment ?? null });
              }
              return buildChain({ list: overrides.conflictingAssignments ?? [] });
            },
            insert: assignmentInsertMock,
          };
        }
        if (table === "experiments") {
          return {
            select: () => buildChain({ single: overrides.experiment ?? null }),
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
        if (table === "factors") {
          return { select: () => buildChain({ list: [] }) };
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
      experiment: { id: "exp-2", name: "cta-text", component_path: "test/cta", targeting_rules: [], platforms: [] },
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

  it("returns null when experiment targets a different platform", async () => {
    const { client } = createMockSupabase({
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

    const result = await evaluateFlag(client, "ios-only", "android");

    expect(result).toBeNull();
  });

  it("returns assignment when experiment targets the current platform", async () => {
    const { client } = createMockSupabase({
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

    const result = await evaluateFlag(client, "mobile-test", "ios");

    expect(result).not.toBeNull();
    expect(result!.variant_key).toBe("control");
  });

  it("returns assignment when experiment has empty platforms (all platforms)", async () => {
    const { client } = createMockSupabase({
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

    const result = await evaluateFlag(client, "all-platforms", "android");

    expect(result).not.toBeNull();
  });

  it("returns null when user has a conflicting assignment on the same component_path", async () => {
    const { client, assignmentInsertMock } = createMockSupabase({
      userId: "user-conflict",
      existingAssignment: null,
      conflictingAssignments: [
        { experiment_id: "other-exp", experiments: { id: "other-exp", status: "running", component_path: "test/cta" } },
      ],
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

    const result = await evaluateFlag(client, "new-cta-test");

    expect(result).toBeNull();
    expect(assignmentInsertMock).not.toHaveBeenCalled();
  });

  it("assigns when no conflicting assignment exists on the same component_path", async () => {
    const { client, assignmentInsertMock } = createMockSupabase({
      userId: "user-clean",
      existingAssignment: null,
      conflictingAssignments: [],
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

    const result = await evaluateFlag(client, "clean-test");

    expect(result).not.toBeNull();
    expect(assignmentInsertMock).toHaveBeenCalledOnce();
  });
});
