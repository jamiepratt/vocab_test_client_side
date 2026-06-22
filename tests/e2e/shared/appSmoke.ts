import { expect, type Page } from "@playwright/test";

export async function runAppSmoke(page: Page) {
  await page.goto("/index.html");

  await expect(page.getByRole("heading", { level: 1, name: "Vocab Test Client Side" })).toBeVisible();
  await expect(page.getByText("shadow-cljs + nREPL")).toBeVisible();
  await expect(page.getByText("A tiny Reagent app with enough behavior")).toBeVisible();

  await expect(page.getByRole("heading", { level: 2, name: "Counter" })).toBeVisible();
  await expect(page.getByText("Count: 0")).toBeVisible();

  await page.getByRole("button", { name: "Increment count" }).click();
  await expect(page.getByText("Count: 1")).toBeVisible();

  await page.getByRole("button", { name: "Increment count" }).click();
  await expect(page.getByText("Count: 2")).toBeVisible();

  await expect(page.getByRole("heading", { level: 2, name: "Status" })).toBeVisible();
  await expect(page.getByText("Status: Disabled")).toBeVisible();

  await page.getByRole("button", { name: "Enable feature" }).click();
  await expect(page.getByText("Status: Enabled")).toBeVisible();
  await expect(page.getByRole("button", { name: "Disable feature" })).toBeVisible();

  await expect(page.getByRole("heading", { level: 2, name: "Greeting" })).toBeVisible();
  await expect(page.getByText("Hello, friend!")).toBeVisible();

  await page.getByLabel("Name").fill("Ada");
  await expect(page.getByText("Hello, Ada!")).toBeVisible();
}
