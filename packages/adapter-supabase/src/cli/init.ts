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
  copyEdgeFunction(targetDir);
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
    const targetFilename = `${timestamp}_factoredui_${file}`;
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
    "Ensure @factoredui/adapter-supabase is installed correctly.",
  );
}

function copyEdgeFunction(targetDir: string): void {
  const functionSource = findEdgeFunctionDir();
  const functionTarget = path.join(targetDir, "supabase", "functions", "factoredui-cluster");

  if (fs.existsSync(path.join(functionTarget, "index.ts"))) {
    console.log("\nEdge function already exists — skipping.");
    return;
  }

  fs.mkdirSync(functionTarget, { recursive: true });

  const EDGE_FN_ALLOWED_FILES = new Set([".ts", ".json"]);
  const files = fs.readdirSync(functionSource).filter(
    (file) => EDGE_FN_ALLOWED_FILES.has(path.extname(file)),
  );
  for (const file of files) {
    fs.copyFileSync(
      path.join(functionSource, file),
      path.join(functionTarget, file),
    );
  }

  console.log(`\nCopied edge function to supabase/functions/factoredui-cluster/`);
}

function findEdgeFunctionDir(): string {
  const packageDir = path.resolve(__dirname, "..", "..", "supabase", "functions", "factoredui-cluster");
  if (fs.existsSync(packageDir)) return packageDir;

  const devDir = path.resolve(__dirname, "..", "supabase", "functions", "factoredui-cluster");
  if (fs.existsSync(devDir)) return devDir;

  throw new Error(
    "Could not find factoredui-cluster edge function. " +
    "Ensure @factoredui/adapter-supabase is installed correctly.",
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

  2. Enable required Postgres extensions (if not already enabled):
       - pg_cron    (scheduled clustering jobs)
       - pg_net     (edge function invocation from pg_cron)
       - vector     (pgvector for factor embeddings)

  3. Add "factoredui" to your PostgREST schema config:
     In supabase/config.toml:
       [api]
       schemas = ["public", "factoredui"]
     Or set the env var: PGRST_DB_SCHEMAS=public,factoredui

  4. Apply migrations:
       npx supabase db push

  5. Deploy the clustering edge function:
       npx supabase functions deploy factoredui-cluster

  6. Configure pg_cron auth (self-hosted / production only):
       ALTER SYSTEM SET app.supabase_url = 'https://your-project.supabase.co';
       ALTER SYSTEM SET app.service_role_key = 'your-service-role-key';
       SELECT pg_reload_conf();

  7. In your app:

     import { createClient } from '@supabase/supabase-js'
     import { createSupabaseStore } from '@factoredui/adapter-supabase'
     import { initCapture } from '@factoredui/core'

     const supabase = createClient(url, anonKey, {
       db: { schema: 'factoredui' }
     })
     const store = createSupabaseStore(supabase)
     const capture = initCapture({ store })

  For React / Expo:

     import { Provider } from '@factoredui/react'

     <Provider store={store} adapter={adapter} platform="web">
       <App />
     </Provider>
`);
}
