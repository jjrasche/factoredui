import * as fs from "node:fs";
import * as path from "node:path";

const CONFIG_FILENAME = "factoredui.config.json";

const DEFAULT_CONFIG = {
  supabaseUrl: "http://localhost:54321",
  supabaseAnonKey: "<your-anon-key>",
  schema: "factoredui",
};

export function runInit(): void {
  const targetDir = process.cwd();
  copyMigrations(targetDir);
  writeConfig(targetDir);
  printSetupInstructions();
}

function copyMigrations(targetDir: string): void {
  const migrationsSource = findMigrationsDir();
  const migrationsTarget = path.join(targetDir, "supabase", "migrations");

  if (!fs.existsSync(path.join(targetDir, "supabase"))) {
    console.log("Creating supabase/ directory...");
    fs.mkdirSync(path.join(targetDir, "supabase"), { recursive: true });
  }

  if (!fs.existsSync(migrationsTarget)) {
    fs.mkdirSync(migrationsTarget, { recursive: true });
  }

  const migrationFiles = fs.readdirSync(migrationsSource).filter(
    (file) => file.endsWith(".sql"),
  );

  const timestamp = generateTimestamp();

  for (const file of migrationFiles) {
    const sourcePath = path.join(migrationsSource, file);
    const targetFilename = `${timestamp}_auxi_${file}`;
    const targetPath = path.join(migrationsTarget, targetFilename);

    if (fs.existsSync(targetPath)) {
      console.log(`  skip: ${targetFilename} (already exists)`);
      continue;
    }

    fs.copyFileSync(sourcePath, targetPath);
    console.log(`  copy: ${targetFilename}`);
  }

  console.log(`\nCopied ${migrationFiles.length} factoredui migrations.`);
}

function findMigrationsDir(): string {
  // When installed as npm package: dist/cli/init.cjs -> ../../supabase/migrations
  const packageMigrations = path.resolve(__dirname, "..", "..", "supabase", "migrations");
  if (fs.existsSync(packageMigrations)) return packageMigrations;

  // Fallback: development mode — supabase/migrations alongside source root
  const devMigrations = path.resolve(__dirname, "..", "supabase", "migrations");
  if (fs.existsSync(devMigrations)) return devMigrations;

  throw new Error(
    "Could not find factoredui migrations directory. " +
    "Ensure @factoredui/core is installed correctly.",
  );
}

function writeConfig(targetDir: string): void {
  const configPath = path.join(targetDir, CONFIG_FILENAME);

  if (fs.existsSync(configPath)) {
    console.log(`\n${CONFIG_FILENAME} already exists — skipping.`);
    return;
  }

  fs.writeFileSync(configPath, JSON.stringify(DEFAULT_CONFIG, null, 2) + "\n");
  console.log(`\nCreated ${CONFIG_FILENAME}`);
}

function generateTimestamp(): string {
  const now = new Date();
  return now.toISOString().replace(/[-:T]/g, "").slice(0, 14);
}

function printSetupInstructions(): void {
  console.log(`
Setup complete! Next steps:

  1. Update ${CONFIG_FILENAME} with your Supabase URL and anon key
  2. Run: npx supabase db push
  3. In your app:

     import { initCapture } from '@factoredui/core'
     import { createClient } from '@supabase/supabase-js'

     const supabase = createClient(url, anonKey)
     const capture = initCapture({ supabase })

  For React Native / Expo:

     import { Provider } from '@factoredui/react'

     <Provider supabase={supabase} adapter={adapter} platform="web">
       <App />
     </Provider>
`);
}

