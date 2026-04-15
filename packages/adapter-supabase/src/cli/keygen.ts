import { generateEd25519Keypair } from "@factoredui/core";

export async function runKeygen(): Promise<void> {
  const { publicKey, privateKey } = await generateEd25519Keypair();

  console.log("Ed25519 keypair generated.\n");
  console.log("Public key (embed in client app):");
  console.log(`  ${publicKey}\n`);
  console.log("Private key (keep secret, use server-side for signing specs):");
  console.log(`  ${privateKey}\n`);
  console.log("Store the private key securely (env var, secret manager).");
  console.log("The public key goes in your app config for spec signature verification.");
}
