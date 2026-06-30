import { test } from "@playwright/test";
import { runAppSmoke, runCurrentDocsTerminology, runHighEstimateRegression } from "../shared/appSmoke";

test("renders and responds to user input", async ({ page }) => {
  test.setTimeout(60000);
  await runAppSmoke(page);
});

test("keeps high estimate copy consistent with the displayed estimate", async ({ page }) => {
  await runHighEstimateRegression(page);
});

test("publishes current docs with lemma-inventory result terminology", async ({ page }) => {
  await runCurrentDocsTerminology(page);
});
