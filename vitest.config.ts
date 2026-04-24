import { defineConfig } from "vitest/config";
import * as path from "path";

const coreAlias = {
  "@factoredui/core": path.resolve(__dirname, "packages/core/src/index.ts"),
};

export default defineConfig({
  resolve: { alias: coreAlias },
  test: {
    globals: true,
    projects: [
      {
        resolve: { alias: coreAlias },
        test: {
          name: "unit",
          environment: "node",
          include: ["packages/*/src/**/*.test.ts"],
          exclude: ["packages/*/src/**/*.integration.test.ts"],
          fileParallelism: true,
        },
      },
      {
        resolve: { alias: coreAlias },
        test: {
          name: "integration",
          environment: "node",
          include: ["packages/*/src/**/*.integration.test.ts"],
          fileParallelism: false,
        },
      },
    ],
  },
});
