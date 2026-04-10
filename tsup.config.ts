import { defineConfig } from "tsup";

export default defineConfig([
  {
    entry: {
      index: "src/index.ts",
      react: "src/bindings/react.tsx",
      "react-native": "src/bindings/react-native.tsx",
    },
    format: ["esm", "cjs"],
    dts: true,
    sourcemap: true,
    clean: true,
    external: ["react", "@supabase/supabase-js"],
  },
  {
    entry: { "cli/init": "src/cli/init.ts" },
    format: ["cjs"],
    banner: { js: "#!/usr/bin/env node" },
    external: ["fs", "path"],
  },
]);
