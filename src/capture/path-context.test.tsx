import { describe, it, expect } from "vitest";
import { renderHook } from "@testing-library/react";
import { render, screen } from "@testing-library/react";
import {
  AuxiFlow,
  AuxiPage,
  AuxiComponent,
  AuxiElement,
  useComponentPath,
} from "./path-context.js";
import type { ReactNode } from "react";

describe("context-based path system", () => {
  it("builds path from nested providers", () => {
    function wrapper({ children }: { children: ReactNode }) {
      return (
        <AuxiFlow name="onboarding">
          <AuxiPage name="review">
            <AuxiComponent name="photo-grid">
              <AuxiElement name="upload-button">
                {children}
              </AuxiElement>
            </AuxiComponent>
          </AuxiPage>
        </AuxiFlow>
      );
    }

    const { result } = renderHook(() => useComponentPath(), { wrapper });
    expect(result.current).toBe("onboarding/review/photo-grid/upload-button");
  });

  it("returns single segment for one provider", () => {
    function wrapper({ children }: { children: ReactNode }) {
      return <AuxiFlow name="dashboard">{children}</AuxiFlow>;
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
        <AuxiFlow name="settings">
          <AuxiElement name="save-button">
            {children}
          </AuxiElement>
        </AuxiFlow>
      );
    }

    const { result } = renderHook(() => useComponentPath(), { wrapper });
    expect(result.current).toBe("settings/save-button");
  });

  it("renders data-auxi attributes on web", () => {
    function TestComponent() {
      return (
        <AuxiFlow name="checkout">
          <AuxiComponent name="cart">
            <span data-testid="inner">content</span>
          </AuxiComponent>
        </AuxiFlow>
      );
    }

    render(<TestComponent />);
    const inner = screen.getByTestId("inner");

    const flowDiv = inner.closest("[data-auxi-flow]");
    expect(flowDiv).not.toBeNull();
    expect(flowDiv!.getAttribute("data-auxi-flow")).toBe("checkout");

    const componentDiv = inner.closest("[data-auxi-component]");
    expect(componentDiv).not.toBeNull();
    expect(componentDiv!.getAttribute("data-auxi-component")).toBe("cart");
  });
});
