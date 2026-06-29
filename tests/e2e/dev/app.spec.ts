import { test } from "@playwright/test";
import {
  runAnswerEventSubmissionFailure,
  runApiQuestionLoading,
  runAppSmoke,
  runHighEstimateRegression,
} from "../shared/appSmoke";

test("renders and responds to user input", async ({ page }) => {
  test.setTimeout(60000);
  await runAppSmoke(page);
});

test("keeps high estimate copy consistent with the displayed estimate", async ({ page }) => {
  await runHighEstimateRegression(page);
});

test("loads questions from the configured API", async ({ page }) => {
  await runApiQuestionLoading(page);
});

test("continues when answer-event submission fails", async ({ page }) => {
  await runAnswerEventSubmissionFailure(page);
});
