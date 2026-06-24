# Vocabulary test features to implement

Feature plan for a sentence-based vocabulary size test: yes/no recognition, selective meaning checks, lemma-based scoring, calibration emails, and a data path from an 80-item launch test to shorter calibrated tests.

Tags: Static HTML, GitHub Pages ready, Sentence context, Lemma scoring, Calibration

## Goal

Build a receptive vocabulary test that estimates how many dictionary-form words a learner probably knows. The user sees full sentences, one highlighted word form, and answers whether they know that word in context.

Main product bet: start with frequency as the difficulty proxy, ask 80 items at launch to average out item-specific difficulty and gather calibration data, then use collected responses to learn real item difficulty and shorten future tests.

## User experience

Example question:

> Wczoraj **spotkaliśmy** dawnego kolegę przed teatrem.

Recognition choices:

- Yes, I know it.
- No / not sure.

Occasional verification:

> If selected for verification, ask what the highlighted word means.

Multiple-choice options:

- We met.
- We waited.
- We carried.
- We watched.

Requirements:

- Show a complete sentence, not an isolated word.
- Highlight exactly one tested word form inside the sentence.
- The shown word may be inflected or otherwise not in dictionary form.
- Primary response: simple recognition, yes or no.
- For a sampled subset of yes answers, ask a multiple-choice meaning check.
- Use visible progress, but avoid revealing exact scoring logic.
- Ask for an email address before or after the result if the user wants calibration updates.
- When calibration changes their estimate, email users who already completed the test with the updated vocabulary estimate.
- When a returning user has multiple completed tests, show a vocabulary-over-time graph with a center estimate and upper and lower bounds.

## Scoring model

Score vocabulary size by dictionary forms, not every observed surface form. If the sentence tests `spotkaliśmy`, the vocabulary item counted is the lemma `spotkać`.

| Concept | Stored value | Why it matters |
|---|---|---|
| Surface form | `spotkaliśmy` | User-visible tested token in the sentence. |
| Lemma | `spotkać` | Vocabulary item counted in final estimate. |
| Frequency rank | `rank = 812` | Initial difficulty proxy before calibration. |
| Observed difficulty | `b_i` | Learned later from response data. |

A yes answer counts as known only when no verification is requested, or when the meaning check is answered correctly. Wrong meaning checks convert the item to unknown and contribute to a reliability flag.

## Feature list

### Sentence item bank

- Store sentence text, tested token offsets, surface form, lemma, translation, frequency rank, and distractors.
- Support multiple sentence examples per lemma.
- Exclude ambiguous, proper-name, offensive, or unnatural examples.

### Recognition question

- Render sentence with one highlighted token.
- Capture yes, no, not sure, skipped, and response time.
- Treat no, not sure, and skipped as unknown for ability scoring.

### Meaning check

- Trigger after a configurable percentage of yes answers.
- Use one correct option plus plausible distractors.
- Record whether the check confirms or contradicts self-report.

### Initial long test

- Sample broadly across frequency bands.
- Start with 80 test items: enough for a first estimate plus extra calibration coverage.
- Include temporary extra items while the test is not fully calibrated.
- Gradually reduce item count as observed item difficulty becomes reliable.
- Average out cases where frequency differs from real learner difficulty.

### Vocabulary estimate

- Estimate known lemmas, not known word forms.
- Show a range, not just a single number.
- Attach a confidence or reliability label from checks and timing.

### Calibration data

- Collect enough responses per lemma to learn real difficulty.
- Compare observed difficulty against frequency rank.
- Use learned difficulty to reduce item count over time.
- Track calibration version so older completed tests can be re-estimated.
- Queue updated estimates for users who opted into email updates.

### Result history

- Store each completed estimate with timestamp, calibration version, center estimate, lower bound, and upper bound.
- For repeat test-takers, chart vocabulary size over time.
- Show the center line plus upper and lower uncertainty bounds.

### Calibration emails

- Collect explicit opt-in before sending follow-up estimate emails.
- Email users when a calibration round materially changes their vocabulary estimate.
- Include the new estimate, range, calibration date, and a link back to their result history.

## Implementation phases

| Phase | Build | Done when |
|---|---|---|
| 1. Static prototype | Sentence card, highlighted token, yes/no response, optional multiple-choice check. | Browser flow works end to end with local sample items. |
| 2. Item bank | Data schema for sentence, surface form, lemma, rank, answer, distractors. | Questions can be generated from structured data, not hardcoded UI text. |
| 3. Initial scoring | Frequency-band sampling and lemma-based vocabulary estimate. | Result page reports estimate, range, and reliability warning. |
| 4. Telemetry export | Persist anonymized response events for calibration. | Each event has item id, lemma id, response, check result, timing, and calibration version. |
| 5. Result history | Store repeat results and render a vocabulary-over-time graph with center, lower, and upper estimate lines. | Returning users can see estimate movement across completed tests. |
| 6. Calibration | Fit observed difficulty per item and compare to frequency rank. | System can identify items easier or harder than frequency predicts. |
| 7. Calibration emails | Recompute previous estimates after calibration rounds and email opted-in users when estimates change. | Users who already completed the test can receive updated vocabulary estimates. |
| 8. Short adaptive test | Select items using learned difficulty instead of frequency alone. | Test reaches similar confidence with fewer questions. |

## Data shape

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

```json
{
  "result_id": "result-123",
  "user_id": "user-456",
  "completed_at": "2026-06-24T12:30:00Z",
  "calibration_version": "2026-06-launch",
  "item_count": 80,
  "estimate": {
    "center": 4200,
    "lower": 3600,
    "upper": 4900
  },
  "email_opt_in": true,
  "email": "learner@example.com"
}
```

## Initial sampling rule

Before calibration, assume word difficulty equals frequency difficulty. Compensate for that rough assumption by starting with 80 test items, including extra calibration items, and sampling across the frequency curve.

1. Choose a broad set of frequency bands.
2. Sample several lemmas from each band.
3. Use one sentence item per lemma per session.
4. Randomize item order enough to reduce fatigue effects.
5. Reserve some yes answers for meaning checks.
6. Keep extra calibration items in the test until item difficulty estimates stabilize.
7. Reduce item count gradually after each calibration round while preserving estimate confidence.

After many responses, compute which lemmas are easier or harder than frequency predicts. Then shift from broad long tests to shorter adaptive tests. Each calibration round should also re-estimate previous completed tests and queue email updates for opted-in users whose estimate changed.

## Open questions

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

Last updated June 24, 2026.
