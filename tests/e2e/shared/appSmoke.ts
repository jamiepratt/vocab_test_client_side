import { expect, type Locator, type Page } from "@playwright/test";

async function expectNoHorizontalOverflow(page: Page) {
  const overflow = await page.evaluate(() => (
    document.documentElement.scrollWidth - document.documentElement.clientWidth
  ));

  expect(overflow).toBeLessThanOrEqual(1);
}

async function expectComfortableButtons(page: Page) {
  for (const button of await page.getByRole("button").all()) {
    if (!(await button.isVisible())) {
      continue;
    }

    const box = await button.boundingBox();
    expect(box).not.toBeNull();

    if (box) {
      expect(box.height).toBeGreaterThanOrEqual(44);
      expect(box.width).toBeLessThanOrEqual(390);
    }
  }
}

async function textColor(locator: Locator) {
  return locator.evaluate((element) => getComputedStyle(element).color);
}

async function expectReferenceStyling(page: Page) {
  await expect(page.locator("#app > main")).toHaveCSS("background-color", "rgb(250, 247, 240)");
  await expect(page.locator("#app > main > section")).toHaveCSS("background-color", "rgb(255, 255, 255)");
  await expect(page.getByRole("button", { name: "Begin Test" })).toHaveCSS("background-color", "rgb(153, 27, 27)");
  await expectNoHorizontalOverflow(page);
  await expectComfortableButtons(page);
}

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
  await expect(page.locator("main button")).toHaveText(question.choices);

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

  const bandColors = await Promise.all(
    expectedBands.map(([band]) => {
      const label = breakdown.getByRole("listitem").filter({ hasText: band }).locator("span").first();
      return textColor(label);
    }),
  );

  expect(new Set(bandColors).size).toBe(expectedBands.length);
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

async function answerAndContinue(page: Page, answer: string) {
  await page.getByRole("button", { name: answer, exact: true }).click();
  await expect(page.getByRole("button", { name: "Next" })).toBeVisible();
  await page.getByRole("button", { name: "Next" }).click();
}

async function expectMainNav(page: Page, activeLink: string) {
  const nav = page.getByRole("navigation", { name: "Main" });

  await expect(nav).toBeVisible();
  await expect(nav.getByRole("link", { name: "Polish Passive Vocabulary Size Test" })).toHaveCount(0);

  const links = [
    ["Test", "#/"],
    ["Features", "#/features"],
    ["Adaptive methodology", "#/adaptive-methodology"],
    ["Progressive methodology", "#/methodology"],
  ] as const;

  await expect(nav.getByRole("link")).toHaveText(links.map(([name]) => name));

  for (const [name, href] of links) {
    await expect(nav.getByRole("link", { name, exact: true })).toHaveAttribute("href", href);
  }

  await expect(nav.getByRole("link", { name: activeLink, exact: true })).toHaveAttribute("aria-current", "page");

  const inactive = nav.getByRole("link", { name: links.find(([name]) => name !== activeLink)![0], exact: true });
  const inactiveStyle = await inactive.evaluate((element) => {
    const style = getComputedStyle(element);
    return {
      borderWidth: Number.parseFloat(style.borderTopWidth),
      boxShadow: style.boxShadow,
      cursor: style.cursor,
    };
  });

  expect(inactiveStyle.borderWidth).toBeGreaterThanOrEqual(1);
  expect(inactiveStyle.boxShadow).not.toBe("none");
  expect(inactiveStyle.cursor).toBe("pointer");
}

