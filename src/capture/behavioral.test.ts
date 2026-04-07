import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import type { AuxiEvent } from "../types.js";
import { createBehavioralDetector } from "./behavioral.js";

describe("createBehavioralDetector", () => {
  let collectedEvents: AuxiEvent[];
  let detector: ReturnType<typeof createBehavioralDetector>;

  beforeEach(() => {
    collectedEvents = [];
    document.body.innerHTML = `
      <div data-auxi-page="test-page">
        <button data-auxi-element="test-btn">Click me</button>
      </div>
    `;
    detector = createBehavioralDetector((event) => collectedEvents.push(event));
    detector.startDetecting();
  });

  afterEach(() => {
    detector.stopDetecting();
    document.body.innerHTML = "";
  });

  describe("rage click detection", () => {
    it("emits rage_click after 3 rapid clicks on same target", () => {
      const button = document.querySelector("button")!;

      button.click();
      button.click();
      button.click();

      const rageEvents = collectedEvents.filter(
        (e) => e.event_type === "rage_click",
      );
      expect(rageEvents.length).toBeGreaterThanOrEqual(1);
      expect(rageEvents[0].component_path).toBe("test-page/test-btn");
    });

    it("does not emit rage_click for 2 clicks", () => {
      const button = document.querySelector("button")!;

      button.click();
      button.click();

      const rageEvents = collectedEvents.filter(
        (e) => e.event_type === "rage_click",
      );
      expect(rageEvents).toHaveLength(0);
    });
  });

  describe("dead click detection", () => {
    it("emits dead_click when no DOM mutation follows click", async () => {
      vi.useFakeTimers();
      const button = document.querySelector("button")!;

      button.click();

      await vi.advanceTimersByTimeAsync(1100);

      const deadEvents = collectedEvents.filter(
        (e) => e.event_type === "dead_click",
      );
      expect(deadEvents.length).toBeGreaterThanOrEqual(1);
      expect(deadEvents[0].component_path).toBe("test-page/test-btn");

      vi.useRealTimers();
    });

    it("does not emit dead_click when DOM mutates after click", async () => {
      vi.useFakeTimers();
      const button = document.querySelector("button")!;

      button.click();

      // Simulate DOM mutation within the wait window
      await vi.advanceTimersByTimeAsync(100);
      const newEl = document.createElement("span");
      document.body.appendChild(newEl);

      await vi.advanceTimersByTimeAsync(1000);

      const deadEvents = collectedEvents.filter(
        (e) => e.event_type === "dead_click",
      );
      expect(deadEvents).toHaveLength(0);

      vi.useRealTimers();
    });
  });
});
