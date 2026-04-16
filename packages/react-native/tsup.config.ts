import { defineConfig } from "tsup";

export default defineConfig({
  entry: { index: "src/index.ts", testing: "src/__mocks__/react-native.ts" },
  format: ["esm", "cjs"],
  splitting: false,
  dts: true,
  sourcemap: true,
  clean: true,
  external: ["react", "react-native", "@factoredui/core", "@factoredui/react"],
});
