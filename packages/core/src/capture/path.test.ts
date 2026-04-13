import { describe, it, expect, beforeEach } from "vitest";
import { resolveComponentPath } from "./path.js";

describe("resolveComponentPath", () => {
  beforeEach(() => {
    document.body.innerHTML = "";
  });

  it("builds path from nested data-factored attributes", () => {
    document.body.innerHTML = `
      <div data-factored-flow="onboarding">
        <div data-factored-page="review">
          <div data-factored-component="photo-grid">
            <button data-factored-element="upload-button">Upload</button>
          </div>
        </div>
      </div>
    `;

    const button = document.querySelector("button")!;
    expect(resolveComponentPath(button)).toBe(
      "onboarding/review/photo-grid/upload-button",
    );
  });

  it("returns partial path when not all tiers are present", () => {
    document.body.innerHTML = `
      <div data-factored-page="settings">
        <button data-factored-element="save">Save</button>
      </div>
    `;

    const button = document.querySelector("button")!;
    expect(resolveComponentPath(button)).toBe("settings/save");
  });

  it("returns unknown when no factored attributes exist", () => {
    document.body.innerHTML = `<button>Bare</button>`;

    const button = document.querySelector("button")!;
    expect(resolveComponentPath(button)).toBe("unknown");
  });

  it("uses closest ancestor for each tier", () => {
    document.body.innerHTML = `
      <div data-factored-component="outer">
        <div data-factored-component="inner">
          <span>Text</span>
        </div>
      </div>
    `;

    const span = document.querySelector("span")!;
    expect(resolveComponentPath(span)).toBe("inner");
  });

  it("collects attributes from same element", () => {
    document.body.innerHTML = `
      <button data-factored-page="home" data-factored-element="cta">Go</button>
    `;

    const button = document.querySelector("button")!;
    expect(resolveComponentPath(button)).toBe("home/cta");
  });
});
