import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import type { ObserveEvent } from "../types.js";
import { createEventListener } from "./listener.js";

describe("createEventListener throttling", () => {
  let collectedEvents: ObserveEvent[];
  let listener: ReturnType<typeof createEventListener>;
  let dateNowSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    collectedEvents = [];
    document.body.innerHTML = `<div data-observe-page="test-page"></div>`;
    listener = createEventListener((event) => collectedEvents.push(event));
    dateNowSpy = vi.spyOn(Date, "now");
    listener.startListening();
  });

  afterEach(() => {
    listener.stopListening();
    dateNowSpy.mockRestore();
    document.body.innerHTML = "";
  });

  function dispatchScroll(): void {
    window.dispatchEvent(new Event("scroll"));
  }

  function collectScrollEvents(): ObserveEvent[] {
    return collectedEvents.filter((e) => e.event_type === "scroll");
  }

  it("fires the first scroll event immediately", () => {
    dateNowSpy.mockReturnValue(1000);

    dispatchScroll();

    expect(collectScrollEvents()).toHaveLength(1);
  });

  it("drops scroll events within the 100ms throttle window", () => {
    dateNowSpy.mockReturnValue(1000);
    dispatchScroll();

    dateNowSpy.mockReturnValue(1050);
    dispatchScroll();

    dateNowSpy.mockReturnValue(1099);
    dispatchScroll();

    expect(collectScrollEvents()).toHaveLength(1);
  });

  it("allows scroll events after the 100ms throttle window", () => {
    dateNowSpy.mockReturnValue(1000);
    dispatchScroll();

    dateNowSpy.mockReturnValue(1100);
    dispatchScroll();

    expect(collectScrollEvents()).toHaveLength(2);
  });

  it("resets the throttle window after each fired event", () => {
    dateNowSpy.mockReturnValue(1000);
    dispatchScroll();

    dateNowSpy.mockReturnValue(1100);
    dispatchScroll();

    // Within 100ms of second fire — should be dropped
    dateNowSpy.mockReturnValue(1150);
    dispatchScroll();

    // After 100ms of second fire — should pass
    dateNowSpy.mockReturnValue(1200);
    dispatchScroll();

    expect(collectScrollEvents()).toHaveLength(3);
  });

  it("throttles resize events independently of scroll", () => {
    dateNowSpy.mockReturnValue(1000);
    dispatchScroll();
    window.dispatchEvent(new Event("resize"));

    // Both fire at t=1000
    expect(collectScrollEvents()).toHaveLength(1);
    expect(collectedEvents.filter((e) => e.event_type === "resize")).toHaveLength(1);

    // At t=1050, both should be throttled
    dateNowSpy.mockReturnValue(1050);
    dispatchScroll();
    window.dispatchEvent(new Event("resize"));

    expect(collectScrollEvents()).toHaveLength(1);
    expect(collectedEvents.filter((e) => e.event_type === "resize")).toHaveLength(1);
  });
});
