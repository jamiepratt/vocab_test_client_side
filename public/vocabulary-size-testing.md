# Current Vocabulary Size Testing

## Summary

The current test estimates passive Polish vocabulary with sentence-context lemma
items. Learners see one Polish sentence, one highlighted target form, and answer
choices for the target lemma's English meaning in that context.

Learners are the first audience: the test should feel clear, low-pressure, and
useful for placement or progress. Teachers can use the same output, but teacher
interpretation should not change the learner-facing instructions.

## Glossary

- Surface-rank window: the exact 80 surface-form difficulty ranks served for one
  question block.
- Frequency bucket: a broad reporting bucket derived from each item's
  `surface-difficulty-rank`.
- Lemma-inventory stratum: a 1,000-lemma scoring bin derived from
  `lemma_inventory_rank`.
- Approximate level: the coarse result label derived from the recognized-lemma
  estimate.

## What Is Measured

The score estimates recognized Polish lemmas, not productive vocabulary and not
surface forms. A learner gets vocabulary evidence when they recognize the target
lemma meaning inside the sentence.

Each real item stores:

- target lemma id;
- target surface form id;
- lemma inventory rank;
- surface-form difficulty rank;
- inventory stratum, with `fixed-stratum` kept as a compatibility alias;
- item type;
- choice count and random-choice hit rate;
- selected answer and correctness.

## Starting Level

The learner chooses the level that feels closest before starting. This is only a
starting prior for the first block.

Current user-facing options:

| Option | Initial role |
| --- | --- |
| Absolute beginner / pre-A1 | route to the easiest measured block |
| A1 | begin around early learner vocabulary |
| A2 | begin around lower-intermediate vocabulary |
| B1 | begin around intermediate vocabulary |
| B2 | begin around upper-intermediate vocabulary |
| C1 | begin around advanced vocabulary |
| C2 | begin around high advanced vocabulary |

There is no CEFR `A3`. `Absolute beginner / pre-A1` is a product label, not a
formal CEFR level.

## Test Blocks

Each scored block has 80 real sentence-context lemma items. Optional control
items may exist later, but they are not counted as real vocabulary evidence.

The current item format:

1. Show one Polish sentence.
2. Highlight one target form.
3. Ask for the best English meaning.
4. Include a `don't know` option.
5. Tell the learner not to guess.

User-facing instruction:

> Choose the best English meaning when you know it. Use `don't know` when
> unsure; guessing makes the estimate less accurate.

## Easier Or Harder Continuation

The first block may not be informative enough. After a completed 80-item block:

| Correct rate | Interpretation | Action |
| ---: | --- | --- |
| 0-15% | block too hard | continue lower, unless this is the floor block |
| 15-85% | block informative | report from current evidence |
| 85-100% | block too easy | continue higher, if a harder block exists |

Previous answers are not discarded. If the session continues, the final result
uses all scored real items from every block.

Floor wording for the lowest block should be non-shaming:

> This result is below the range this block can measure precisely, so we report
> a broad beginner range.

## Surface Difficulty And Lemma Reporting

Blocks are selected by surface-form difficulty spans because the exact displayed
form affects item difficulty. Results are reported over the linked lemma
inventory because the product promise is vocabulary size in lemmas.

The implementation keeps both ranks:

- `surface_form_difficulty_rank` controls item ordering and block selection;
- `lemma_inventory_rank` controls the vocabulary-size denominator and scoring
  stratum.

## Current Served Windows

The API serves 80-rank windows. For `block=0`, current starting-level windows
are:

| Starting level | Served surface-rank window |
| --- | ---: |
| Absolute beginner / pre-A1 | 1-80 |
| A1 | 401-480 |
| A2 | 1,001-1,080 |
| B1 | 2,001-2,080 |
| B2 | 4,001-4,080 |
| C1 | 8,001-8,080 |
| C2 | 8,001-8,080 |

Continuation requests add another 80-rank window for the same starting prior.

## Nominal Starting-Level Coverage

These broader spans are product coverage labels for the adaptive blocks, not the
exact window served by a single API request:

| Block | Surface-form difficulty span |
| --- | ---: |
| Absolute beginner / pre-A1 | 1-500 |
| Pre-A1 stretch | 250-1,000 |
| A1 | 1-2,000 |
| A2 | 500-3,000 |
| B1 | 1,000-5,000 |
| B2 | 2,000-8,000 |
| C1 | 3,000-10,000 |
| C2 | 5,000-10,000 |
| C2 stretch | 8,000-15,000 |

Adjacent blocks overlap so answers near a boundary remain useful.

## Static Intro Page

The in-app learner introduction lives at `#/current/testing`. It presents the
same testing behavior with `Quick`, `Guide`, and `Detail` expansion presets plus
static UI fragments for the level selector and a sentence question card.
