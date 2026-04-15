import { describe, it, expect, vi } from "vitest";
import { createExperiment, startExperiment } from "./lifecycle.js";
import type { ExperimentDefinition } from "./lifecycle.js";
import type { FactoredStore } from "../store.js";

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

function createMockStore(overrides: {
  insertExperimentResult?: { id: string; name: string; status: string; component_path: string };
  insertExperimentError?: Error;
  insertVariantsError?: Error;
  startExperimentError?: Error;
} = {}) {
  const insertVariants = overrides.insertVariantsError
    ? vi.fn().mockRejectedValue(overrides.insertVariantsError)
    : vi.fn().mockResolvedValue(undefined);

  const insertExperiment = overrides.insertExperimentError
    ? vi.fn().mockRejectedValue(overrides.insertExperimentError)
    : vi.fn().mockResolvedValue(
        overrides.insertExperimentResult ?? {
          id: "exp-1", name: "cta-color-test", status: "draft", component_path: "checkout/cta",
        },
      );

  const startExperimentFn = overrides.startExperimentError
    ? vi.fn().mockRejectedValue(overrides.startExperimentError)
    : vi.fn().mockResolvedValue(undefined);

  const store = {
    insertExperiment,
    insertVariants,
    startExperiment: startExperimentFn,
  } as unknown as FactoredStore;

  return { store, insertVariants };
}

describe("createExperiment", () => {
  it("validates traffic percentages sum to 100", async () => {
    const { store } = createMockStore();
    const definition = buildValidDefinition({
      variants: [
        { variant_key: "control", config: {}, traffic_percentage: 50 },
        { variant_key: "treatment", config: {}, traffic_percentage: 40 },
      ],
    });

    await expect(createExperiment(store, definition)).rejects.toThrow(
      "Traffic percentages must sum to 100, got 90",
    );
  });

  it("requires a control variant", async () => {
    const { store } = createMockStore();
    const definition = buildValidDefinition({
      variants: [
        { variant_key: "a", config: {}, traffic_percentage: 50 },
        { variant_key: "b", config: {}, traffic_percentage: 50 },
      ],
    });

    await expect(createExperiment(store, definition)).rejects.toThrow(
      "Experiment requires a 'control' variant",
    );
  });

  it("requires at least 2 variants", async () => {
    const { store } = createMockStore();
    const definition = buildValidDefinition({
      variants: [
        { variant_key: "control", config: {}, traffic_percentage: 100 },
      ],
    });

    await expect(createExperiment(store, definition)).rejects.toThrow(
      "Experiment requires at least 2 variants",
    );
  });

  it("inserts experiment and variants on valid definition", async () => {
    const { store, insertVariants } = createMockStore();
    const definition = buildValidDefinition();

    const result = await createExperiment(store, definition);

    expect(result.id).toBe("exp-1");
    expect(result.status).toBe("draft");
    expect(insertVariants).toHaveBeenCalledOnce();
  });

  it("propagates experiment insert errors", async () => {
    const { store } = createMockStore({
      insertExperimentError: new Error("duplicate name"),
    });

    await expect(createExperiment(store, buildValidDefinition())).rejects.toThrow(
      "duplicate name",
    );
  });

  it("propagates variant insert errors", async () => {
    const { store } = createMockStore({
      insertVariantsError: new Error("fk violation"),
    });

    await expect(createExperiment(store, buildValidDefinition())).rejects.toThrow(
      "fk violation",
    );
  });
});

describe("startExperiment", () => {
  it("transitions experiment to running", async () => {
    const { store } = createMockStore();

    await expect(startExperiment(store, "exp-1")).resolves.toBeUndefined();
  });

  it("propagates errors", async () => {
    const { store } = createMockStore({
      startExperimentError: new Error("not found or not in draft status"),
    });

    await expect(startExperiment(store, "exp-1")).rejects.toThrow(
      "not found or not in draft status",
    );
  });
});
