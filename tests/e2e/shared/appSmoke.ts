import { expect, type Page } from "@playwright/test";

async function expectProgress(page: Page, current: number) {
  const total = 80;

  await expect(page.getByText(`${current} / ${total}`)).toBeVisible();
  await expect(page.getByRole("progressbar")).toHaveAttribute("aria-valuenow", String(current));
  await expect(page.getByRole("progressbar")).toHaveAttribute("aria-valuemax", String(total));
}

async function expectQuestion(page: Page, question: {
  current: number;
  word: string;
  wordClass: string;
  band: string;
  choices: string[];
}) {
  await expectProgress(page, question.current);
  await expect(page.getByText(question.band, { exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { level: 2, name: question.word })).toBeVisible();
  await expect(page.getByText(question.wordClass, { exact: true })).toBeVisible();
  await expect(page.getByText("Select the correct meaning")).toBeVisible();

  for (const choice of question.choices) {
    await expect(page.getByRole("button", { name: choice, exact: true })).toBeVisible();
  }
}

async function expectChoicesLocked(page: Page, choices: string[]) {
  for (const choice of choices) {
    await expect(page.getByRole("button", { name: choice, exact: true })).toBeDisabled();
  }
}

async function expectBandBreakdown(page: Page) {
  const breakdown = page.getByRole("region", { name: "Accuracy by frequency band" });
  const expectedBands = [
    ["0-250", "1/12 (8%)"],
    ["250-500", "0/16 (0%)"],
    ["500-1K", "0/18 (0%)"],
    ["1K-2K", "0/16 (0%)"],
    ["2K-3.5K", "0/10 (0%)"],
    ["3.5K+", "0/8 (0%)"],
  ];

  await expect(breakdown).toBeVisible();

  for (const [band, score] of expectedBands) {
    const row = breakdown.getByRole("listitem").filter({ hasText: band });
    await expect(row).toContainText(score);
  }
}

async function expectReviewList(page: Page) {
  const review = page.getByRole("region", { name: "Words to review (79)" });

  await expect(review).toBeVisible();
  await expect(review.getByRole("listitem")).toHaveCount(79);
  await expect(review.getByRole("listitem").filter({ hasText: "jeść" })).toContainText("0-250");
  await expect(review.getByRole("listitem").filter({ hasText: "jeść" })).toContainText("to eat");
  await expect(review.getByRole("listitem").filter({ hasText: "duży" })).toContainText("big / large");
  await expect(review.getByRole("listitem").filter({ hasText: "szybko" })).toContainText("quickly / fast");
  await expect(review.getByRole("listitem").filter({ hasText: "niezłomny" })).toContainText("3.5K+");
  await expect(review.getByRole("listitem").filter({ hasText: "niezłomny" })).toContainText("unyielding / steadfast / indomitable");
}

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

  const firstChoices = ["water", "fire", "air", "earth", "don't know"];
  await expectQuestion(page, {
    current: 1,
    word: "woda",
    wordClass: "noun",
    band: "0-250",
    choices: firstChoices,
  });

  await page.getByRole("button", { name: "water", exact: true }).click();

  await expect(page.getByText("Correct", { exact: true })).toBeVisible();
  await expectChoicesLocked(page, firstChoices);
  await expect(page.getByRole("button", { name: "Next" })).toBeVisible();

  await page.getByRole("button", { name: "Next" }).click();

  const secondChoices = ["to eat", "to drink", "to sleep", "to walk", "don't know"];
  await expectQuestion(page, {
    current: 2,
    word: "jeść",
    wordClass: "verb",
    band: "0-250",
    choices: secondChoices,
  });

  await page.getByRole("button", { name: "to drink", exact: true }).click();

  await expect(page.getByText("Correct answer: to eat", { exact: true })).toBeVisible();
  await expectChoicesLocked(page, secondChoices);
  await expect(page.getByRole("button", { name: "Next" })).toBeVisible();

  await page.getByRole("button", { name: "Next" }).click();

  const thirdChoices = ["big / large", "small", "fast", "heavy", "don't know"];
  await expectQuestion(page, {
    current: 3,
    word: "duży",
    wordClass: "adj",
    band: "0-250",
    choices: thirdChoices,
  });

  await page.getByRole("button", { name: "don't know", exact: true }).click();

  await expect(page.getByText("Correct answer: big / large", { exact: true })).toBeVisible();
  await expectChoicesLocked(page, thirdChoices);
  await expect(page.getByRole("button", { name: "Next" })).toBeVisible();

  await page.getByRole("button", { name: "Next" }).click();

  for (let current = 4; current <= 79; current++) {
    await expectProgress(page, current);
    await page.getByRole("button", { name: "don't know", exact: true }).click();
    await expect(page.getByRole("button", { name: "Next" })).toBeVisible();
    await page.getByRole("button", { name: "Next" }).click();
  }

  const lastChoices = [
    "unyielding / steadfast / indomitable",
    "fragile / weak",
    "flexible",
    "hesitant",
    "don't know",
  ];
  await expectQuestion(page, {
    current: 80,
    word: "niezłomny",
    wordClass: "adj",
    band: "3.5K+",
    choices: lastChoices,
  });

  await page.getByRole("button", { name: "don't know", exact: true }).click();

  await expect(page.getByText("Correct answer: unyielding / steadfast / indomitable", { exact: true })).toBeVisible();
  await expectChoicesLocked(page, lastChoices);

  await page.getByRole("button", { name: "Next" }).click();

  await expect(page.getByRole("heading", { level: 1, name: "Results" })).toBeVisible();
  await expect(page.getByText("1%", { exact: true })).toBeVisible();
  await expect(page.getByText("1 of 80 correct", { exact: true })).toBeVisible();
  await expect(page.getByText("Wrong: 1", { exact: true })).toBeVisible();
  await expect(page.getByText("Don't know: 78", { exact: true })).toBeVisible();
  await expect(page.getByText("Estimated passive vocabulary", { exact: true })).toBeVisible();
  await expect(page.getByText("~0 words", { exact: true })).toBeVisible();
  await expect(page.getByText("Ceiling: 0-250", { exact: true })).toBeVisible();
  await expect(page.getByText("Slightly below your estimate. Your vocabulary appears ~500 words under your 500 guess - but this is well within normal range.")).toBeVisible();
  await expect(page.getByText("Even the top 250 words are shaky, which puts the vocabulary under 250. This is very early - the focus should be on drilling the most frequent words until they're automatic.")).toBeVisible();
  await expect(page.getByText("You used \"don't know\" honestly. This estimate is probably accurate or slightly conservative.")).toBeVisible();
  await expect(page.getByText("This test shows words in their dictionary (nominative) form. Polish has 7 cases")).toBeVisible();
  await expect(page.getByText("woda -> wode -> woda -> wodzie")).toBeVisible();
  await expect(page.getByText("Passive vocabulary (recognition) is typically 2-3x active vocabulary (production). This test measures recognition only.")).toBeVisible();
  await expectBandBreakdown(page);
  await expectReviewList(page);

  await expect(page.getByText("Select the correct meaning")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "don't know", exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Retake" })).toBeVisible();

  await page.getByRole("button", { name: "Retake" }).click();

  await expectQuestion(page, {
    current: 1,
    word: "woda",
    wordClass: "noun",
    band: "0-250",
    choices: firstChoices,
  });
  await expect(page.getByText("Correct", { exact: true })).toHaveCount(0);
  await expect(page.getByText("Correct answer:", { exact: false })).toHaveCount(0);
  await expect(page.getByRole("heading", { level: 1, name: "Results" })).toHaveCount(0);
  await expect(page.getByRole("region", { name: "Words to review (79)" })).toHaveCount(0);
}
