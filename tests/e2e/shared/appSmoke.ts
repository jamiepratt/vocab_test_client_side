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

async function cssValue(locator: Locator, property: string) {
  return locator.evaluate((element, propertyName) => getComputedStyle(element).getPropertyValue(propertyName), property);
}

function colorChannels(color: string) {
  return (color.match(/[\d.]+/g) ?? []).slice(0, 3).map(Number);
}

function redDominance(color: string) {
  const [red, green, blue] = colorChannels(color);
  return red - Math.max(green, blue);
}

const pendingLiveEstimateText =
  "Not enough questions answered to make an estimate yet, answer at least 30 questions and estimate is updated live as you answer each question.";

const instantScrollUrl = "/index.html?scrollDelayMs=0#/";

const dontKnowButtonName =
  "don't know (don't guess for a more accurate estimate, press this if unsure)";

const lazyRouteTimeout = 15000;

function dontKnowButton(scope: Page | Locator) {
  return scope.getByRole("button", { name: dontKnowButtonName, exact: true });
}

function questionCards(page: Page) {
  return page.locator(".app-question-card");
}

function activeQuestionCard(page: Page) {
  return questionCards(page).last();
}

function questionCard(page: Page, item: number) {
  return page.locator(`#question-card-${item}`);
}

async function answerCurrentQuestion(page: Page, answer: string) {
  await activeQuestionCard(page).getByRole("button", { name: answer, exact: true }).click();
  await expect(page.getByRole("button", { name: "Next" })).toHaveCount(0);
}

async function answerCurrentQuestionFast(page: Page, answer: string) {
  await activeQuestionCard(page).getByRole("button", { name: answer, exact: true }).click();
}

type SentenceItem = {
  "item-id": string;
  sentence: string;
  "sentence-translation": string;
  "target-surface": string;
  "target-surface-form-id": number;
  "highlight-span": { start: number; end: number };
  "lemma-id": number;
  "lemma-pos-id": number;
  "lemma-inventory-rank": number;
  "surface-difficulty-rank": number;
  "lemma-inventory-stratum": number;
  "correct-translation": string;
  distractors: string[];
  "item-type": string;
  "choice-count": number;
  "guess-rate": number;
};

const starterItems = [
  {
    sentence: "Kot pije wodę.",
    translation: "The cat drinks water.",
    target: "Kot",
    correct: "cat",
    distractors: ["dog", "bird", "fish", "tree"],
  },
  {
    sentence: "Codziennie piję wodę po treningu.",
    translation: "I drink water every day after training.",
    target: "piję",
    correct: "I drink",
    distractors: ["I sleep", "I walk", "I read", "I wait"],
  },
  {
    sentence: "Duży dom stoi przy rzece.",
    translation: "A big house stands by the river.",
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
  translation?: string;
  target?: string;
  correct?: string;
  distractors?: string[];
  lemmaRank?: number;
  lemmaInventoryStratum?: number;
  surfaceDifficultyRank?: number;
} = {}): SentenceItem {
  const starter = starterItems[index];
  const target = overrides.target ?? starter?.target ?? `słowo${index + 1}`;
  const sentence = overrides.sentence ?? starter?.sentence ?? `To jest ${target} w krótkim zdaniu.`;
  const translation = overrides.translation ?? starter?.translation ?? `This is ${target} in a short sentence.`;
  const correct = overrides.correct ?? starter?.correct ?? `meaning-${index + 1}`;
  const lemmaRank = overrides.lemmaRank ?? index + 1;
  const surfaceDifficultyRank = overrides.surfaceDifficultyRank ?? lemmaRank;
  const lemmaInventoryStratum = overrides.lemmaInventoryStratum ?? Math.floor((lemmaRank - 1) / 1000) + 1;
  const distractors = overrides.distractors ?? starter?.distractors ?? [
    `wrong-a-${index + 1}`,
    `wrong-b-${index + 1}`,
    `wrong-c-${index + 1}`,
    `wrong-d-${index + 1}`,
  ];

  return {
    "item-id": `example-sentence:${index + 1}`,
    sentence,
    "sentence-translation": translation,
    "target-surface": target,
    "target-surface-form-id": 2000 + index,
    "highlight-span": targetSpan(sentence, target),
    "lemma-id": index + 1,
    "lemma-pos-id": 1000 + index,
    "lemma-inventory-rank": lemmaRank,
    "surface-difficulty-rank": surfaceDifficultyRank,
    "lemma-inventory-stratum": lemmaInventoryStratum,
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
  lemmaRankStart?: number;
} = {}) {
  const block = overrides.block ?? 0;
  const offset = block * 80;
  const lemmaRankStart = overrides.lemmaRankStart ?? offset + 1;

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
      const lemmaRank = lemmaRankStart + index;

      if (block === 0 && index === 79) {
        return sentenceItem(index, {
          sentence: "Niezłomny duch pomaga jej dalej pracować.",
          translation: "Her unyielding spirit helps her keep working.",
          target: "Niezłomny",
          correct: "unyielding / steadfast / indomitable",
          distractors: ["fragile / weak", "flexible", "hesitant", "temporary"],
          lemmaRank,
          surfaceDifficultyRank: lemmaRank,
        });
      }

      return sentenceItem(index + offset, {
        lemmaRank,
        surfaceDifficultyRank: lemmaRank,
      });
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
  const status = page.getByLabel("Quiz status details");
  const progress = page.getByRole("progressbar");

  const statusBox = await status.boundingBox();
  const progressBox = await progress.boundingBox();

  expect(statusBox).not.toBeNull();
  expect(progressBox).not.toBeNull();
  expect(progressBox!.y).toBeGreaterThanOrEqual(statusBox!.y + statusBox!.height);
}

