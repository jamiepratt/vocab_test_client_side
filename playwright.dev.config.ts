import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./tests/e2e/dev",
  timeout: 10_000,
  expect: {
    timeout: 5_000,
  },
  reporter: "list",
  use: {
    ...devices["Desktop Chrome"],
    baseURL: "http://localhost:8000",
    trace: "on-first-retry",
  },
  webServer: {
    command: "npm run dev",
    url: "http://localhost:8000/index.html",
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
