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
    baseURL: "http://localhost:8001",
    trace: "on-first-retry",
  },
  webServer: [
    {
      command: "npm run api",
      url: "http://localhost:8080/healthz",
      reuseExistingServer: true,
      timeout: 120_000,
    },
    {
      command: "npm run e2e:frontend",
      url: "http://localhost:8001/index.html",
      reuseExistingServer: false,
      timeout: 120_000,
    },
  ],
});