async function expectLiveEstimateBelowQuestionCard(page: Page) {
  const questionCard = activeQuestionCard(page);
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
  rangeColor?: string;
  sentence: string;
  target: string;
  choices: string[];
}) {
  const card = activeQuestionCard(page);
  const sentence = card.getByRole("group", { name: "Polish sentence" });
  const translation = card.getByRole("group", { name: "English translation" });
  const answers = card.getByRole("group", { name: "Answer choices" });
  const target = card.getByRole("term");

  await expectProgress(page, question.scored);
  await expectProgressBelowQuizStatus(page);
  await expectLiveEstimateBelowQuestionCard(page);
  await expect(page.getByText(`Item ${question.item} of 80`, { exact: true })).toBeVisible();
  if (question.range) {
    const rangeBadge = page.getByLabel("Quiz status details").getByText(question.range, { exact: true });
    await expect(rangeBadge).toBeVisible();
    if (question.rangeColor) {
      await expect(rangeBadge).toHaveCSS("color", question.rangeColor);
    }
  }
  const questionHeading = card.getByRole("heading", { level: 2, name: "What does the highlighted word in this sentence mean?" });
  await expect(questionHeading).toBeVisible();
  await expect(questionHeading.locator(".app-target-mark")).toHaveText("highlighted");
  await expect(sentence).toContainText(question.sentence);
  await expect(translation).toHaveCount(0);
  await expect(target).toHaveCount(1);
  await expect(target).toHaveText(question.target);
  await expect(card.getByText("Select the best English meaning")).toHaveCount(0);
  await expect(answers.getByRole("button")).toHaveText(question.choices);

  for (const choice of question.choices) {
    await expect(card.getByRole("button", { name: choice, exact: true })).toBeVisible();
  }

  await expect(dontKnowButton(card)).toBeVisible();
}

async function expectChoicesLocked(scope: Locator, choices: string[]) {
  for (const choice of choices) {
    await expect(scope.getByRole("button", { name: choice, exact: true })).toBeDisabled();
  }

  await expect(dontKnowButton(scope)).toBeDisabled();
}

