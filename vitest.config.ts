import { defineConfig } from "vitest/config";
import * as path from "path";

export default defineConfig({
  resolve: {
    alias: {
      "@factoredui/core": path.resolve(__dirname, "packages/core/src/index.ts"),
      "@factoredui/adapter-supabase": path.resolve(__dirname, "packages/adapter-supabase/src/index.ts"),
      "@factoredui/react": path.resolve(__dirname, "packages/react/src/index.ts"),
      "@factoredui/react-native": path.resolve(__dirname, "packages/react-native/src/index.ts"),
    },
  },
  test: {
    globals: true,
    projects: [
      {
        resolve: {
          alias: {
            "@factoredui/core": path.resolve(__dirname, "packages/core/src/index.ts"),
            "@factoredui/adapter-supabase": path.resolve(__dirname, "packages/adapter-supabase/src/index.ts"),
            "@factoredui/react": path.resolve(__dirname, "packages/react/src/index.ts"),
            "@factoredui/react-native": path.resolve(__dirname, "packages/react-native/src/index.ts"),
            "react-native": path.resolve(__dirname, "packages/react-native/src/__mocks__/react-native.ts"),
          },
        },
        test: {
          name: "unit",
          environment: "jsdom",
          include: ["packages/*/src/**/*.test.ts", "packages/*/src/**/*.test.tsx"],
          exclude: ["packages/*/src/**/*.integration.test.ts"],
          fileParallelism: true,
        },
      },
      {
        resolve: {
          alias: {
            "@factoredui/core": path.resolve(__dirname, "packages/core/src/index.ts"),
            "@factoredui/adapter-supabase": path.resolve(__dirname, "packages/adapter-supabase/src/index.ts"),
            "@factoredui/react": path.resolve(__dirname, "packages/react/src/index.ts"),
            "@factoredui/react-native": path.resolve(__dirname, "packages/react-native/src/index.ts"),
          },
        },
        test: {
          name: "integration",
          environment: "jsdom",
          include: ["packages/*/src/**/*.integration.test.ts", "packages/*/src/**/*.integration.test.tsx"],
          fileParallelism: false,
        },
      },
    ],
  },
});
