import type { Spec } from "./spec-types.js";
import type { SignatureVerifier } from "./spec-loader.js";

/**
 * Creates an Ed25519 signature verifier for client-side spec validation.
 * Public key is embedded in the APK/IPA at build time.
 *
 * Uses Web Crypto API (Ed25519 supported in Node 20+, Chrome 113+, Deno).
 */
export function createEd25519Verifier(publicKeyBase64: string): SignatureVerifier {
  let cachedKey: CryptoKey | null = null;

  async function importPublicKey(): Promise<CryptoKey> {
    if (cachedKey) return cachedKey;

    const keyBuffer = base64ToBytes(publicKeyBase64);
    cachedKey = await crypto.subtle.importKey(
      "raw",
      keyBuffer,
      { name: "Ed25519" },
      false,
      ["verify"],
    );
    return cachedKey;
  }

  return {
    async verify(specHash: string, signature: string): Promise<boolean> {
      const key = await importPublicKey();
      const signatureBuffer = base64ToBytes(signature);
      const hashBytes = new TextEncoder().encode(specHash);

      return crypto.subtle.verify("Ed25519", key, signatureBuffer, hashBytes);
    },

    async computeHash(spec: Spec): Promise<string> {
      return computeSpecHash(spec);
    },
  };
}

/**
 * Ed25519 spec signer for server-side spec publishing.
 * Signs spec_hash + monotonic version + renderer_min + timestamp.
 */
export interface SpecSigner {
  signSpec(spec: Spec): Promise<{ spec_hash: string; signature: string; signed_at: string }>;
}

export function createEd25519Signer(privateKeyBase64: string): SpecSigner {
  let cachedKey: CryptoKey | null = null;

  async function importPrivateKey(): Promise<CryptoKey> {
    if (cachedKey) return cachedKey;

    const keyBuffer = base64ToBytes(privateKeyBase64);
    cachedKey = await crypto.subtle.importKey(
      "pkcs8",
      keyBuffer,
      { name: "Ed25519" },
      false,
      ["sign"],
    );
    return cachedKey;
  }

  return {
    async signSpec(spec: Spec) {
      const specHash = await computeSpecHash(spec);
      const key = await importPrivateKey();
      const hashBytes = new TextEncoder().encode(specHash);
      const signatureBytes = await crypto.subtle.sign("Ed25519", key, hashBytes);
      const signature = bytesToBase64(new Uint8Array(signatureBytes));
      const signedAt = new Date().toISOString();

      return { spec_hash: specHash, signature, signed_at: signedAt };
    },
  };
}

/**
 * Generates an Ed25519 keypair for initial setup.
 * Returns base64-encoded public and private keys.
 */
export async function generateEd25519Keypair(): Promise<{
  publicKey: string;
  privateKey: string;
}> {
  const keypair = await crypto.subtle.generateKey("Ed25519", true, ["sign", "verify"]);

  const publicKeyBytes = await crypto.subtle.exportKey("raw", keypair.publicKey);
  const privateKeyBytes = await crypto.subtle.exportKey("pkcs8", keypair.privateKey);

  return {
    publicKey: bytesToBase64(new Uint8Array(publicKeyBytes)),
    privateKey: bytesToBase64(new Uint8Array(privateKeyBytes)),
  };
}

async function computeSpecHash(spec: Spec): Promise<string> {
  const json = JSON.stringify(spec);
  const bytes = new TextEncoder().encode(json);
  const hashBuffer = await crypto.subtle.digest("SHA-256", bytes);
  return bytesToHex(new Uint8Array(hashBuffer));
}

function base64ToBytes(base64: string): Uint8Array<ArrayBuffer> {
  const binary = atob(base64);
  const buffer = new ArrayBuffer(binary.length);
  const bytes = new Uint8Array(buffer);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

function bytesToHex(bytes: Uint8Array): string {
  const hexChars: string[] = [];
  for (let i = 0; i < bytes.length; i++) {
    hexChars.push(bytes[i].toString(16).padStart(2, "0"));
  }
  return hexChars.join("");
}