async function expectAnsweredTranslation(scope: Locator, translationText: string) {
  const translation = scope.getByRole("group", { name: "English translation" });

  await expect(translation).toBeVisible();
  await expect(translation).toHaveText(translationText);
  await expect(translation.locator("mark")).toHaveCount(0);
  await expect(translation.getByRole("term")).toHaveCount(0);
}

async function expectWrongAnswerStrongerThanDontKnow(wrongButton: Locator, dkButton: Locator) {
  const wrongBorder = await cssValue(wrongButton, "border-color");
  const dkBorder = await cssValue(dkButton, "border-color");

  expect(redDominance(wrongBorder)).toBeGreaterThan(redDominance(dkBorder));
}

async function expectObservedLemmaRankBreakdown(page: Page) {
  const breakdown = page.getByRole("region", { name: "Vocabulary estimate by lemma rank" });
  const row = breakdown.getByRole("listitem").filter({ hasText: "Lemma ranks 1-1,000" });

  await expect(page.getByRole("region", { name: "Accuracy by frequency bucket" })).toHaveCount(0);
  await expect(breakdown).toBeVisible();
  await expect(breakdown.getByRole("listitem")).toHaveCount(1);
  await expect(row).toContainText(/Lemma ranks 1-1,000\s*\|\s*observed\s*\|\s*1\/80\s*\|\s*est\. [0-9,]+ \(range [0-9,]+-[0-9,]+\)/);
}

async function expectAssumedKnownLemmaRankBreakdown(page: Page) {
  const breakdown = page.getByRole("region", { name: "Vocabulary estimate by lemma rank" });
  const assumedRow = breakdown.getByRole("listitem").filter({ hasText: "Lemma ranks 1-1,000" });
  const observedRow = breakdown.getByRole("listitem").filter({ hasText: "Lemma ranks 8,001-9,000" });

  await expect(page.getByRole("region", { name: "Accuracy by frequency bucket" })).toHaveCount(0);
  await expect(breakdown).toBeVisible();
  await expect(assumedRow).toContainText(
    /Lemma ranks 1-1,000\s*\|\s*assumed known from higher-rank pass\s*\|\s*not directly tested\s*\|\s*est\. 1,000 \(range 1,000-1,000\)/,
  );
  await expect(observedRow).toContainText(
    /Lemma ranks 8,001-9,000\s*\|\s*observed\s*\|\s*80\/80\s*\|\s*est\. [0-9,]+ \(range [0-9,]+-[0-9,]+\)/,
  );
}

async function expectReviewList(page: Page) {
  const review = page.getByRole("region", { name: "Words to review (79)" });
  const reviewItems = review.getByRole("listitem");

  await expect(review).toBeVisible();
  await expect(reviewItems).toHaveCount(79);

  const reviewText = await reviewItems.allTextContents();
  expect(reviewText.every((text) => /Lemma rank \d+/.test(text))).toBe(true);
  expect(reviewText.some((text) => /1-250|251-500|501-1K|1,001-2K|2,001-3\.5K|3,501\+/.test(text))).toBe(false);
  expect(reviewText[0]).toContain("Lemma rank 90");
  expect(reviewText[0]).toContain("Niezłomny");
  expect(reviewText[0]).toContain("unyielding / steadfast / indomitable");
  expect(reviewText[1]).toContain("Lemma rank 120");
  expect(reviewText[1]).toContain("piję");
  expect(reviewText[1]).toContain("I drink");
  expect(reviewText[2]).toContain("Lemma rank 3");
  expect(reviewText[2]).toContain("Duży");
  expect(reviewText[2]).toContain("big / large");
  expect(reviewText[78]).toContain("Lemma rank 79");
  expect(reviewText[78]).toContain("słowo79");
  expect(reviewText[78]).toContain("meaning-79");
}

async function expectPublicMarkdown(page: Page, path: string, requiredText: string) {
  const response = await page.request.get(path);
  const body = await response.text();

  expect(response.ok()).toBe(true);
  expect(body).toContain(requiredText);
}

async function expectLazyRouteHeading(page: Page, level: 1 | 2, name: string) {
  await expect(page.getByRole("heading", { level, name })).toBeVisible({ timeout: lazyRouteTimeout });
}

