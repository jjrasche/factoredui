import type { SupabaseClient } from "@supabase/supabase-js";
import type { Spec, SignedSpec } from "./spec-types.js";
import { RENDERER_VERSION } from "./spec-types.js";
import { validateSpec } from "./spec-validator.js";

/**
 * Spec loading: baseline (bundled) → active (local storage) → remote (Supabase).
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
  supabase: SupabaseClient,
  platform: string,
  baseline: Spec,
  storage: SpecStorage,
  verifier: SignatureVerifier,
): Promise<LoadedSpec> {
  const remote = await fetchRemoteSpec(supabase, platform);
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
  supabase: SupabaseClient,
  platform: string,
): Promise<SignedSpec | null> {
  try {
    const { data, error } = await supabase
      .from("ui_active")
      .select("spec_id, ui_specs(*)")
      .eq("platform", platform)
      .single();

    if (error || !data) return null;

    return mapRowToSignedSpec(data);
  } catch {
    return null;
  }
}

function mapRowToSignedSpec(data: unknown): SignedSpec {
  const row = data as {
    spec_id: string;
    ui_specs: {
      component_tree: unknown;
      spec_version: number;
      renderer_min: number;
      spec_hash: string;
      signature: string;
    };
  };

  return {
    spec: {
      spec_version: row.ui_specs.spec_version,
      renderer_min: row.ui_specs.renderer_min,
      root: row.ui_specs.component_tree as Spec["root"],
    },
    signature: row.ui_specs.signature,
    signed_at: "",
    spec_hash: row.ui_specs.spec_hash,
  };
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
