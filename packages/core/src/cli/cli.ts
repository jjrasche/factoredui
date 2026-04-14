import { runInit } from "./init.js";
import { runKeygen } from "./keygen.js";

const COMMANDS: Record<string, () => void | Promise<void>> = {
  init: runInit,
  keygen: runKeygen,
};

function main(): void {
  const command = process.argv[2];

  if (!command) {
    printUsage();
    process.exit(1);
  }

  const handler = COMMANDS[command];
  if (!handler) {
    console.error(`Unknown command: ${command}\n`);
    printUsage();
    process.exit(1);
  }

  Promise.resolve(handler()).catch((err) => {
    console.error(`factoredui ${command} failed:`, err.message);
    process.exit(1);
  });
}

function printUsage(): void {
  console.log(`Usage: factoredui <command>

Commands:
  init      Copy migrations and create config file
  keygen    Generate Ed25519 keypair for SDUI spec signing`);
}

main();
