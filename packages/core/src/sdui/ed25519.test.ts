import { describe, it, expect } from "vitest";
import { createEd25519Verifier, createEd25519Signer, generateEd25519Keypair } from "./ed25519.js";
import type { Spec } from "./spec-types.js";

const testSpec: Spec = {
  spec_version: 1,
  renderer_min: 1,
  root: { id: "root", type: "column" },
};

const altSpec: Spec = {
  spec_version: 2,
  renderer_min: 1,
  root: { id: "alt", type: "row" },
};

// Ed25519 via Web Crypto requires Node 20+ or compatible runtime
const hasEd25519 = await (async () => {
  try {
    await crypto.subtle.generateKey("Ed25519", true, ["sign", "verify"]);
    return true;
  } catch {
    return false;
  }
})();

describe.skipIf(!hasEd25519)("Ed25519 signing", () => {
  it("generates a valid keypair", async () => {
    const { publicKey, privateKey } = await generateEd25519Keypair();

    expect(publicKey).toBeTruthy();
    expect(privateKey).toBeTruthy();
    expect(publicKey.length).toBeGreaterThan(10);
    expect(privateKey.length).toBeGreaterThan(10);
  });

  it("round-trips sign and verify", async () => {
    const { publicKey, privateKey } = await generateEd25519Keypair();
    const signer = createEd25519Signer(privateKey);
    const verifier = createEd25519Verifier(publicKey);

    const { spec_hash, signature } = await signer.signSpec(testSpec);

    const isValid = await verifier.verify(spec_hash, signature);
    expect(isValid).toBe(true);
  });

  it("rejects tampered spec hash", async () => {
    const { publicKey, privateKey } = await generateEd25519Keypair();
    const signer = createEd25519Signer(privateKey);
    const verifier = createEd25519Verifier(publicKey);

    const { signature } = await signer.signSpec(testSpec);

    const isValid = await verifier.verify("tampered-hash", signature);
    expect(isValid).toBe(false);
  });

  it("rejects signature from a different key", async () => {
    const keypairA = await generateEd25519Keypair();
    const keypairB = await generateEd25519Keypair();

    const signer = createEd25519Signer(keypairA.privateKey);
    const verifier = createEd25519Verifier(keypairB.publicKey);

    const { spec_hash, signature } = await signer.signSpec(testSpec);

    const isValid = await verifier.verify(spec_hash, signature);
    expect(isValid).toBe(false);
  });

  it("produces deterministic hash for the same spec", async () => {
    const { publicKey } = await generateEd25519Keypair();
    const verifier = createEd25519Verifier(publicKey);

    const hash1 = await verifier.computeHash(testSpec);
    const hash2 = await verifier.computeHash(testSpec);

    expect(hash1).toBe(hash2);
  });

  it("produces different hashes for different specs", async () => {
    const { publicKey } = await generateEd25519Keypair();
    const verifier = createEd25519Verifier(publicKey);

    const hash1 = await verifier.computeHash(testSpec);
    const hash2 = await verifier.computeHash(altSpec);

    expect(hash1).not.toBe(hash2);
  });

  it("hash is a hex string (SHA-256)", async () => {
    const { publicKey } = await generateEd25519Keypair();
    const verifier = createEd25519Verifier(publicKey);

    const hash = await verifier.computeHash(testSpec);

    expect(hash).toMatch(/^[0-9a-f]{64}$/);
  });
});
