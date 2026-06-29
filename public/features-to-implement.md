# Vocabulary test features to implement

Feature plan for the current Polish sentence-context vocabulary-size test and backlog: sentence-question loading, lemma scoring, adaptive continuation, result review, methodology docs, calibration path, and progressive confidence with early finish.

Tags: Static HTML, GitHub Pages ready, Sentence context, Lemma scoring, Vocabulary estimate, Calibration

## Current app snapshot

- Reagent/Shadow app served by `public/index.html` with Test, Features, and methodology routes.
- Sentence-context questions load from `/api/sentence-question-blocks`, backed by database example sentences and optional configured API base URL.
- Test uses 80 Polish sentence-context lemma items per block, with adaptive continuation when the first block is outside the informative range.
- Each quiz item shows a Polish sentence with one highlighted target form, served rank window, progress bar, English choices, and `don't know`.
- Answering locks choices, shows immediate feedback, and reveals the correct answer for misses.
- Results show accuracy, answered/wrong/don't know counts, accuracy by frequency bucket, words to review, estimated recognized Polish lemmas, likely range, approximate level, and Retake.
- Methodology pages explain sentence-context vocabulary-size measurement and the staged path toward calibrated adaptive testing.

## Goal

Build from the current sentence-context multiple-choice test toward a calibrated receptive vocabulary test that estimates how many Polish lemmas a learner probably recognizes in context. Later phases can add calibrated item difficulty, richer reliability checks, result history, and email updates.

Main product bet: start with frequency as the difficulty proxy, ask 80 items at launch to average out item-specific difficulty and gather calibration data, then use collected responses to learn real item difficulty and shorten future tests.

## User experience

Current question:

> Kot pije wodę.
>
> highlighted: **Kot**
>
> 1-250

Choices:

- dog
- bird
- fish
- tree
- cat
- don't know

Current requirements:

- Show visible progress as `current / 80` plus an accessible progress bar.
- Show the current served rank window without exposing scoring internals.
- Include `don't know` on every item and tell users not to guess.
- Lock answer buttons after a choice.
- Show immediate correct/incorrect feedback.
- Show the correct English answer after wrong or `don't know`.
- Let the user move forward only after the answer is locked.
- End with a result page that supports review and retake.

Future UX requirements:

- Keep complete sentence context with exactly one highlighted tested token.
- Support inflected displayed forms while scoring the linked lemma.
- Add selective reliability checks after some claimed-known answers.
- Ask for email opt-in before or after results if the user wants calibration updates.
- Show a vocabulary-over-time graph for returning users with center, lower, and upper estimate lines.
- While the quiz is in progress, show how confident the estimate is and allow finishing early once the learner is satisfied with that precision.

## Scoring model

Current scoring uses sentence-context lemma items grouped into fixed lemma-inventory strata. Correct answers count as recognized, wrong and `don't know` count as unknown, and forced-choice guessing is accounted for in the posterior update. Results foreground a likely range, not only a point estimate.

| Concept | Current stored value | Why it matters |
|---|---|---|
| Sentence | `Kot pije wodę.` | User-visible context. |
| Target surface | `Kot` | Highlighted form the learner answers about. |
| Lemma id | `11` | Recognition is scored against the lemma. |
| Inventory stratum | `1` | Lemma-inventory bin used for posterior estimates. |
| Correct answer | `cat` | Recognized item when selected. |
| Distractors | `dog`, `bird`, `fish`, `tree` | Plausible alternatives. |

Sentence-based scoring counts lemmas, not every observed surface form. If the sentence tests `spotkaliśmy`, the vocabulary item counted is the lemma `spotkać`. Future reliability checks can convert unsupported claimed-known answers to unknown and contribute to a reliability flag.

## Feature list

### Implemented baseline

