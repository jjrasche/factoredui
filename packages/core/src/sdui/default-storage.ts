import type { Spec, SignedSpec } from "./spec-types.js";
import type { SpecStorage, SignatureVerifier } from "./spec-loader.js";
import type { DataSourceCache } from "./data-source.js";

/**
 * Generic key-value storage interface.
 * AsyncStorage (RN), localStorage (web), or any compatible backend.
 */
export interface KVStorage {
  getItem(key: string): Promise<string | null> | string | null;
  setItem(key: string, value: string): Promise<void> | void;
  removeItem(key: string): Promise<void> | void;
}

/**
 * Creates a SpecStorage backed by any KV store.
 * Stores the active signed spec as JSON under a single key.
 */
export function createSpecStorage(kv: KVStorage, prefix = "factoredui"): SpecStorage {
  const key = `${prefix}:active-spec`;

  return {
    async loadActive(): Promise<SignedSpec | null> {
      const raw = await kv.getItem(key);
      if (!raw) return null;
      return JSON.parse(raw) as SignedSpec;
    },

    async saveActive(signed: SignedSpec): Promise<void> {
      await kv.setItem(key, JSON.stringify(signed));
    },
  };
}

/**
 * Creates a DataSourceCache backed by any KV store.
 * Caches resolved source data for offline rendering.
 */
export function createDataSourceCache(kv: KVStorage, prefix = "factoredui"): DataSourceCache {
  const keyPrefix = `${prefix}:source:`;

  return {
    async load(sourceKey: string): Promise<unknown | null> {
      const raw = await kv.getItem(keyPrefix + sourceKey);
      if (!raw) return null;
      return JSON.parse(raw);
    },

    async save(sourceKey: string, data: unknown): Promise<void> {
      await kv.setItem(keyPrefix + sourceKey, JSON.stringify(data));
    },
  };
}

/**
 * Dev-only signature verifier. Always passes.
 * Replace with Ed25519 verification before OTA spec delivery.
 */
export const devSignatureVerifier: SignatureVerifier = {
  async verify(_specHash: string, _signature: string): Promise<boolean> {
    return true;
  },

  async computeHash(spec: Spec): Promise<string> {
    const json = JSON.stringify(spec);
    let hash = 0;
    for (let i = 0; i < json.length; i++) {
      const char = json.charCodeAt(i);
      hash = ((hash << 5) - hash + char) | 0;
    }
    return `dev:${hash.toString(16)}`;
  },
};
