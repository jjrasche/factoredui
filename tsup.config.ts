import { defineConfig } from "tsup";

export default defineConfig([
  {
    entry: {
      index: "src/index.ts",
      react: "src/bindings/react.tsx",
      "react-native": "src/bindings/react-native.tsx",
      sdui: "src/sdui/index.ts",
    },
    format: ["esm", "cjs"],
    splitting: false,
    dts: true,
    sourcemap: true,
    clean: true,
    external: ["react", "react-native", "@supabase/supabase-js"],
  },
  {
    entry: { "cli/init": "src/cli/init.ts" },
    format: ["cjs"],
    banner: { js: "#!/usr/bin/env node" },
    external: ["fs", "path"],
  },
]);