- 80-item sentence-context multiple-choice blocks.
- Visible rank-window labels, progress, and live estimate readiness.
- Sentence-question data from the API.
- Immediate feedback and locked answers.
- Frequency-bucket result breakdown.
- Words-to-review list.
- Recognized-lemma estimate, likely range, approximate level, and methodology routes.

### Sentence item bank

- Store sentence text, tested token offsets, surface form, lemma, translation, frequency rank, and distractors.
- Support multiple sentence examples per lemma.
- Exclude ambiguous, proper-name, offensive, or unnatural examples.

### Sentence recognition mode

- Render sentence with one highlighted token.
- Capture yes, no, not sure, skipped, and response time.
- Treat no, not sure, and skipped as unknown for ability scoring.

### Meaning check

- Trigger after a configurable percentage of yes answers.
- Use one correct option plus plausible distractors.
- Record whether the check confirms or contradicts self-report.

### Vocabulary estimate range

- Estimate known lemmas, not known word forms.
- Show a center estimate plus lower and upper range.
- Attach a confidence or reliability label from answer count, rank-bucket coverage, checks, and timing.

### Progressive confidence and early finish

- Recompute the provisional estimate and uncertainty after each locked answer.
- Show user-facing confidence such as `Low`, `Medium`, `High`, or a percentage-like precision score.
- Explain the tradeoff in-product: continue for a tighter estimate, or finish now for a wider range.
- Enable `Show results now` after minimum item count and rank-bucket coverage rules are met.
- Record early-finished sessions with item count, confidence level, and wider uncertainty range.

### Calibration data

- Persist anonymized response events for calibration.
- Collect enough responses per item or lemma to learn real difficulty.
- Compare observed difficulty against frequency rank.
- Track calibration version so older completed tests can be re-estimated.

### Result history

- Store each completed estimate with timestamp, calibration version, center estimate, lower bound, and upper bound.
- For repeat test-takers, chart vocabulary size over time.
- Show the center line plus upper and lower uncertainty bounds.

### Calibration emails

- Collect explicit opt-in before sending follow-up estimate emails.
- Email users when a calibration round materially changes their vocabulary estimate.
- Include the new estimate, range, calibration date, and a link back to their result history.

### Short adaptive test

- Select items using learned difficulty instead of frequency alone.
- Stop when the confidence range is narrow enough for the product promise.
- Keep some calibration-tail items so the item bank keeps improving.

## Implementation phases

| Phase | Status | Build | Done when |
|---|---|---|---|
| 1. Sentence-context test route | Done | 80 sentence items, answer choices, `don't know`, progress, feedback, retake. | Browser flow works end to end from `#/`. |
| 2. Structured item loading | Done | Sentence-question blocks served through `/api/sentence-question-blocks`. | App can load questions from local or configured API base URL. |
| 3. Initial scoring and results | Done | Stratum posterior estimate, likely range, review list, approximate level. | Result page reports range and frequency-bucket breakdown. |
| 4. Telemetry export | Done | Persist anonymized response events for calibration. | Each event has item id, response, timing, ranks, and scoring metadata. |
| 5. Estimate range | Done | Add lower/center/upper estimate and live readiness. | Result page shows range, not only a single estimate. |
| 6. Progressive confidence and early finish | New | Show live estimate confidence during the quiz and allow finishing early. | User can stop after minimum evidence and results show early-stop confidence/range. |
| 7. Reliability checks | Future | Optional quality items and selective meaning checks. | Scoring can flag unreliable sessions. |
| 8. Result history | Future | Store repeat results and render vocabulary-over-time graph. | Returning users can see estimate movement across completed tests. |
| 9. Calibration emails | Future | Recompute previous estimates after calibration rounds and email opted-in users when estimates change. | Users can receive updated vocabulary estimates. |
| 10. Short adaptive test | Future | Select items using learned difficulty instead of frequency alone. | Test reaches similar confidence with fewer questions. |

## Data shape

Current sentence item:

