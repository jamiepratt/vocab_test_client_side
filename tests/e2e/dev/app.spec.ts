import { test } from "@playwright/test";
import { runAppSmoke } from "../shared/appSmoke";

test("renders and responds to user input", async ({ page }) => {
  await runAppSmoke(page);
});
