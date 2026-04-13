import { describe, it, expect } from "vitest";
import { renderHook } from "@testing-library/react";
import { render, screen } from "@testing-library/react";
import {
  Flow,
  Page,
  Component,
  Element,
  useComponentPath,
} from "./path-context.js";
import type { ReactNode } from "react";

describe("context-based path system", () => {
  it("builds path from nested providers", () => {
    function wrapper({ children }: { children: ReactNode }) {
      return (
        <Flow name="onboarding">
          <Page name="review">
            <Component name="photo-grid">
              <Element name="upload-button">
                {children}
              </Element>
            </Component>
          </Page>
        </Flow>
      );
    }

    const { result } = renderHook(() => useComponentPath(), { wrapper });
    expect(result.current).toBe("onboarding/review/photo-grid/upload-button");
  });

  it("returns single segment for one provider", () => {
    function wrapper({ children }: { children: ReactNode }) {
      return <Flow name="dashboard">{children}</Flow>;
    }

    const { result } = renderHook(() => useComponentPath(), { wrapper });
    expect(result.current).toBe("dashboard");
  });

  it("returns empty string with no providers", () => {
    const { result } = renderHook(() => useComponentPath());
    expect(result.current).toBe("");
  });

  it("skips tiers when not all are provided", () => {
    function wrapper({ children }: { children: ReactNode }) {
      return (
        <Flow name="settings">
          <Element name="save-button">
            {children}
          </Element>
        </Flow>
      );
    }

    const { result } = renderHook(() => useComponentPath(), { wrapper });
    expect(result.current).toBe("settings/save-button");
  });

  it("renders data-factored attributes on web", () => {
    function TestComponent() {
      return (
        <Flow name="checkout">
          <Component name="cart">
            <span data-testid="inner">content</span>
          </Component>
        </Flow>
      );
    }

    render(<TestComponent />);
    const inner = screen.getByTestId("inner");

    const flowDiv = inner.closest("[data-factored-flow]");
    expect(flowDiv).not.toBeNull();
    expect(flowDiv!.getAttribute("data-factored-flow")).toBe("checkout");

    const componentDiv = inner.closest("[data-factored-component]");
    expect(componentDiv).not.toBeNull();
    expect(componentDiv!.getAttribute("data-factored-component")).toBe("cart");
  });
});
