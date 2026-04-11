import { describe, it, expect, vi } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useSourceData } from "./use-source-data";
import type { DataSourceRegistry } from "./spec-types";
import type { DataSourceCache } from "./data-source";

const nullCache: DataSourceCache = {
  load: async () => null,
  save: async () => {},
};

describe("useSourceData", () => {
  it("starts with empty data and sourcesLoaded false", () => {
    const buildRegistry = () => ({});
    const { result } = renderHook(() => useSourceData(buildRegistry, nullCache));

    expect(result.current.sourceData).toEqual({});
    expect(result.current.errors).toEqual({});
    expect(result.current.sourcesLoaded).toBe(false);
  });

  it("resolves sources and sets sourcesLoaded on refreshSources", async () => {
    const registry: DataSourceRegistry = {
      items: { fetch: async () => ["milk", "eggs"] },
    };
    const buildRegistry = () => registry;

    const { result } = renderHook(() => useSourceData(buildRegistry, nullCache));

    await act(async () => {
      await result.current.refreshSources();
    });

    expect(result.current.sourcesLoaded).toBe(true);
    expect(result.current.sourceData).toEqual({ items: ["milk", "eggs"] });
    expect(result.current.errors).toEqual({});
  });

  it("surfaces errors for failed sources without crashing", async () => {
    const registry: DataSourceRegistry = {
      good: { fetch: async () => [1, 2] },
      bad: { fetch: async () => { throw new Error("network down"); } },
    };
    const buildRegistry = () => registry;

    const { result } = renderHook(() => useSourceData(buildRegistry, nullCache));

    await act(async () => {
      await result.current.refreshSources();
    });

    expect(result.current.sourcesLoaded).toBe(true);
    expect(result.current.sourceData.good).toEqual([1, 2]);
    expect(result.current.sourceData.bad).toBeNull();
    expect(result.current.errors.bad).toMatch(/fetch failed/);
  });

  it("memoizes registry from buildRegistry function", () => {
    const fetchSpy = vi.fn(async () => ["data"]);
    const registry: DataSourceRegistry = { items: { fetch: fetchSpy } };
    const buildRegistry = vi.fn(() => registry);

    const { rerender } = renderHook(() => useSourceData(buildRegistry, nullCache));
    rerender();

    expect(buildRegistry).toHaveBeenCalledTimes(1);
  });
});
