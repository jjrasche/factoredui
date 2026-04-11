import { describe, it, expect } from "vitest";
import { validateSpec } from "./spec-validator.js";
import type { AuxiSpec } from "./spec-types.js";

function makeValidSpec(overrides?: Partial<AuxiSpec>): AuxiSpec {
  return {
    spec_version: 1,
    renderer_min: 1,
    root: {
      id: "root",
      type: "column",
      children: [
        { id: "title", type: "text", props: { value: "Hello" } },
        { id: "btn", type: "button", props: { label: "Click" } },
      ],
    },
    ...overrides,
  };
}

describe("validateSpec", () => {
  it("passes a valid spec", () => {
    const errors = validateSpec(makeValidSpec());
    expect(errors).toHaveLength(0);
  });

  it("rejects spec with renderer_min higher than installed", () => {
    const errors = validateSpec(makeValidSpec({ renderer_min: 999 }));
    expect(errors.some((e) => e.includes("renderer_min"))).toBe(true);
  });

  it("rejects unknown component types", () => {
    const spec = makeValidSpec();
    (spec.root.children![0] as { type: string }).type = "carousel";
    const errors = validateSpec(spec);
    expect(errors.some((e) => e.includes("unknown type"))).toBe(true);
  });

  it("rejects duplicate node ids", () => {
    const spec = makeValidSpec();
    spec.root.children![1].id = "title";
    const errors = validateSpec(spec);
    expect(errors.some((e) => e.includes("duplicate"))).toBe(true);
  });

  it("rejects malformed binding refs", () => {
    const spec = makeValidSpec();
    spec.root.children![0].visible = "not a ref";
    const errors = validateSpec(spec);
    expect(errors.some((e) => e.includes("binding ref"))).toBe(true);
  });

  it("accepts valid binding refs in visible", () => {
    const spec = makeValidSpec();
    spec.root.children![0].visible = "{flags.showTitle}";
    const errors = validateSpec(spec);
    expect(errors).toHaveLength(0);
  });

  it("rejects missing spec_version", () => {
    const errors = validateSpec(makeValidSpec({ spec_version: 0 }));
    expect(errors.some((e) => e.includes("spec_version"))).toBe(true);
  });
});
