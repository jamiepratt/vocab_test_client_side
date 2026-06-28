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

const pendingLiveEstimateText =
  "Not enough questions answered to make an estimate yet, answer at least 30 questions and estimate is updated live as you answer each question.";

const dontKnowButtonName =
  "don't know (don't guess for a more accurate estimate, press this if unsure)";

function dontKnowButton(page: Page) {
  return page.getByRole("button", { name: dontKnowButtonName, exact: true });
}

type SentenceItem = {
  "item-id": string;
  sentence: string;
  "target-surface": string;
  "target-surface-form-id": number;
  "highlight-span": { start: number; end: number };
  "lemma-id": number;
  "lemma-pos-id": number;
  "lemma-inventory-rank": number;
  "surface-difficulty-rank": number;
  "fixed-stratum": number;
  "correct-translation": string;
  distractors: string[];
  "item-type": string;
  "choice-count": number;
  "guess-rate": number;
};

const starterItems = [
  {
    sentence: "Kot pije wodę.",
    target: "Kot",
    correct: "cat",
    distractors: ["dog", "bird", "fish", "tree"],
  },
  {
    sentence: "Codziennie piję wodę po treningu.",
    target: "piję",
    correct: "I drink",
    distractors: ["I sleep", "I walk", "I read", "I wait"],
  },
  {
    sentence: "Duży dom stoi przy rzece.",
    target: "Duży",
    correct: "big / large",
    distractors: ["small", "fast", "heavy", "quiet"],
  },
];

function targetSpan(sentence: string, target: string) {
  const start = sentence.indexOf(target);
  return { start, end: start + target.length };
}

function sentenceItem(index: number, overrides: {
  sentence?: string;
  target?: string;
  correct?: string;
  distractors?: string[];
} = {}): SentenceItem {
  const starter = starterItems[index];
  const target = overrides.target ?? starter?.target ?? `słowo${index + 1}`;
  const sentence = overrides.sentence ?? starter?.sentence ?? `To jest ${target} w krótkim zdaniu.`;
  const correct = overrides.correct ?? starter?.correct ?? `meaning-${index + 1}`;
  const distractors = overrides.distractors ?? starter?.distractors ?? [
    `wrong-a-${index + 1}`,
    `wrong-b-${index + 1}`,
    `wrong-c-${index + 1}`,
    `wrong-d-${index + 1}`,
  ];

  return {
    "item-id": `example-sentence:${index + 1}`,
    sentence,
    "target-surface": target,
    "target-surface-form-id": 2000 + index,
    "highlight-span": targetSpan(sentence, target),
    "lemma-id": index + 1,
    "lemma-pos-id": 1000 + index,
    "lemma-inventory-rank": index + 1,
    "surface-difficulty-rank": index + 1,
    "fixed-stratum": Math.floor(index / 1000) + 1,
    "correct-translation": correct,
    distractors,
    "item-type": "sentence-context-lemma",
    "choice-count": distractors.length + 1,
    "guess-rate": 1 / (distractors.length + 1),
  };
}

function sentenceBlockFixture(overrides: {
  block?: number;
  rankStart?: number;
  rankEnd?: number;
} = {}) {
  const block = overrides.block ?? 0;
  const offset = block * 80;

  return {
    level: "pre-a1",
    "requested-level": "absolute-beginner",
    "level-role": "starting-prior",
    "measurement-unit": "lemma",
    block,
    "block-size": 80,
    "surface-rank-start": overrides.rankStart ?? 1,
    "surface-rank-end": overrides.rankEnd ?? 80,
    items: Array.from({ length: 80 }, (_, index) => {
      if (block === 0 && index === 79) {
        return sentenceItem(index, {
          sentence: "Niezłomny duch pomaga jej dalej pracować.",
          target: "Niezłomny",
          correct: "unyielding / steadfast / indomitable",
          distractors: ["fragile / weak", "flexible", "hesitant", "temporary"],
        });
      }

      return sentenceItem(index + offset);
    }),
    "invalid-items": [],
  };
}

async function routeSentenceBlock(page: Page, block = sentenceBlockFixture()) {
  await page.route(/\/api\/sentence-question-blocks(\?.*)?$/, async (route) => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(block),
    });
  });
}