```json
{
  "item-id": "example-sentence:101",
  "sentence": "Kot pije wodę.",
  "target-surface": "Kot",
  "highlight-span": {"start": 0, "end": 3},
  "lemma-id": 11,
  "lemma-pos-id": 111,
  "lemma-inventory-rank": 42,
  "surface-difficulty-rank": 17,
  "inventory-stratum": 1,
  "fixed-stratum": 1,
  "correct-translation": "cat",
  "distractors": ["dog", "bird", "fish", "tree"],
  "item-type": "sentence-context-lemma",
  "choice-count": 5,
  "guess-rate": 0.2
}
```

Future sentence item:

```json
{
  "item_id": "pl-spotkac-001",
  "language": "pl",
  "sentence": "Wczoraj spotkaliśmy dawnego kolegę przed teatrem.",
  "target": {
    "surface": "spotkaliśmy",
    "lemma": "spotkać",
    "start": 8,
    "end": 19,
    "frequency_rank": 812
  },
  "meaning_check": {
    "correct": "we met",
    "distractors": ["we waited", "we carried", "we watched"]
  },
  "calibration": {
    "difficulty": null,
    "responses": 0,
    "version": "2026-06-launch"
  }
}
```

Future live progress estimate:

```json
{
  "answered": 32,
  "minimum_items_met": true,
  "rank_bucket_coverage_met": true,
  "can_finish": true,
  "estimate": {
    "center": 850,
    "lower": 550,
    "upper": 1250,
    "confidence_label": "Medium"
  }
}
```

Future saved result:

```json
{
  "result_id": "result-123",
  "user_id": "user-456",
  "completed_at": "2026-06-24T12:30:00Z",
  "calibration_version": "2026-06-launch",
  "item_count": 80,
  "stopped_early": false,
  "estimate": {
    "center": 4200,
    "lower": 3600,
    "upper": 4900,
    "confidence_label": "High"
  },
  "email_opt_in": true,
  "email": "learner@example.com"
}
```

## Initial sampling rule

Before calibration, assume word difficulty equals frequency difficulty. Compensate for that rough assumption by starting with 80 test items, including extra calibration items, and sampling across the frequency curve.

1. Choose a broad set of rank buckets.
2. Sample several words or lemmas from each bucket.
3. Use one visible item per word or lemma per session.
4. Randomize item order enough to reduce fatigue effects.
5. Reserve some claimed-known answers for meaning checks once sentence mode exists.
6. Keep extra calibration items in the test until item difficulty estimates stabilize.
7. Reduce item count gradually after each calibration round while preserving estimate confidence.
8. Let users finish early after minimum coverage once they accept the current uncertainty range.

After many responses, compute which items are easier or harder than frequency predicts. Then shift from broad long tests to shorter adaptive tests. Each calibration round should also re-estimate previous completed tests and queue email updates for opted-in users whose estimate changed.

## Open questions

- What confidence labels and range widths are honest enough for early finish?
- What minimum item count and rank-bucket coverage are required before `Show results now` appears?
- Should low confidence hide early finish or allow it with a stronger warning?
- What percentage of yes answers should receive meaning checks?
- Should a failed meaning check reduce trust globally or only score that item as unknown?
- What data store will hold response events before calibration tooling exists?
- What threshold counts as a material estimate change worth emailing?
- What identity/session model should connect repeat tests into one result history?
- Which lemmatizer or source data will provide reliable Polish lemma mappings?

## GitHub Pages deployment

The HTML version is static HTML under `public/`. The release build copies `public/*.md` into `target/release/public/`, and the deploy workflow copies `public/*.html` before uploading the artifact.

Expected deployed HTML file: `features-to-implement.html`, relative to the Pages site root.

## Related files

- `public/features-to-implement.html`
- `public/features-to-implement.md`
- `src/jamiepratt/vocab_test_client_side/core.cljs`
- `src/jamiepratt/vocab_test_client_side/scoring.cljc`
- `src/jamiepratt/vocab_test_client_side/questions.clj`

Last updated June 27, 2026.
