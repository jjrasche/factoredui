import type { FactoredStore } from "../store.js";
import type { Spec, SignedSpec } from "./spec-types.js";
import { RENDERER_VERSION } from "./spec-types.js";
import { validateSpec } from "./spec-validator.js";

/**
 * Spec loading: baseline (bundled) → active (local storage) → remote (store).
 * Signature verification is required. Fail closed on invalid signatures.
 *
 * The host app provides:
 * - baselineSpec: bundled in the APK/IPA at build time
 * - loadActive/saveActive: local storage for the current active spec
 * - verifySignature: Ed25519 verification against embedded public key
 */

export interface SpecStorage {
  loadActive(): Promise<SignedSpec | null>;
  saveActive(signed: SignedSpec): Promise<void>;
}

export interface SignatureVerifier {
  verify(specHash: string, signature: string): Promise<boolean>;
  computeHash(spec: Spec): Promise<string>;
}

export interface LoadedSpec {
  spec: Spec;
  source: "remote" | "active" | "baseline";
}

export async function loadSpec(
  store: FactoredStore,
  platform: string,
  baseline: Spec,
  storage: SpecStorage,
  verifier: SignatureVerifier,
): Promise<LoadedSpec> {
  const remote = await fetchRemoteSpec(store, platform);
  if (remote) {
    const validated = await validateSignedSpec(remote, verifier);
    if (validated) {
      await storage.saveActive(remote);
      return { spec: remote.spec, source: "remote" };
    }
  }

  const active = await loadActiveSpec(storage);
  if (active) {
    const validated = await validateSignedSpec(active, verifier);
    if (validated) {
      return { spec: active.spec, source: "active" };
    }
  }

  return { spec: baseline, source: "baseline" };
}

async function validateSignedSpec(
  signed: SignedSpec,
  verifier: SignatureVerifier,
): Promise<boolean> {
  const hashMatches = await verifySpecHash(signed, verifier);
  if (!hashMatches) return false;

  const signatureValid = await verifier.verify(signed.spec_hash, signed.signature);
  if (!signatureValid) return false;

  if (signed.spec.renderer_min > RENDERER_VERSION) return false;

  const schemaErrors = validateSpec(signed.spec);
  return schemaErrors.length === 0;
}

async function verifySpecHash(
  signed: SignedSpec,
  verifier: SignatureVerifier,
): Promise<boolean> {
  const computedHash = await verifier.computeHash(signed.spec);
  return computedHash === signed.spec_hash;
}

async function fetchRemoteSpec(
  store: FactoredStore,
  platform: string,
): Promise<SignedSpec | null> {
  try {
    return await store.loadActiveSpec(platform);
  } catch {
    return null;
  }
}

async function loadActiveSpec(
  storage: SpecStorage,
): Promise<SignedSpec | null> {
  try {
    return await storage.loadActive();
  } catch {
    return null;
  }
}