async function routeAnswerEvents(page: Page, status = 201, answerEvents: unknown[] = []) {
  await page.route(/\/api\/answer-events$/, async (route) => {
    answerEvents.push(route.request().postDataJSON());
    await route.fulfill({
      status,
      contentType: "application/json",
      body: JSON.stringify(status < 400 ? { "answer-event-id": answerEvents.length } : { error: "forced failure" }),
    });
  });
}

async function expectReferenceStyling(page: Page) {
  await expect(page.locator(".app-frame")).toHaveAttribute("data-theme", "light");
  await expect(page.locator("#page-content > main")).toHaveCSS("background-color", "rgb(243, 247, 247)");
  await expect(page.locator("#page-content > main > section")).toHaveCSS("background-color", "rgb(255, 255, 255)");
  await expect(page.getByRole("button", { name: "Begin Test" })).toHaveCSS("background-color", "rgb(16, 109, 104)");
  await expectNoHorizontalOverflow(page);
  await expectComfortableButtons(page);
}

async function expectProgress(page: Page, scored: number) {
  const total = 80;

  await expect(page.getByText(`${scored} / ${total} scored`)).toBeVisible();
  await expect(page.getByRole("progressbar")).toHaveAttribute("aria-valuenow", String(scored));
  await expect(page.getByRole("progressbar")).toHaveAttribute("aria-valuemax", String(total));
}

async function expectProgressBelowQuizStatus(page: Page) {
  const status = page.getByLabel("Quiz status");
  const progress = page.getByRole("progressbar");

  const statusBox = await status.boundingBox();
  const progressBox = await progress.boundingBox();

  expect(statusBox).not.toBeNull();
  expect(progressBox).not.toBeNull();
  expect(progressBox!.y).toBeGreaterThanOrEqual(statusBox!.y + statusBox!.height);
}

async function expectLiveEstimateBelowQuestionCard(page: Page) {
  const questionCard = page.locator("section[aria-labelledby='question-heading']");
  const liveEstimate = page.getByRole("region", { name: "Live estimate" });

  const cardBox = await questionCard.boundingBox();
  const liveEstimateBox = await liveEstimate.boundingBox();

  expect(cardBox).not.toBeNull();
  expect(liveEstimateBox).not.toBeNull();
  expect(liveEstimateBox!.y).toBeGreaterThanOrEqual(cardBox!.y + cardBox!.height);
  expect(Math.abs(liveEstimateBox!.width - cardBox!.width)).toBeLessThanOrEqual(1);
  expect(liveEstimateBox!.y - (cardBox!.y + cardBox!.height)).toBeGreaterThanOrEqual(16);
}

async function expectSentenceQuestion(page: Page, question: {
  scored: number;
  item: number;
  range?: string;
  sentence: string;
  target: string;
  choices: string[];
}) {
  const sentence = page.getByRole("group", { name: "Polish sentence" });
  const answers = page.getByRole("group", { name: "Answer choices" });
  const target = page.getByRole("term");

  await expectProgress(page, question.scored);
  await expectProgressBelowQuizStatus(page);
  await expectLiveEstimateBelowQuestionCard(page);
  await expect(page.getByText(`Item ${question.item} of 80`, { exact: true })).toBeVisible();
  if (question.range) {
    await expect(page.getByText(question.range, { exact: true })).toBeVisible();
  }
  const questionHeading = page.getByRole("heading", { level: 2, name: "What does the highlighted word in this sentence mean?" });
  await expect(questionHeading).toBeVisible();
  await expect(questionHeading.locator(".app-target-mark")).toHaveText("highlighted");
  await expect(sentence).toContainText(question.sentence);
  await expect(target).toHaveCount(1);
  await expect(target).toHaveText(question.target);
  await expect(page.getByText("Select the best English meaning")).toBeVisible();
  await expect(answers.getByRole("button")).toHaveText(question.choices);

  for (const choice of question.choices) {
    await expect(page.getByRole("button", { name: choice, exact: true })).toBeVisible();
  }

  await expect(dontKnowButton(page)).toBeVisible();
}

