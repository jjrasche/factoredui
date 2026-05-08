import { describe, it, expect } from "vitest";
import {
  isBindingRef,
  resolveBinding,
  resolveTextWithBindings,
  resolveProps,
} from "./binding.js";

describe("isBindingRef", () => {
  it("recognizes valid binding refs", () => {
    expect(isBindingRef("{user.name}")).toBe(true);
    expect(isBindingRef("{sources.pipeline}")).toBe(true);
    expect(isBindingRef("{flags.showBanner}")).toBe(true);
  });

  it("rejects non-binding strings", () => {
    expect(isBindingRef("plain text")).toBe(false);
    expect(isBindingRef("{invalid ref}")).toBe(false);
    expect(isBindingRef("")).toBe(false);
    expect(isBindingRef("{123invalid}")).toBe(false);
  });
});

describe("resolveBinding", () => {
  const context = {
    user: { name: "Jim", age: 30 },
    sources: { pipeline: [{ id: 1 }, { id: 2 }] },
    flags: { showBanner: true },
  };

  it("resolves nested paths", () => {
    expect(resolveBinding("{user.name}", context)).toBe("Jim");
    expect(resolveBinding("{user.age}", context)).toBe(30);
    expect(resolveBinding("{flags.showBanner}", context)).toBe(true);
  });

  it("returns undefined for missing paths", () => {
    expect(resolveBinding("{user.email}", context)).toBeUndefined();
    expect(resolveBinding("{missing.path}", context)).toBeUndefined();
  });

  it("returns the string unchanged if not a binding ref", () => {
    expect(resolveBinding("plain text", context)).toBe("plain text");
  });

  it("indexes into arrays by numeric segment", () => {
    expect(resolveBinding("{sources.pipeline.0.id}", context)).toBe(1);
    expect(resolveBinding("{sources.pipeline.1.id}", context)).toBe(2);
  });

  it("returns undefined for out-of-range indices", () => {
    expect(resolveBinding("{sources.pipeline.5}", context)).toBeUndefined();
  });
});

describe("resolveTextWithBindings", () => {
  const context = {
    user: { name: "Jim" },
    count: 3,
  };

  it("replaces inline bindings in text", () => {
    expect(resolveTextWithBindings("Hello {user.name}", context)).toBe("Hello Jim");
  });

  it("replaces multiple bindings", () => {
    expect(resolveTextWithBindings("{user.name} has {count} items", context)).toBe("Jim has 3 items");
  });

  it("renders empty string for missing values", () => {
    expect(resolveTextWithBindings("Hi {user.email}", context)).toBe("Hi ");
  });
});

describe("resolveProps", () => {
  it("resolves binding refs in prop values", () => {
    const props = { label: "{user.name}", count: 5, visible: "{flags.show}" };
    const context = { user: { name: "Jim" }, flags: { show: true } };

    const resolved = resolveProps(props, context);
    expect(resolved.label).toBe("Jim");
    expect(resolved.count).toBe(5);
    expect(resolved.visible).toBe(true);
  });

  it("resolves nested objects", () => {
    const props = { style: { color: "{theme.primary}" } };
    const context = { theme: { primary: "#007AFF" } };

    const resolved = resolveProps(props, context);
    expect((resolved.style as Record<string, unknown>).color).toBe("#007AFF");
  });
});
