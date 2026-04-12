import { describe, it, expect, vi } from "vitest";
import { resolveAllSources, type DataSourceCache } from "./data-source.js";
import type { DataSourceRegistry } from "./spec-types.js";

function createMockCache(): DataSourceCache {
  const store = new Map<string, unknown>();
  return {
    load: vi.fn(async (key: string) => store.get(key) ?? null),
    save: vi.fn(async (key: string, data: unknown) => { store.set(key, data); }),
  };
}

describe("resolveAllSources", () => {
  it("fetches all sources and returns data", async () => {
    const registry: DataSourceRegistry = {
      users: { fetch: async () => [{ id: 1, name: "Jim" }] },
      config: { fetch: async () => ({ theme: "dark" }) },
    };

    const { sources, errors } = await resolveAllSources(registry, createMockCache());

    expect(sources.users).toEqual([{ id: 1, name: "Jim" }]);
    expect(sources.config).toEqual({ theme: "dark" });
    expect(Object.keys(errors)).toHaveLength(0);
  });

  it("returns cached data when fetch fails", async () => {
    const cache = createMockCache();
    await cache.save("users", [{ id: 1, name: "cached" }]);

    const registry: DataSourceRegistry = {
      users: {
        fetch: async () => { throw new Error("network error"); },
        cache: "local",
      },
    };

    const { sources, errors } = await resolveAllSources(registry, cache);

    expect(sources.users).toEqual([{ id: 1, name: "cached" }]);
    expect(Object.keys(errors)).toHaveLength(0);
  });

  it("records error when fetch fails and no cache", async () => {
    const registry: DataSourceRegistry = {
      users: { fetch: async () => { throw new Error("offline"); } },
    };

    const { sources, errors } = await resolveAllSources(registry, createMockCache());

    expect(sources.users).toBeNull();
    expect(errors.users).toContain("fetch failed");
  });

  it("saves to cache on successful fetch", async () => {
    const cache = createMockCache();
    const registry: DataSourceRegistry = {
      users: {
        fetch: async () => [{ id: 1 }],
        cache: "local",
      },
    };

    await resolveAllSources(registry, cache);

    expect(cache.save).toHaveBeenCalledWith("users", [{ id: 1 }]);
  });

  it("enforces maxItems on array data", async () => {
    const registry: DataSourceRegistry = {
      items: {
        fetch: async () => Array.from({ length: 200 }, (_, i) => ({ id: i })),
        maxItems: 100,
      },
    };

    const { sources } = await resolveAllSources(registry, createMockCache());

    expect((sources.items as unknown[]).length).toBe(100);
  });
});