async function expectChoicesLocked(page: Page, choices: string[]) {
  for (const choice of choices) {
    await expect(page.getByRole("button", { name: choice, exact: true })).toBeDisabled();
  }

  await expect(dontKnowButton(page)).toBeDisabled();
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
  await expect(review.getByRole("listitem").filter({ hasText: "piję" })).toContainText("0-250");
  await expect(review.getByRole("listitem").filter({ hasText: "piję" })).toContainText("I drink");
  await expect(review.getByRole("listitem").filter({ hasText: "Duży" })).toContainText("big / large");
  await expect(review.getByRole("listitem").filter({ hasText: "słowo13" })).toContainText("250-500");
  await expect(review.getByRole("listitem").filter({ hasText: "Niezłomny" })).toContainText("3.5K+");
  await expect(review.getByRole("listitem").filter({ hasText: "Niezłomny" })).toContainText("unyielding / steadfast / indomitable");
}

async function answerAndContinue(page: Page, answer: string) {
  await page.getByRole("button", { name: answer, exact: true }).click();
  await expect(page.getByRole("button", { name: "Next" })).toBeVisible();
  await page.getByRole("button", { name: "Next" }).click();
}

async function expectPublicMarkdown(page: Page, path: string, requiredText: string) {
  const response = await page.request.get(path);
  const body = await response.text();

  expect(response.ok()).toBe(true);
  expect(body).toContain(requiredText);
}

async function openTheoryMenu(page: Page) {
  const nav = page.getByRole("navigation", { name: "Main" });
  const menu = nav.locator("details.app-theory-menu");
  const isOpen = await menu.evaluate((element) => (element as HTMLDetailsElement).open);

  if (!isOpen) {
    await menu.locator("summary").click();
  }

  return menu;
}

