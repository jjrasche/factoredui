// @vitest-environment jsdom
import { describe, it, expect, vi } from "vitest";
import { createWebAdapter } from "./web-adapter.js";
import type { CaptureEvent } from "../types.js";

describe("createWebAdapter", () => {
  it("stores and loads session ID via sessionStorage", () => {
    const adapter = createWebAdapter();

    adapter.storeSessionId("test-session-123");
    expect(adapter.loadSessionId()).toBe("test-session-123");

    adapter.clearSessionId();
    expect(adapter.loadSessionId()).toBeNull();
  });

  it("collects session metadata from window/navigator", () => {
    const adapter = createWebAdapter();
    const metadata = adapter.collectSessionMetadata();

    expect(metadata).toHaveProperty("user_agent");
    expect(metadata).toHaveProperty("screen_width");
    expect(metadata).toHaveProperty("viewport_width");
    expect(metadata).toHaveProperty("language");
  });

  it("starts and stops listening without errors", () => {
    const adapter = createWebAdapter();
    const onEvent = vi.fn();

    adapter.startListening(onEvent);
    adapter.stopListening();
  });

  it("emits click events when document is clicked", () => {
    const adapter = createWebAdapter();
    const events: CaptureEvent[] = [];

    adapter.startListening((event) => events.push(event));

    const div = document.createElement("div");
    document.body.appendChild(div);
    div.click();

    // click event mapper fires synchronously
    const clickEvents = events.filter((e) => e.event_type === "click");
    expect(clickEvents.length).toBeGreaterThan(0);
    expect(clickEvents[0].payload).toHaveProperty("x");
    expect(clickEvents[0].payload).toHaveProperty("y");

    adapter.stopListening();
    document.body.removeChild(div);
  });
});
