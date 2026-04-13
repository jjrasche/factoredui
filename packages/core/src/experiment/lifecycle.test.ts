import { describe, it, expect, vi } from "vitest";
import { createExperiment, startExperiment } from "./lifecycle.js";
import type { ExperimentDefinition } from "./lifecycle.js";

function buildValidDefinition(overrides: Partial<ExperimentDefinition> = {}): ExperimentDefinition {
  return {
    name: "cta-color-test",
    component_path: "checkout/cta",
    variants: [
      { variant_key: "control", config: { color: "blue" }, traffic_percentage: 50 },
      { variant_key: "treatment", config: { color: "green" }, traffic_percentage: 50 },
    ],
    ...overrides,
  };
}

function createMockSupabase(overrides: {
  insertExperimentResult?: { data: Record<string, unknown> | null; error: { message: string } | null };
  insertVariantsResult?: { error: { message: string } | null };
  updateResult?: { error: { message: string } | null };
} = {}) {
  const insertVariantsMock = vi.fn().mockResolvedValue(
    overrides.insertVariantsResult ?? { error: null },
  );

  return {
    client: {
      from: (table: string) => {
        if (table === "experiments") {
          return {
            insert: () => ({
              select: () => ({
                single: () => Promise.resolve(
                  overrides.insertExperimentResult ?? {
                    data: { id: "exp-1", name: "cta-color-test", status: "draft", component_path: "checkout/cta" },
                    error: null,
                  },
                ),
              }),
            }),
            update: () => ({
              eq: () => ({
                eq: () => Promise.resolve(overrides.updateResult ?? { error: null }),
              }),
            }),
          };
        }
        if (table === "experiment_variants") {
          return { insert: insertVariantsMock };
        }
        return {};
      },
    } as never,
    insertVariantsMock,
  };
}

describe("createExperiment", () => {
  it("validates traffic percentages sum to 100", async () => {
    const { client } = createMockSupabase();
    const definition = buildValidDefinition({
      variants: [
        { variant_key: "control", config: {}, traffic_percentage: 50 },
        { variant_key: "treatment", config: {}, traffic_percentage: 40 },
      ],
    });

    await expect(createExperiment(client, definition)).rejects.toThrow(
      "Traffic percentages must sum to 100, got 90",
    );
  });

  it("requires a control variant", async () => {
    const { client } = createMockSupabase();
    const definition = buildValidDefinition({
      variants: [
        { variant_key: "a", config: {}, traffic_percentage: 50 },
        { variant_key: "b", config: {}, traffic_percentage: 50 },
      ],
    });

    await expect(createExperiment(client, definition)).rejects.toThrow(
      "Experiment requires a 'control' variant",
    );
  });

  it("requires at least 2 variants", async () => {
    const { client } = createMockSupabase();
    const definition = buildValidDefinition({
      variants: [
        { variant_key: "control", config: {}, traffic_percentage: 100 },
      ],
    });

    await expect(createExperiment(client, definition)).rejects.toThrow(
      "Experiment requires at least 2 variants",
    );
  });

  it("inserts experiment and variants on valid definition", async () => {
    const { client, insertVariantsMock } = createMockSupabase();
    const definition = buildValidDefinition();

    const result = await createExperiment(client, definition);

    expect(result.id).toBe("exp-1");
    expect(result.status).toBe("draft");
    expect(insertVariantsMock).toHaveBeenCalledOnce();
  });

  it("propagates experiment insert errors", async () => {
    const { client } = createMockSupabase({
      insertExperimentResult: { data: null, error: { message: "duplicate name" } },
    });

    await expect(createExperiment(client, buildValidDefinition())).rejects.toThrow(
      "insertExperiment failed: duplicate name",
    );
  });

  it("propagates variant insert errors", async () => {
    const { client } = createMockSupabase({
      insertVariantsResult: { error: { message: "fk violation" } },
    });

    await expect(createExperiment(client, buildValidDefinition())).rejects.toThrow(
      "insertVariants failed: fk violation",
    );
  });
});

describe("startExperiment", () => {
  it("transitions experiment to running", async () => {
    const { client } = createMockSupabase();

    await expect(startExperiment(client, "exp-1")).resolves.toBeUndefined();
  });

  it("propagates errors", async () => {
    const { client } = createMockSupabase({
      updateResult: { error: { message: "not found" } },
    });

    await expect(startExperiment(client, "exp-1")).rejects.toThrow(
      "startExperiment failed: not found",
    );
  });
});
