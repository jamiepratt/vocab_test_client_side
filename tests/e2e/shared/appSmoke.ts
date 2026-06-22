import { expect, type Page } from "@playwright/test";

export async function runAppSmoke(page: Page) {
  await page.goto("/index.html");

  await expect(page.getByRole("heading", { level: 1, name: "Polish Vocabulary Test" })).toBeVisible();
  await expect(page.getByText("Polish to English")).toBeVisible();
  await expect(page.getByText("You'll see a Polish word. Pick the correct English meaning from 4 choices.")).toBeVisible();
  await expect(page.getByText("80 words across 6 bands")).toBeVisible();
  await expect(page.getByText("Band 1 - top 250 (sanity) - 12 words")).toBeVisible();
  await expect(page.getByText("Band 2 - 250-500 (your estimate) - 16 words")).toBeVisible();
  await expect(page.getByText("Band 3 - 500-1,000 - 18 words")).toBeVisible();
  await expect(page.getByText("Band 4 - 1,000-2,000 - 16 words")).toBeVisible();
  await expect(page.getByText("Band 5 - 2,000-3,500 - 10 words")).toBeVisible();
  await expect(page.getByText("Band 6 - 3,500+ (ceiling) - 8 words")).toBeVisible();

  await page.getByRole("button", { name: "Begin Test" }).click();

  await expect(page.getByText("1 / 80")).toBeVisible();
  await expect(page.getByText("0-250", { exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { level: 2, name: "woda" })).toBeVisible();
  await expect(page.getByText("noun", { exact: true })).toBeVisible();
  await expect(page.getByText("Select the correct meaning")).toBeVisible();
  await expect(page.getByRole("button", { name: "water" })).toBeVisible();
  await expect(page.getByRole("button", { name: "fire" })).toBeVisible();
  await expect(page.getByRole("button", { name: "air" })).toBeVisible();
  await expect(page.getByRole("button", { name: "earth" })).toBeVisible();
  await expect(page.getByRole("button", { name: "don't know" })).toBeVisible();

  await expect(page.getByRole("heading", { level: 2, name: "Counter" })).toHaveCount(0);
  await expect(page.getByText("Status: Disabled")).toHaveCount(0);
  await expect(page.getByText("Hello, friend!")).toHaveCount(0);
}
