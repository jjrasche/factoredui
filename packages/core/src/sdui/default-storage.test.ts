import { describe, it, expect } from "vitest";
import { createSpecStorage, createDataSourceCache, devSignatureVerifier } from "./default-storage";
import type { KVStorage } from "./default-storage";
import type { SignedSpec, AuxiSpec } from "./spec-types";

function createMemoryKV(): KVStorage & { store: Map<string, string> } {
  const store = new Map<string, string>();
  return {
    store,
    getItem: (key) => store.get(key) ?? null,
    setItem: (key, value) => { store.set(key, value); },
    removeItem: (key) => { store.delete(key); },
  };
}

const testSpec: AuxiSpec = {
  spec_version: 1,
  renderer_min: 1,
  root: { id: "root", type: "column" },
};

const testSigned: SignedSpec = {
  spec: testSpec,
  signature: "test-sig",
  signed_at: "2026-04-11T00:00:00Z",
  spec_hash: "test-hash",
};

describe("createSpecStorage", () => {
  it("returns null when no active spec stored", async () => {
    const kv = createMemoryKV();
    const storage = createSpecStorage(kv);

    const result = await storage.loadActive();
    expect(result).toBeNull();
  });

  it("round-trips a signed spec through save and load", async () => {
    const kv = createMemoryKV();
    const storage = createSpecStorage(kv);

    await storage.saveActive(testSigned);
    const loaded = await storage.loadActive();

    expect(loaded).toEqual(testSigned);
  });

  it("uses the configured prefix in storage keys", async () => {
    const kv = createMemoryKV();
    const storage = createSpecStorage(kv, "myapp");

    await storage.saveActive(testSigned);

    expect(kv.store.has("myapp:active-spec")).toBe(true);
    expect(kv.store.has("auxi:active-spec")).toBe(false);
  });
});

describe("createDataSourceCache", () => {
  it("returns null for uncached keys", async () => {
    const kv = createMemoryKV();
    const cache = createDataSourceCache(kv);

    const result = await cache.load("items");
    expect(result).toBeNull();
  });

  it("round-trips data through save and load", async () => {
    const kv = createMemoryKV();
    const cache = createDataSourceCache(kv);
    const data = [{ id: 1, name: "milk" }, { id: 2, name: "eggs" }];

    await cache.save("items", data);
    const loaded = await cache.load("items");

    expect(loaded).toEqual(data);
  });

  it("uses the configured prefix in storage keys", async () => {
    const kv = createMemoryKV();
    const cache = createDataSourceCache(kv, "myapp");

    await cache.save("items", [1, 2, 3]);

    expect(kv.store.has("myapp:source:items")).toBe(true);
    expect(kv.store.has("auxi:source:items")).toBe(false);
  });

  it("isolates keys between different source names", async () => {
    const kv = createMemoryKV();
    const cache = createDataSourceCache(kv);

    await cache.save("items", ["a"]);
    await cache.save("actions", ["b"]);

    expect(await cache.load("items")).toEqual(["a"]);
    expect(await cache.load("actions")).toEqual(["b"]);
  });
});

describe("devSignatureVerifier", () => {
  it("always verifies as true", async () => {
    const result = await devSignatureVerifier.verify("any-hash", "any-sig");
    expect(result).toBe(true);
  });

  it("produces a deterministic hash for the same spec", async () => {
    const hash1 = await devSignatureVerifier.computeHash(testSpec);
    const hash2 = await devSignatureVerifier.computeHash(testSpec);

    expect(hash1).toBe(hash2);
    expect(hash1).toMatch(/^dev:/);
  });

  it("produces different hashes for different specs", async () => {
    const otherSpec: AuxiSpec = {
      spec_version: 2,
      renderer_min: 1,
      root: { id: "other", type: "row" },
    };

    const hash1 = await devSignatureVerifier.computeHash(testSpec);
    const hash2 = await devSignatureVerifier.computeHash(otherSpec);

    expect(hash1).not.toBe(hash2);
  });
});
