import { test } from "@playwright/test";
import { runAppSmoke, runHighEstimateRegression } from "../shared/appSmoke";

test("renders and responds to user input", async ({ page }) => {
  await runAppSmoke(page);
});

test("keeps high estimate copy consistent with the displayed estimate", async ({ page }) => {
  await runHighEstimateRegression(page);
});