export async function runAppSmoke(page: Page) {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.addInitScript(() => {
    Math.random = () => 0;
  });
  await page.goto("/index.html");

  await expectMainNav(page, "Test");
  await page.getByRole("link", { name: "Progressive methodology" }).click();
  await expect(page).toHaveURL(/#\/methodology$/);
  await expect(page.getByRole("heading", { level: 1, name: "Progressive vocabulary test methodology" })).toBeVisible();
  await expect(page.getByText("Frequency rank is good enough to launch.")).toBeVisible();
  await expect(page.getByRole("link", { name: "What is Item Response Theory?" })).toHaveAttribute(
    "href",
    "https://www.youtube.com/watch?v=P8huS6PPxJA",
  );
  await expectMainNav(page, "Progressive methodology");

  await page.getByRole("link", { name: "Adaptive methodology" }).click();
  await expect(page).toHaveURL(/#\/adaptive-methodology$/);
  await expect(page.getByRole("heading", { level: 1, name: "Adaptive vocabulary size testing from a frequency list" })).toBeVisible();
  await expect(page.getByRole("tab", { name: "Beginner" })).toHaveAttribute("aria-selected", "true");
  await expect(page.getByText("Do not start with a short fully adaptive test.")).toBeVisible();
  await page.getByRole("tab", { name: "Advanced" }).click();
  await expect(page.getByRole("tab", { name: "Advanced" })).toHaveAttribute("aria-selected", "true");
  await expect(page.getByRole("heading", { level: 2, name: "Method" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Better Measurement with Item Response Theory" })).toHaveAttribute(
    "href",
    "https://www.youtube.com/watch?v=HoMVasu2tg8",
  );
  await expectMainNav(page, "Adaptive methodology");

  await page.getByRole("link", { name: "Features" }).click();
  await expect(page).toHaveURL(/#\/features$/);
  await expect(page.getByRole("heading", { level: 1, name: "Vocabulary test features to implement" })).toBeVisible();
  await expect(page.getByRole("heading", { level: 2, name: "Current app snapshot" })).toBeVisible();
  await expectMainNav(page, "Features");

  await page.goto("/adaptive-vocabulary-testing.html");
  await expect(page).toHaveURL(/index\.html#\/adaptive-methodology$/);
  await expect(page.getByRole("heading", { level: 1, name: "Adaptive vocabulary size testing from a frequency list" })).toBeVisible();
  await expectMainNav(page, "Adaptive methodology");

  await page.goto("/features-to-implement.html");
  await expect(page).toHaveURL(/index\.html#\/features$/);
  await expect(page.getByRole("heading", { level: 1, name: "Vocabulary test features to implement" })).toBeVisible();
  await expectMainNav(page, "Features");

  await page.getByRole("link", { name: "Test", exact: true }).click();

  await expect(page).toHaveTitle("Polish Passive Vocabulary Size Test");
  await expect(page.getByRole("heading", { level: 1, name: "Polish Passive Vocabulary Size Test" })).toBeVisible();
  await expect(page.getByText("Polish to English")).toBeVisible();
  await expect(page.getByText("You'll see a Polish word. Pick the correct English meaning from 4 choices.")).toBeVisible();
  await expect(page.getByText("80 words across 6 bands")).toBeVisible();
  await expect(page.getByText("Band 1 - top 250 (sanity) - 12 words")).toBeVisible();
  await expect(page.getByText("Band 2 - 250-500 (your estimate) - 16 words")).toBeVisible();
  await expect(page.getByText("Band 3 - 500-1,000 - 18 words")).toBeVisible();
  await expect(page.getByText("Band 4 - 1,000-2,000 - 16 words")).toBeVisible();
  await expect(page.getByText("Band 5 - 2,000-3,500 - 10 words")).toBeVisible();
  await expect(page.getByText("Band 6 - 3,500+ (ceiling) - 8 words")).toBeVisible();
  await expectReferenceStyling(page);

  await page.getByRole("button", { name: "Begin Test" }).click();

  const firstChoices = ["fire", "air", "earth", "water", "don't know"];
  await expectQuestion(page, {
    current: 1,
    word: "woda",
    wordClass: "noun",
    band: "0-250",
    choices: firstChoices,
  });
  await expectNoHorizontalOverflow(page);
  await expectComfortableButtons(page);

  await page.getByRole("button", { name: "water", exact: true }).click();

  await expect(page.getByText("Correct", { exact: true })).toBeVisible();
  await expectChoicesLocked(page, firstChoices);
  await expect(page.getByRole("button", { name: "Next" })).toBeVisible();

  await page.getByRole("button", { name: "Next" }).click();

  const secondChoices = ["to drink", "to sleep", "to walk", "to eat", "don't know"];
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

  const thirdChoices = ["small", "fast", "heavy", "big / large", "don't know"];
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
    "fragile / weak",
    "flexible",
    "hesitant",
    "unyielding / steadfast / indomitable",
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
  await expectNoHorizontalOverflow(page);

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

export async function runHighEstimateRegression(page: Page) {
  const correctThroughBand5 = [
    "water",
    "to eat",
    "big / large",
    "house / home",
    "good",
    "dog",
    "to read",
    "day",
    "to drink",
    "small / little",
    "thank you",
    "woman",
    "quickly / fast",
    "to buy",
    "city / town",
    "money",
    "happy",
    "always",
    "to close / to shut",
    "hot",
    "difficult / hard",
    "to speak / to say",
    "to sleep",
    "tomorrow",
    "work / job",
    "cold",
    "road / way / expensive (fem.)",
    "to run",
    "to remember",
    "to forget",
    "to explain",
    "to meet",
    "health",
    "weather",
    "dangerous",
    "almost / nearly",
    "tired",
    "knowledge",
    "market / town square",
    "of course / obviously",
    "clean / pure",
    "strong",
    "to worry",
    "idea",
    "duty / obligation",
    "to smile",
    "experience",
    "influence / impact",
    "to avoid",
    "to require / to demand",
    "to manage / to administer",
    "environment",
    "society",
    "to destroy",
    "to deceive / to cheat",
    "expenses / spending",
    "to oppose / to object",
    "to repair / to fix",
    "safety / security",
    "behavior / conduct",
    "careful / cautious",
    "to persuade / to convince",
    "inevitable / unavoidable",
    "intricate / complicated",
    "perseverance / persistence",
    "relief",
    "fraud / scam / deception",
    "obstacle / barrier",
    "to strengthen / to reinforce",
    "tendency / inclination",
    "to discourage",
    "to deepen / to intensify",
  ];

  await page.setViewportSize({ width: 390, height: 844 });
  await page.addInitScript(() => {
    Math.random = () => 0;
  });
  await page.goto("/index.html");
  await page.getByRole("button", { name: "Begin Test" }).click();

  for (const answer of correctThroughBand5) {
    await answerAndContinue(page, answer);
  }

  for (let index = 0; index < 8; index++) {
    await answerAndContinue(page, "don't know");
  }

  await expect(page.getByRole("heading", { level: 1, name: "Results" })).toBeVisible();
  await expect(page.getByText("~3500 words", { exact: true })).toBeVisible();
  await expect(page.getByText("Ceiling: 2K-3.5K", { exact: true })).toBeVisible();
  await expect(page.getByText("closer to ~3500 words than 500", { exact: false })).toBeVisible();
  await expect(page.getByText("1,200-1,800", { exact: false })).toHaveCount(0);
  await expectNoHorizontalOverflow(page);
}

export async function runApiQuestionLoading(page: Page) {
  const questions = Array.from({ length: 80 }, (_, index) => ({
    word: index === 0 ? "testowe" : `dummy-${index}`,
    "word-class": index === 0 ? "adj" : "noun",
    band: "B1",
    correct: index === 0 ? "from the API" : `correct-${index}`,
    wrong: [`wrong-a-${index}`, `wrong-b-${index}`, `wrong-c-${index}`],
  }));

  await page.route(/\/api\/questions$/, async (route) => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(questions),
    });
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.addInitScript(() => {
    Math.random = () => 0;
  });
  await page.goto("/index.html");
  await page.getByRole("button", { name: "Begin Test" }).click();

  await expectQuestion(page, {
    current: 1,
    word: "testowe",
    wordClass: "adj",
    band: "0-250",
    choices: ["wrong-a-0", "wrong-b-0", "wrong-c-0", "from the API", "don't know"],
  });
}
