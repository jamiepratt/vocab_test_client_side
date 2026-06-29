# Current Vocabulary Size Scoring

## Summary

Current scoring model: `latent-guessing-v1`.

The score estimates how many Polish lemmas a learner recognizes in sentence
context. It reports a point estimate plus a likely range. It should not imply an
exact vocabulary count.

## Inventory

The reporting inventory contains 10,000 Polish lemmas. It is divided into
1,000-lemma fixed strata:

| Stratum | Lemma ranks |
| ---: | --- |
| 1 | 0-1,000 |
| 2 | 1,000-2,000 |
| 3 | 2,000-3,000 |
| 4 | 3,000-4,000 |
| 5 | 4,000-5,000 |
| 6 | 5,000-6,000 |
| 7 | 6,000-7,000 |
| 8 | 7,000-8,000 |
| 9 | 8,000-9,000 |
| 10 | 9,000-10,000 |

Every scored real item belongs to one fixed lemma-inventory stratum.

## Live Estimate

The live estimate appears after 30 scored real answers. Before that point, the
app says there is not enough evidence yet.

Current live wording:

> Current estimate: about N recognized Polish lemmas
> Likely range: lower-upper

The range should stay prominent. If the interval is wide, the UI should not
foreground false precision.

## Latent Guessing

Multiple-choice answers can be correct by luck. The model uses a session-level
latent guessing parameter:

- the prior assumes the learner usually follows the no-guess instruction;
- incorrect selected answers increase posterior guessing probability;
- higher guessing probability discounts lucky correct answers and widens the
  likely range;
- `don't know` answers count as unknown without increasing guessing evidence.

Item likelihood:

- `P(correct) = theta + (1 - theta) * q * r`
- `P(wrong) = (1 - theta) * q * (1 - r)`
- `P(dk) = (1 - theta) * (1 - q)`

Where `theta` is known proportion in the item's stratum, `q` is session guessing
probability, and `r` is the random-choice hit rate.

## Likely Ranges

The model reports posterior likely ranges rather than frequentist confidence
intervals. Product copy should use `likely range`, `credible range`, or
`uncertainty range`.

Final result copy:

> Estimated recognized Polish lemmas: about N
> Likely range: lower-upper
> Approximate level band: label

Avoid:

> You know exactly N words.

## Floor Results

For the lowest beginner block, a very low correct rate cannot route lower. The
current floor result reports:

- estimate label: `under 200`;
- likely range: `0-200`;
- level band: `Absolute beginner / pre-A1`.

This should be framed as a broad starting estimate, not a failure.

## Lower-Strata Assumptions

Do not give vocabulary credit for untested lower strata just because the learner
started at a high level.

Lower strata may be marked assumed known only when the evidence includes a
high-confidence pass. Otherwise the app should either gather a lower anchor
block or report from observed strata with a visibly broad range.

## Static Intro Page

The in-app scoring introduction lives at `#/current/scoring`. It presents the
same scoring behavior with `Quick`, `Guide`, and `Detail` expansion presets plus
static examples for the live estimate panel and final result card.
