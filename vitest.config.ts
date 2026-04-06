import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    globals: true,
    projects: [
      {
        test: {
          name: "unit",
          environment: "jsdom",
          include: ["src/**/*.test.ts", "src/**/*.test.tsx"],
          exclude: ["src/**/*.integration.test.ts"],
          fileParallelism: true,
        },
      },
      {
        test: {
          name: "integration",
          environment: "jsdom",
          include: ["src/**/*.integration.test.ts"],
          fileParallelism: false,
        },
      },
    ],
  },
});
