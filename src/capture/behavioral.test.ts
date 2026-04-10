import { describe, it, expect } from "vitest";
import {
  createRageClickState,
  recordClickAndDetectRage,
  createScrollState,
  detectScrollReversal,
} from "./behavioral.js";

describe("recordClickAndDetectRage", () => {
  it("returns true after 3 rapid clicks on same path", () => {
    const state = createRageClickState();
    const now = 1000;

    expect(recordClickAndDetectRage(state, "page/button", now)).toBe(false);
    expect(recordClickAndDetectRage(state, "page/button", now + 100)).toBe(false);
    expect(recordClickAndDetectRage(state, "page/button", now + 200)).toBe(true);
  });

  it("resets when target path changes", () => {
    const state = createRageClickState();
    const now = 1000;

    recordClickAndDetectRage(state, "page/button-a", now);
    recordClickAndDetectRage(state, "page/button-a", now + 100);
    // Switch target — count resets
    recordClickAndDetectRage(state, "page/button-b", now + 200);
    expect(recordClickAndDetectRage(state, "page/button-b", now + 300)).toBe(false);
  });

  it("does not trigger when clicks are outside time window", () => {
    const state = createRageClickState();

    expect(recordClickAndDetectRage(state, "page/btn", 1000)).toBe(false);
    expect(recordClickAndDetectRage(state, "page/btn", 1100)).toBe(false);
    // 600ms after first click — outside 500ms window, first click pruned
    expect(recordClickAndDetectRage(state, "page/btn", 1600)).toBe(false);
  });
});

describe("detectScrollReversal", () => {
  it("detects rapid direction change", () => {
    const state = createScrollState();

    // Establish initial direction (down)
    expect(detectScrollReversal(state, 100, 1000)).toBeNull();
    // First reversal (up) — sets lastDirectionChangeAt
    expect(detectScrollReversal(state, 50, 1100)).toBeNull();
    // Second reversal (down) within 300ms of the first — triggers
    expect(detectScrollReversal(state, 100, 1200)).toBe("down");
  });

  it("does not trigger on slow direction change", () => {
    const state = createScrollState();

    detectScrollReversal(state, 100, 1000);
    detectScrollReversal(state, 200, 1100);
    // Reverse after 400ms — outside 300ms window
    expect(detectScrollReversal(state, 150, 1500)).toBeNull();
  });

  it("returns null on first scroll", () => {
    const state = createScrollState();
    expect(detectScrollReversal(state, 100, 1000)).toBeNull();
  });
});