async function expectMainNav(page: Page, activeLink: string) {
  const nav = page.getByRole("navigation", { name: "Main" });
  const theoryActive = activeLink === "Progressive methodology" || activeLink === "Adaptive methodology";
  const theorySummary = nav.locator("summary");
  const theoryMenu = nav.locator("details.app-theory-menu");
  const expectedTheoryLabel = theoryActive ? `Theory › ${activeLink}` : "Theory";

  await expect(nav).toBeVisible();
  await expect(nav.getByRole("link", { name: "Polish Passive Vocabulary Size Test" })).toHaveCount(0);
  await expect(theorySummary).toHaveText(expectedTheoryLabel);
  expect(await theoryMenu.evaluate((element) => (element as HTMLDetailsElement).open)).toBe(false);
  await openTheoryMenu(page);

  const links = [
    ["Test", "#/"],
    ["Features", "#/features"],
    ["Progressive methodology", "#/methodology"],
    ["Adaptive methodology", "#/adaptive-methodology"],
  ] as const;

  await expect(nav.getByRole("link")).toHaveText(links.map(([name]) => name));

  for (const [name, href] of links) {
    await expect(nav.getByRole("link", { name, exact: true })).toHaveAttribute("href", href);
  }

  await expect(nav.getByRole("link", { name: activeLink, exact: true })).toHaveAttribute("aria-current", "page");

  if (theoryActive) {
    await expect(theorySummary).toHaveAttribute("aria-current", "page");
  } else {
    await expect(theorySummary).not.toHaveAttribute("aria-current", "page");
  }

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

async function expectThemeSwitcher(page: Page) {
  const frame = page.locator(".app-frame");

  await expect(frame).toHaveAttribute("data-theme", "light");
  await expect(page.getByRole("button", { name: "Light" })).toHaveAttribute("aria-pressed", "true");
  await page.getByRole("button", { name: "Dark" }).click();
  await expect(frame).toHaveAttribute("data-theme", "dark");
  await expect(page.locator("#page-content > main")).toHaveCSS("background-color", "rgb(16, 20, 23)");
  await expect(page.locator("#start-heading")).toHaveCSS("color", "rgb(241, 246, 245)");
  await expect(page.getByRole("button", { name: "Dark" })).toHaveAttribute("aria-pressed", "true");
  await page.getByRole("button", { name: "Light" }).click();
  await expect(frame).toHaveAttribute("data-theme", "light");
}

export async function runAppSmoke(page: Page) {
  await page.setViewportSize({ width: 390, height: 844 });
  await routeSentenceBlock(page);
  await routeAnswerEvents(page);
  await page.addInitScript(() => {
    Math.random = () => 0;
    localStorage.removeItem("vocab-theme");
    localStorage.removeItem("vocab-design");
  });
  await page.goto("/index.html#/");

  await expectPublicMarkdown(
    page,
    "/progressive-vocabulary-testing-methodology.md",
    "Progressive vocabulary test methodology",
  );
  await expectPublicMarkdown(
    page,
    "/vocabulary-size-testing-methodology.md",
    "Polish Vocabulary Size Testing Methodology",
  );

  await expectMainNav(page, "Test");
  await expectThemeSwitcher(page);
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
  const currentSnapshot = page.locator("#current");
  await expect(currentSnapshot.getByText("Sentence-context questions load from")).toBeVisible();
  await expect(currentSnapshot.getByText("/api/sentence-question-blocks")).toBeVisible();
  await expect(page.getByText("Questions load from /api/questions")).toHaveCount(0);
  await expect(page.getByText("Test uses 80 dictionary-form Polish words")).toHaveCount(0);
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
  await expect(page.getByText("Polish sentence context")).toBeVisible();
  await expect(page.getByText("You'll see a Polish sentence. Choose the highlighted word's English meaning.")).toBeVisible();
  await expect(page.getByRole("radio", { name: "Absolute beginner / pre-A1" })).toBeChecked();
  await expect(page.getByRole("radio", { name: "A1", exact: true })).toBeVisible();
  await expect(page.getByRole("radio", { name: "A2", exact: true })).toBeVisible();
  await expect(page.getByRole("radio", { name: "B1", exact: true })).toBeVisible();
  await expect(page.getByRole("radio", { name: "B2", exact: true })).toBeVisible();
  await expect(page.getByRole("radio", { name: "C1", exact: true })).toBeVisible();
  await expect(page.getByRole("radio", { name: "C2", exact: true })).toBeVisible();
  await expect(page.getByRole("radio", { name: "A3", exact: true })).toHaveCount(0);
  await expectReferenceStyling(page);

  await page.getByRole("button", { name: "Begin Test" }).click();

  const firstChoices = ["dog", "bird", "fish", "tree", "cat"];
  await expectSentenceQuestion(page, {
    scored: 0,
    item: 1,
    range: "0-500",
    sentence: "Kot pije wodę.",
    target: "Kot",
    choices: firstChoices,
  });
  await expect(page.getByText("0-250", { exact: true })).toHaveCount(0);
  await expect(page.getByRole("heading", { level: 2, name: "Kot" })).toHaveCount(0);
  await expectNoHorizontalOverflow(page);
  await expectComfortableButtons(page);
  await expect(page.getByLabel("Live estimate")).toContainText(
    "Live estimate of how many dictionary forms of words you know",
  );
  await expect(page.getByLabel("Live estimate")).toContainText(pendingLiveEstimateText);

  await page.getByRole("button", { name: "cat", exact: true }).click();

  await expect(page.getByText("Correct", { exact: true })).toBeVisible();
  await expectProgress(page, 1);
  await expectChoicesLocked(page, firstChoices);
  await expect(page.getByRole("button", { name: "Next" })).toBeVisible();

  await page.getByRole("button", { name: "Next" }).click();

  const secondChoices = ["I sleep", "I walk", "I read", "I wait", "I drink"];
  await expectSentenceQuestion(page, {
    scored: 1,
    item: 2,
    sentence: "Codziennie piję wodę po treningu.",
    target: "piję",
    choices: secondChoices,
  });

  await page.getByRole("button", { name: "I sleep", exact: true }).click();

  await expect(page.getByText("Correct answer: I drink", { exact: true })).toBeVisible();
  await expectProgress(page, 2);
  await expectChoicesLocked(page, secondChoices);
  await expect(page.getByRole("button", { name: "Next" })).toBeVisible();

  await page.getByRole("button", { name: "Next" }).click();

  const thirdChoices = ["small", "fast", "heavy", "quiet", "big / large"];
  await expectSentenceQuestion(page, {
    scored: 2,
    item: 3,
    sentence: "Duży dom stoi przy rzece.",
    target: "Duży",
    choices: thirdChoices,
  });

  await dontKnowButton(page).click();

  await expect(page.getByText("Correct answer: big / large", { exact: true })).toBeVisible();
  await expectProgress(page, 3);
  await expectChoicesLocked(page, thirdChoices);
  await expect(page.getByRole("button", { name: "Next" })).toBeVisible();

  await page.getByRole("button", { name: "Next" }).click();

  for (let item = 4; item <= 79; item++) {
    await expectProgress(page, item - 1);
    if (item === 30) {
      await expect(page.getByLabel("Live estimate")).toContainText(pendingLiveEstimateText);
    }
    await dontKnowButton(page).click();
    await expectProgress(page, item);
    if (item === 30) {
      await expect(page.getByLabel("Live estimate")).toContainText("Current estimate: ");
      await expect(page.getByLabel("Live estimate")).toContainText("Likely range: ");
    }
    await expect(page.getByRole("button", { name: "Next" })).toBeVisible();
    await page.getByRole("button", { name: "Next" }).click();
  }

  const lastChoices = [
    "fragile / weak",
    "flexible",
    "hesitant",
    "temporary",
    "unyielding / steadfast / indomitable",
  ];
  await expectSentenceQuestion(page, {
    scored: 79,
    item: 80,
    sentence: "Niezłomny duch pomaga jej dalej pracować.",
    target: "Niezłomny",
    choices: lastChoices,
  });

  await dontKnowButton(page).click();

  await expect(page.getByText("Correct answer: unyielding / steadfast / indomitable", { exact: true })).toBeVisible();
  await expectProgress(page, 80);
  await expectChoicesLocked(page, lastChoices);

  await page.getByRole("button", { name: "Next" }).click();

  await expect(page.getByRole("heading", { level: 1, name: "Results" })).toBeVisible();
  await expect(page.getByText("1%", { exact: true })).toBeVisible();
  await expect(page.getByText("1 of 80 correct", { exact: true })).toBeVisible();
  await expect(page.getByText("Wrong: 1", { exact: true })).toBeVisible();
  await expect(page.getByText("Don't know: 78", { exact: true })).toBeVisible();
  await expect(page.getByText("Estimated recognized Polish lemmas", { exact: true })).toBeVisible();
  await expect(page.getByText("under 200", { exact: true })).toBeVisible();
  await expect(page.getByText("Likely range: 0-200", { exact: true })).toBeVisible();
  await expect(page.getByText("Approximate level band: Absolute beginner / pre-A1", { exact: true })).toBeVisible();
  await expect(page.getByText("Estimated passive vocabulary", { exact: true })).toHaveCount(0);
  await expect(page.getByText("within +/-150 words", { exact: false })).toHaveCount(0);
  await expect(page.getByText("This test scores recognition of Polish lemmas in sentence context.", { exact: false })).toBeVisible();
  await expectBandBreakdown(page);
  await expectReviewList(page);
  await expectNoHorizontalOverflow(page);

  await expect(page.getByText("Select the best English meaning")).toHaveCount(0);
  await expect(dontKnowButton(page)).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Retake" })).toBeVisible();

  await page.getByRole("button", { name: "Retake" }).click();

  await expectSentenceQuestion(page, {
    scored: 0,
    item: 1,
    range: "0-500",
    sentence: "Kot pije wodę.",
    target: "Kot",
    choices: firstChoices,
  });
  await expect(page.getByText("Correct", { exact: true })).toHaveCount(0);
  await expect(page.getByText("Correct answer:", { exact: false })).toHaveCount(0);
  await expect(page.getByRole("heading", { level: 1, name: "Results" })).toHaveCount(0);
  await expect(page.getByRole("region", { name: "Words to review (79)" })).toHaveCount(0);
}

export async function runHighEstimateRegression(page: Page) {
  const block = sentenceBlockFixture();
  const harderBlock = sentenceBlockFixture({ block: 1, rankStart: 250, rankEnd: 1000 });
  harderBlock.items[0] = sentenceItem(80, {
    sentence: "Trudniejsze słowo pojawia się dalej.",
    target: "Trudniejsze",
    correct: "more difficult",
    distractors: ["easier", "later", "clean", "bright"],
  });
  const correctAnswers = block.items.map((item) => item["correct-translation"]);
  const requestedBlocks: string[] = [];

  await page.setViewportSize({ width: 390, height: 844 });
  await routeAnswerEvents(page);
  await page.route(/\/api\/sentence-question-blocks(\?.*)?$/, async (route) => {
    const url = new URL(route.request().url());
    const requestedBlock = url.searchParams.get("block") ?? "0";
    requestedBlocks.push(requestedBlock);
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(requestedBlock === "1" ? harderBlock : block),
    });
  });
  await page.addInitScript(() => {
    Math.random = () => 0;
    localStorage.removeItem("vocab-theme");
    localStorage.removeItem("vocab-design");
  });
  await page.goto("/index.html#/");
  await page.getByRole("button", { name: "Begin Test" }).click();

  for (const answer of correctAnswers) {
    await answerAndContinue(page, answer);
  }

  await expect(page.getByRole("status")).toContainText("first block was too easy");
  await expectSentenceQuestion(page, {
    scored: 0,
    item: 1,
    range: "250-1K",
    sentence: "Trudniejsze słowo pojawia się dalej.",
    target: "Trudniejsze",
    choices: ["easier", "later", "clean", "bright", "more difficult"],
  });
  expect(requestedBlocks).toContain("0");
  expect(requestedBlocks).toContain("1");
  await expectNoHorizontalOverflow(page);
}

