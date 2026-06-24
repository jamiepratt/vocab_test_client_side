# Vocabulary test features to implement

Feature plan for the current Polish-to-English vocabulary test and backlog: 80-item multiple-choice baseline, frequency-band scoring, result review, methodology docs, calibration path, and progressive confidence with early finish.

Tags: Static HTML, GitHub Pages ready, Multiple choice, Frequency bands, Vocabulary estimate, Calibration

## Current app snapshot

- Reagent/Shadow app served by `public/index.html` with Test and Testing methodology routes.
- Questions load from `/api/questions`, backed by structured EDN and optional configured API base URL.
- Test uses 80 dictionary-form Polish words across six bands: 12, 16, 18, 16, 10, and 8 items.
- Each quiz item shows Polish word, part of speech, band badge, progress bar, four English choices, and `don't know`.
- Answering locks choices, shows immediate feedback, reveals the correct answer for misses, then enables Next.
- Results show accuracy, answered/wrong/don't know counts, accuracy by frequency band, words to review, ceiling band, passive-vocabulary estimate, comparison to a 500-word guess, interpretation, honesty note, Polish case note, passive-vs-active note, and Retake.
- Methodology page explains the staged path from frequency-ranked launch test to calibrated adaptive test.

## Goal

Build from the current dictionary-form multiple-choice launch test toward a receptive vocabulary test that estimates how many target-language lemmas a learner probably knows. The current app tests isolated dictionary forms; later phases can add sentence context, inflected forms, meaning checks, telemetry, calibration, history, and email updates.

Main product bet: start with frequency as the difficulty proxy, ask 80 items at launch to average out item-specific difficulty and gather calibration data, then use collected responses to learn real item difficulty and shorten future tests.

## User experience

Current question:

> **woda**
>
> noun
>
> 0-250

Choices:

- fire
- air
- earth
- water
- don't know

Current requirements:

- Show visible progress as `current / 80` plus an accessible progress bar.
- Show the current frequency band without exposing scoring internals.
- Include `don't know` on every item and tell users not to guess.
- Lock answer buttons after a choice.
- Show immediate correct/incorrect feedback.
- Show the correct English answer after wrong or `don't know`.
- Let the user move forward only after the answer is locked.
- End with a result page that supports review and retake.

Future UX requirements:

- Show complete sentence context with exactly one highlighted tested token.
- Support inflected displayed forms while scoring the lemma.
- Ask selective meaning checks after some claimed-known answers.
- Ask for email opt-in before or after results if the user wants calibration updates.
- Show a vocabulary-over-time graph for returning users with center, lower, and upper estimate lines.
- While the quiz is in progress, show how confident the estimate is and allow finishing early once the learner is satisfied with that precision.

## Scoring model

Current scoring uses dictionary-form words grouped by frequency band. Correct answers count as known, wrong and `don't know` count as unknown. The estimate sums each band size times that band's hit rate, then applies a small guessing-bias penalty from wrong answers.

| Concept | Current stored value | Why it matters |
|---|---|---|
| Word | `woda` | User-visible tested dictionary form. |
| Word class | `noun` | Helps disambiguate translation choices. |
| Band | `B1` | Frequency band used for weighting and result breakdown. |
| Correct answer | `water` | Known item when selected. |
| Wrong answers | `fire`, `air`, `earth` | Plausible distractors. |

Future sentence-based scoring should count lemmas, not every observed surface form. If the sentence tests `spotkaliśmy`, the vocabulary item counted is the lemma `spotkać`. A yes answer should count as known only when no verification is requested, or when the meaning check is answered correctly. Failed meaning checks convert that item to unknown and contribute to a reliability flag.

## Feature list

### Implemented baseline

- 80-item multiple-choice test.
- Six frequency bands with visible band labels.
- Structured question data from the API.
- Immediate feedback and locked answers.
- Banded result breakdown.
- Words-to-review list.
- Passive vocabulary estimate, ceiling band, interpretation, and honesty note.
- Testing methodology route.

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
- Attach a confidence or reliability label from answer count, band coverage, checks, and timing.