async function openNavMenu(page: Page, className: string) {
  const nav = page.getByRole("navigation", { name: "Main" });
  const menu = nav.locator(`details.${className}`);
  const isOpen = await menu.evaluate((element) => (element as HTMLDetailsElement).open);

  if (!isOpen) {
    await menu.locator("summary").click();
  }

  return menu;
}

async function openCurrentMenu(page: Page) {
  return openNavMenu(page, "app-current-menu");
}

async function openTheoryMenu(page: Page) {
  return openNavMenu(page, "app-methodology-menu");
}

async function expectMainNav(page: Page, activeLink: string) {
  const nav = page.getByRole("navigation", { name: "Main" });
  const currentActive = activeLink === "Testing" || activeLink === "Scoring";
  const theoryActive = activeLink === "Progressive methodology" || activeLink === "Adaptive methodology";
  const currentSummary = nav.locator("details.app-current-menu summary");
  const theorySummary = nav.locator("details.app-methodology-menu summary");
  const currentMenu = nav.locator("details.app-current-menu");
  const theoryMenu = nav.locator("details.app-methodology-menu");
  const expectedCurrentLabel = currentActive ? `Current › ${activeLink}` : "Current";
  const expectedTheoryLabel = theoryActive ? `Theory › ${activeLink}` : "Theory";

  await expect(nav).toBeVisible();
  await expect(nav.getByRole("link", { name: "Polish Passive Vocabulary Size Test" })).toHaveCount(0);
  await expect(currentSummary).toHaveText(expectedCurrentLabel);
  await expect(theorySummary).toHaveText(expectedTheoryLabel);
  expect(await currentMenu.evaluate((element) => (element as HTMLDetailsElement).open)).toBe(false);
  expect(await theoryMenu.evaluate((element) => (element as HTMLDetailsElement).open)).toBe(false);
  await openCurrentMenu(page);
  await openTheoryMenu(page);

  const links = [
    ["Test", "#/"],
    ["Features", "#/features"],
    ["Testing", "#/current/testing"],
    ["Scoring", "#/current/scoring"],
    ["Progressive methodology", "#/methodology"],
    ["Adaptive methodology", "#/adaptive-methodology"],
  ] as const;

  await expect(nav.getByRole("link")).toHaveText(links.map(([name]) => name));

  for (const [name, href] of links) {
    await expect(nav.getByRole("link", { name, exact: true })).toHaveAttribute("href", href);
  }

  await expect(nav.getByRole("link", { name: activeLink, exact: true })).toHaveAttribute("aria-current", "page");

  if (currentActive) {
    await expect(currentSummary).toHaveAttribute("aria-current", "page");
  } else {
    await expect(currentSummary).not.toHaveAttribute("aria-current", "page");
  }

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

async function expectAccordionState(page: Page, openNames: string[], closedNames: string[]) {
  for (const name of openNames) {
    await expect(page.getByRole("button", { name, exact: true })).toHaveAttribute("aria-expanded", "true");
  }

  for (const name of closedNames) {
    await expect(page.getByRole("button", { name, exact: true })).toHaveAttribute("aria-expanded", "false");
  }
}

async function expectQuickPreset(page: Page) {
  await expect(page.getByRole("button", { name: /Quick/ })).toHaveAttribute("aria-pressed", "true");
  await expect(page.getByRole("button", { name: /Guide/ })).toHaveAttribute("aria-pressed", "false");
  await expect(page.getByRole("button", { name: /Detail/ })).toHaveAttribute("aria-pressed", "false");
}

async function expectCurrentTestingPage(page: Page) {
  await expectLazyRouteHeading(page, 1, "Current vocabulary size testing");
  await expectQuickPreset(page);
  await expectAccordionState(
    page,
    ["What the test measures", "Choose a starting level"],
    ["Answer sentence items", "Easier or harder continuation", "Testing methodology details"],
  );
  await expect(page.getByRole("radiogroup", { name: "Example starting level" })).toBeVisible();

  await page.getByRole("button", { name: /Guide/ }).click();
  await expect(page.getByRole("button", { name: /Guide/ })).toHaveAttribute("aria-pressed", "true");
  await expectAccordionState(
    page,
    ["What the test measures", "Choose a starting level", "Answer sentence items", "Easier or harder continuation"],
    ["Testing methodology details"],
  );
  const sampleQuestion = page.getByRole("article", { name: "Example sentence question" });
  await expect(sampleQuestion).toBeVisible();
  await expect(sampleQuestion.getByRole("term")).toHaveText("piję");

  await page.getByRole("button", { name: "Answer sentence items", exact: true }).click();
  await expect(page.getByRole("button", { name: "Answer sentence items", exact: true })).toHaveAttribute("aria-expanded", "false");
  await expect(page.getByRole("article", { name: "Example sentence question" })).toHaveCount(0);

  await page.getByRole("button", { name: /Detail/ }).click();
  await expect(page.getByRole("button", { name: /Detail/ })).toHaveAttribute("aria-pressed", "true");
  await expectAccordionState(
    page,
    ["What the test measures", "Choose a starting level", "Answer sentence items", "Easier or harder continuation", "Testing methodology details"],
    [],
  );
  await expect(page.getByRole("link", { name: "vocabulary-size-testing.md" })).toHaveAttribute(
    "href",
    "vocabulary-size-testing.md",
  );
}

async function expectCurrentScoringPage(page: Page) {
  await expectLazyRouteHeading(page, 1, "Current vocabulary size scoring");
  await expectQuickPreset(page);
  await expectAccordionState(
    page,
    ["What the score means", "Live estimate and final result"],
    ["Guessing handling", "Very low and uneven results", "Scoring model details"],
  );
  await expect(page.getByRole("region", { name: "Example live estimate" })).toContainText("Likely range: 1,050-1,900");
  await expect(page.getByRole("article", { name: "Example final result" })).toContainText("Approximate level: A2");

  await page.getByRole("button", { name: /Guide/ }).click();
  await expectAccordionState(
    page,
    ["What the score means", "Live estimate and final result", "Guessing handling", "Very low and uneven results"],
    ["Scoring model details"],
  );
  await expect(page.getByText("under 200", { exact: true })).toBeVisible();

  await page.getByRole("button", { name: /Detail/ }).click();
  await expect(page.getByText("latent-guessing-v1", { exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "vocabulary-size-scoring.md" })).toHaveAttribute(
    "href",
    "vocabulary-size-scoring.md",
  );
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
  const block = sentenceBlockFixture();
  block.items[1] = {
    ...block.items[1],
    "lemma-inventory-rank": 120,
    "surface-difficulty-rank": 120,
    "lemma-inventory-stratum": 1,
  };
  block.items[79] = {
    ...block.items[79],
    "lemma-inventory-rank": 90,
    "surface-difficulty-rank": 90,
    "lemma-inventory-stratum": 1,
  };

  await page.setViewportSize({ width: 390, height: 844 });
  await routeSentenceBlock(page, block);
  await routeAnswerEvents(page);
  await page.addInitScript(() => {
    Math.random = () => 0;
    localStorage.removeItem("vocab-theme");
    localStorage.removeItem("vocab-design");
  });
  await page.goto(instantScrollUrl);

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
  await expectPublicMarkdown(
    page,
    "/vocabulary-size-testing.md",
    "Current Vocabulary Size Testing",
  );
  await expectPublicMarkdown(
    page,
    "/vocabulary-size-scoring.md",
    "latent-guessing-v1",
  );

  await expectMainNav(page, "Test");
  await expectThemeSwitcher(page);

  await openCurrentMenu(page);
  await page.getByRole("link", { name: "Testing", exact: true }).click();
  await expect(page).toHaveURL(/#\/current\/testing$/);
  await expectCurrentTestingPage(page);
  await expectMainNav(page, "Testing");

  await openCurrentMenu(page);
  await page.getByRole("link", { name: "Scoring", exact: true }).click();
  await expect(page).toHaveURL(/#\/current\/scoring$/);
  await expectCurrentScoringPage(page);
  await expectMainNav(page, "Scoring");

  await openTheoryMenu(page);
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
  await expectLazyRouteHeading(page, 1, "Adaptive vocabulary size testing from a frequency list");
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
  await expectLazyRouteHeading(page, 1, "Vocabulary test features to implement");
  await expect(page.getByRole("heading", { level: 2, name: "Current app snapshot" })).toBeVisible();
  const currentSnapshot = page.locator("#current");
  await expect(currentSnapshot.getByText("Sentence-context questions load from")).toBeVisible();
  await expect(currentSnapshot.getByText("/api/sentence-question-blocks")).toBeVisible();
  await expect(page.getByText("Questions load from /api/questions")).toHaveCount(0);
  await expect(page.getByText("Test uses 80 dictionary-form Polish words")).toHaveCount(0);
  await expectMainNav(page, "Features");

  await page.goto("/adaptive-vocabulary-testing.html");
  await expect(page).toHaveURL(/index\.html#\/adaptive-methodology$/);
  await expectLazyRouteHeading(page, 1, "Adaptive vocabulary size testing from a frequency list");
  await expectMainNav(page, "Adaptive methodology");

  await page.goto("/features-to-implement.html");
  await expect(page).toHaveURL(/index\.html#\/features$/);
  await expectLazyRouteHeading(page, 1, "Vocabulary test features to implement");
  await expectMainNav(page, "Features");

  await page.goto(instantScrollUrl);

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
    range: "1-500",
    rangeColor: "rgb(153, 27, 27)",
    sentence: "Kot pije wodę.",
    target: "Kot",
    choices: firstChoices,
  });
  await expect(page.getByText("1-250", { exact: true })).toHaveCount(0);
  await expect(page.getByRole("heading", { level: 2, name: "Kot" })).toHaveCount(0);
  await expectNoHorizontalOverflow(page);
  await expectComfortableButtons(page);
  await expect(page.getByLabel("Live estimate")).toContainText(
    "Live estimate of how many dictionary forms of words you know",
  );
  await expect(page.getByLabel("Live estimate")).toContainText(pendingLiveEstimateText);
  const autoScroll = page.getByRole("combobox", { name: "Auto-scroll behavior" });
  await expect(autoScroll).toHaveValue("0");
  await expect(autoScroll.locator("option")).toHaveText([
    "New question immediately",
    "Review correct answer for 3 seconds",
    "Review correct answer for 5 seconds",
    "Review correct answer for 10 seconds",
  ]);
  await autoScroll.selectOption("3000");
  await expect(autoScroll).toHaveValue("3000");
  await autoScroll.selectOption("0");

  await answerCurrentQuestion(page, "cat");

  await expect(questionCards(page)).toHaveCount(2);
  await expect(questionCard(page, 1).getByText("Correct", { exact: true })).toBeVisible();
  await expectAnsweredTranslation(questionCard(page, 1), "The cat drinks water.");
  await expectProgress(page, 1);
  await expectChoicesLocked(questionCard(page, 1), firstChoices);

  const secondChoices = ["I sleep", "I walk", "I read", "I wait", "I drink"];
  await expectSentenceQuestion(page, {
    scored: 1,
    item: 2,
    sentence: "Codziennie piję wodę po treningu.",
    target: "piję",
    choices: secondChoices,
  });

  await answerCurrentQuestion(page, "I sleep");

  await expect(questionCard(page, 2).getByText("Correct answer: I drink", { exact: true })).toBeVisible();
  await expectAnsweredTranslation(questionCard(page, 2), "I drink water every day after training.");
  await expectProgress(page, 2);
  await expectChoicesLocked(questionCard(page, 2), secondChoices);

  const thirdChoices = ["small", "fast", "heavy", "quiet", "big / large"];
  await expectSentenceQuestion(page, {
    scored: 2,
    item: 3,
    sentence: "Duży dom stoi przy rzece.",
    target: "Duży",
    choices: thirdChoices,
  });

  await dontKnowButton(activeQuestionCard(page)).click();

  await expect(questionCard(page, 3).getByText("Correct answer: big / large", { exact: true })).toBeVisible();
  await expectAnsweredTranslation(questionCard(page, 3), "A big house stands by the river.");
  await expectWrongAnswerStrongerThanDontKnow(
    questionCard(page, 2).getByRole("button", { name: "I sleep", exact: true }),
    dontKnowButton(questionCard(page, 3)),
  );
  await expectProgress(page, 3);
  await expectChoicesLocked(questionCard(page, 3), thirdChoices);

  for (let item = 4; item <= 79; item++) {
    await expectProgress(page, item - 1);
    if (item === 30) {
      await expect(page.getByLabel("Live estimate")).toContainText(pendingLiveEstimateText);
    }
    await dontKnowButton(activeQuestionCard(page)).click();
    await expectProgress(page, item);
    if (item === 30) {
      await expect(page.getByLabel("Live estimate")).toContainText("Current estimate: ");
      await expect(page.getByLabel("Live estimate")).toContainText("Likely range: ");
    }
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

  await answerCurrentQuestion(page, "fragile / weak");

  await expect(questionCard(page, 80).getByText("Correct answer: unyielding / steadfast / indomitable", { exact: true })).toBeVisible();
  await expectAnsweredTranslation(questionCard(page, 80), "Her unyielding spirit helps her keep working.");
  await expectProgress(page, 80);
  await expectChoicesLocked(questionCard(page, 80), lastChoices);
  await expect(questionCards(page)).toHaveCount(80);

  await expect(page.getByRole("heading", { level: 1, name: "Results" })).toBeVisible();
  await expect(page.getByText("1%", { exact: true })).toBeVisible();
  await expect(page.getByText("1 of 80 correct", { exact: true })).toBeVisible();
  await expect(page.getByText("Wrong: 2", { exact: true })).toBeVisible();
  await expect(page.getByText("Don't know: 77", { exact: true })).toBeVisible();
  await expect(page.getByText("Estimated recognized Polish lemmas", { exact: true })).toBeVisible();
  await expect(page.getByText("under 200", { exact: true })).toBeVisible();
  await expect(page.getByText("Likely range: 0-200", { exact: true })).toBeVisible();
  await expect(page.getByText("Approximate level: Absolute beginner / pre-A1", { exact: true })).toBeVisible();
  await expect(page.getByText("Estimated passive vocabulary", { exact: true })).toHaveCount(0);
  await expect(page.getByText("within +/-150 words", { exact: false })).toHaveCount(0);
  await expect(page.getByText("This test scores recognition of Polish lemmas in sentence context.", { exact: false })).toBeVisible();
  await expectObservedLemmaRankBreakdown(page);
  await expectReviewList(page);
  await expectNoHorizontalOverflow(page);

  await expect(page.getByRole("button", { name: "Next" })).toHaveCount(0);
  await expect(dontKnowButton(questionCard(page, 80))).toBeDisabled();
  await expect(page.getByRole("button", { name: "Retake" })).toBeVisible();

  await page.getByRole("button", { name: "Retake" }).click();

  await expectSentenceQuestion(page, {
    scored: 0,
    item: 1,
    range: "1-500",
    rangeColor: "rgb(153, 27, 27)",
    sentence: "Kot pije wodę.",
    target: "Kot",
    choices: firstChoices,
  });
  await expect(page.getByText("Correct", { exact: true })).toHaveCount(0);
  await expect(page.getByText("Correct answer:", { exact: false })).toHaveCount(0);
  await expect(page.getByRole("heading", { level: 1, name: "Results" })).toHaveCount(0);
  await expect(questionCards(page)).toHaveCount(1);
  await expect(page.getByRole("region", { name: "Words to review (79)" })).toHaveCount(0);
}

export async function runHighEstimateRegression(page: Page) {
  const block = sentenceBlockFixture({ rankStart: 8001, rankEnd: 8080, lemmaRankStart: 8001 });
  const harderBlock = sentenceBlockFixture({ block: 1, rankStart: 9001, rankEnd: 9080, lemmaRankStart: 9001 });
  harderBlock.items[0] = sentenceItem(80, {
    sentence: "Trudniejsze słowo pojawia się dalej.",
    target: "Trudniejsze",
    correct: "more difficult",
    distractors: ["easier", "later", "clean", "bright"],
    lemmaRank: 9001,
    surfaceDifficultyRank: 501,
  });
  harderBlock.items = [harderBlock.items[0]];
  const correctAnswers = block.items.map((item) => item["correct-translation"]);
  const harderCorrectAnswers = harderBlock.items.map((item) => item["correct-translation"]);
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
  await page.goto(instantScrollUrl);
  await page.getByRole("radio", { name: "C2", exact: true }).click();
  await page.getByRole("button", { name: "Begin Test" }).click();

  for (let index = 0; index < correctAnswers.length; index++) {
    await answerCurrentQuestionFast(page, correctAnswers[index]);
    if (index < correctAnswers.length - 1) {
      await expect(questionCard(page, index + 2)).toBeVisible();
    }
  }

  await expect(page.getByRole("status")).toContainText("first block was too easy");
  await expect(page.getByText("0 / 1 scored", { exact: true })).toBeVisible();
  await expect(page.getByText("Item 1 of 1", { exact: true })).toBeVisible();
  await expect(page.getByLabel("Quiz status details").getByText("8,000-15,000", { exact: true })).toHaveCSS(
    "color",
    "rgb(109, 40, 217)",
  );
  await expect(activeQuestionCard(page).getByText("Trudniejsze słowo pojawia się dalej.")).toBeVisible();
  await expect(activeQuestionCard(page).getByRole("term")).toHaveText("Trudniejsze");
  await expect(activeQuestionCard(page).getByRole("group", { name: "Answer choices" }).getByRole("button")).toHaveText([
    "easier",
    "later",
    "clean",
    "bright",
    "more difficult",
  ]);
  await expect(dontKnowButton(activeQuestionCard(page))).toBeVisible();
  expect(requestedBlocks).toContain("0");
  expect(requestedBlocks).toContain("1");

  for (const answer of harderCorrectAnswers) {
    await answerCurrentQuestionFast(page, answer);
  }

  await expect(page.getByRole("heading", { level: 1, name: "Results" })).toBeVisible();
  await expect(page.getByText("Estimated recognized Polish lemmas", { exact: true })).toBeVisible();
  await expectAssumedKnownLemmaRankBreakdown(page);
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
  await page.goto(instantScrollUrl);
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

  await page.goto(instantScrollUrl);
  await page.getByRole("button", { name: "Begin Test" }).click();
  await answerCurrentQuestion(page, "cat");

  await expect(questionCard(page, 1).getByText("Correct", { exact: true })).toBeVisible();
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
  expect(event["lemma-inventory-stratum"]).toBe(1);
  expect(event).not.toHaveProperty("inventory-stratum");
  expect(event).not.toHaveProperty("fixed-stratum");
  expect(event["lemma-rank"]).toBe(1);
  expect(event["surface-difficulty-rank"]).toBe(1);
  expect(event["item-type"]).toBe("sentence-context-lemma");
  expect(event["choice-count"]).toBe(5);
  expect(event["guess-rate"]).toBe(0.2);
  expect(event["selected-answer"]).toBe("cat");
  expect(event.correct).toBe(true);
  expect(event["attention-check-status"]).toBe("not-attention-check");

  await expectSentenceQuestion(page, {
    scored: 1,
    item: 2,
    sentence: "Codziennie piję wodę po treningu.",
    target: "piję",
    choices: ["I sleep", "I walk", "I read", "I wait", "I drink"],
  });
}