export async function runApiQuestionLoading(page: Page) {
  let requestedUrl = "";
  const block = sentenceBlockFixture();
  block.items[0] = sentenceItem(0, {
    sentence: "To testowe zdanie ładuje się z API.",
    target: "testowe",
    correct: "from the API",
    distractors: ["wrong-a", "wrong-b", "wrong-c", "wrong-d"],
  });

  await page.route(/\/api\/sentence-question-blocks(\?.*)?$/, async (route) => {
    requestedUrl = route.request().url();
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(block),
    });
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.addInitScript(() => {
    Math.random = () => 0;
    localStorage.removeItem("vocab-theme");
    localStorage.removeItem("vocab-design");
  });
  await page.goto("/index.html#/");
  await page.getByRole("radio", { name: "A1", exact: true }).click();
  await page.getByRole("button", { name: "Begin Test" }).click();

  await expectSentenceQuestion(page, {
    scored: 0,
    item: 1,
    sentence: "To testowe zdanie ładuje się z API.",
    target: "testowe",
    choices: ["wrong-a", "wrong-b", "wrong-c", "wrong-d", "from the API"],
  });
  expect(requestedUrl).toContain("/api/sentence-question-blocks");
  expect(requestedUrl).toContain("level=a1");
}

export async function runAnswerEventSubmissionFailure(page: Page) {
  const answerEvents: unknown[] = [];
  let sawWarning = false;

  await page.setViewportSize({ width: 390, height: 844 });
  await routeSentenceBlock(page);
  await routeAnswerEvents(page, 500, answerEvents);
  page.on("console", (message) => {
    if (message.type() === "warning" && message.text().includes("Answer event submission failed")) {
      sawWarning = true;
    }
  });
  await page.addInitScript(() => {
    Math.random = () => 0;
    localStorage.removeItem("vocab-theme");
    localStorage.removeItem("vocab-design");
  });

  await page.goto("/index.html#/");
  await page.getByRole("button", { name: "Begin Test" }).click();
  await page.getByRole("button", { name: "cat", exact: true }).click();

  await expect(page.getByRole("button", { name: "Next" })).toBeVisible();
  await expect.poll(() => answerEvents.length).toBe(1);
  await expect.poll(() => sawWarning).toBe(true);

  const event = answerEvents[0] as Record<string, unknown>;
  expect(event["anonymous-session-id"]).toMatch(
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
  );
  expect(event["test-block-id"]).toBe("pre-a1");
  expect(event["target-lemma-id"]).toBe(1);
  expect(event["target-surface-form-id"]).toBe(2000);
  expect(event["candidate-rank"]).toBe(1);
  expect(event["inventory-stratum"]).toBe(1);
  expect(event["lemma-rank"]).toBe(1);
  expect(event["surface-difficulty-rank"]).toBe(1);
  expect(event["item-type"]).toBe("sentence-context-lemma");
  expect(event["choice-count"]).toBe(5);
  expect(event["guess-rate"]).toBe(0.2);
  expect(event["selected-answer"]).toBe("cat");
  expect(event.correct).toBe(true);
  expect(event["attention-check-status"]).toBe("not-attention-check");

  await page.getByRole("button", { name: "Next" }).click();
  await expectSentenceQuestion(page, {
    scored: 1,
    item: 2,
    sentence: "Codziennie piję wodę po treningu.",
    target: "piję",
    choices: ["I sleep", "I walk", "I read", "I wait", "I drink"],
  });
}