### Progressive confidence and early finish

- Recompute the provisional estimate and uncertainty after each locked answer.
- Show user-facing confidence such as `Low`, `Medium`, `High`, or a percentage-like precision score.
- Explain the tradeoff in-product: continue for a tighter estimate, or finish now for a wider range.
- Enable `Show results now` after minimum item count and band-coverage rules are met.
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
| 1. Multiple-choice launch test | Done | 80 questions, four choices, `don't know`, progress, feedback, retake. | Browser flow works end to end. |
| 2. Structured item loading | Done | EDN question bank served through `/api/questions`. | App can load questions from local or configured API base URL. |
| 3. Initial scoring and results | Done | Band hit rates, weighted estimate, guessing penalty, review list, interpretation. | Result page reports estimate and band breakdown. |
| 4. Telemetry export | Next | Persist anonymized response events for calibration. | Each event has item id, response, timing, band, and scoring version. |
| 5. Estimate range | Next | Add lower/center/upper estimate and reliability label. | Result page shows range, not only a single estimate. |
| 6. Progressive confidence and early finish | New | Show live estimate confidence during the quiz and allow finishing early. | User can stop after minimum evidence and results show early-stop confidence/range. |
| 7. Sentence item bank and checks | Future | Sentence context, highlighted token, lemma, meaning checks. | Scoring can validate claimed-known sentence items. |
| 8. Result history | Future | Store repeat results and render vocabulary-over-time graph. | Returning users can see estimate movement across completed tests. |
| 9. Calibration emails | Future | Recompute previous estimates after calibration rounds and email opted-in users when estimates change. | Users can receive updated vocabulary estimates. |
| 10. Short adaptive test | Future | Select items using learned difficulty instead of frequency alone. | Test reaches similar confidence with fewer questions. |

## Data shape

Current question:

```json
{
  "word": "woda",
  "word_class": "noun",
  "band": "B1",
  "correct": "water",
  "wrong": ["fire", "air", "earth"]
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
  "band_coverage_met": true,
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

1. Choose a broad set of frequency bands.
2. Sample several words or lemmas from each band.
3. Use one visible item per word or lemma per session.
4. Randomize item order enough to reduce fatigue effects.
5. Reserve some claimed-known answers for meaning checks once sentence mode exists.
6. Keep extra calibration items in the test until item difficulty estimates stabilize.
7. Reduce item count gradually after each calibration round while preserving estimate confidence.
8. Let users finish early after minimum coverage once they accept the current uncertainty range.

After many responses, compute which items are easier or harder than frequency predicts. Then shift from broad long tests to shorter adaptive tests. Each calibration round should also re-estimate previous completed tests and queue email updates for opted-in users whose estimate changed.

## Open questions

- What confidence labels and range widths are honest enough for early finish?
- What minimum item count and band coverage are required before `Show results now` appears?
- Should low confidence hide early finish or allow it with a stronger warning?
- What percentage of yes answers should receive meaning checks?
- Should a failed meaning check reduce trust globally or only score that item as unknown?
- What data store will hold response events before calibration tooling exists?
- What threshold counts as a material estimate change worth emailing?
- What identity/session model should connect repeat tests into one result history?
- Which lemmatizer or source data will provide reliable Polish lemma mappings?

## GitHub Pages deployment

The HTML version is static HTML under `public/`. The existing deploy workflow runs the release build, then copies `public/*.html` into `target/release/public/` before uploading the Pages artifact.

Expected deployed HTML file: `features-to-implement.html`, relative to the Pages site root.

## Related files

- `public/features-to-implement.html`
- `public/features-to-implement.md`
- `src/jamiepratt/vocab_test_client_side/core.cljs`
- `src/jamiepratt/vocab_test_client_side/scoring.cljc`
- `resources/jamiepratt/vocab_test_client_side/questions.edn`

Last updated June 24, 2026.
