import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./tests/e2e/release",
  timeout: 10_000,
  expect: {
    timeout: 5_000,
  },
  reporter: "list",
  use: {
    ...devices["Desktop Chrome"],
    baseURL: "http://localhost:4173",
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
      command: "python3 -m http.server 4173 --directory target/release/public",
      url: "http://localhost:4173/index.html",
      reuseExistingServer: false,
      timeout: 30_000,
    },
  ],
});
