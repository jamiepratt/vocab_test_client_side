# Progressive vocabulary test methodology

Historical note: this prototype methodology predates the current
`latent-guessing-v1` scoring model. It uses "frequency band" for what the
current product calls frequency buckets and should not be treated as the current
scoring contract.

This is the rollout plan for a vocabulary-size test that starts before item calibration exists, improves from live response data, and ends as a short adaptive test.

The core bet:

1. Frequency rank is good enough to launch.
2. It is not good enough to keep forever.
3. Raw responses let us learn real item difficulty.
4. Once item difficulty is known, old tests can be re-scored and future tests can get shorter.

## What we estimate

Estimate receptive vocabulary size: how many target-language lemmas the learner probably recognizes.

Count lemmas, not displayed word forms. If the test sentence shows `spotkaliśmy`, the counted vocabulary item is the lemma `spotkać`.

Every final score should include:

- center estimate
- lower and upper range
- scoring model version
- reliability flags from checks, timing, and fake-word behavior

## Item types

Use three item roles.

| Role | Affects current score | Main purpose |
|---|---:|---|
| Scored item | yes | estimate current user ability |
| Calibration item | no, until calibrated | learn item difficulty for future tests |
| Quality item | no | catch false positives, guessing, bots, low effort |

In early versions, most real items are both provisional scoring items and calibration items. Later, only calibrated items affect the displayed score.

## Data to keep

Keep immutable raw response events. Do not only store final estimates.

```json
{
  "session_id": "sess-123",
  "user_id": "user-456",
  "item_id": "pl-spotkac-001",
  "item_version": 1,
  "language": "pl",
  "lemma": "spotkać",
  "surface": "spotkaliśmy",
  "frequency_rank": 812,
  "item_role": "scored_and_calibration",
  "response": "known",
  "meaning_check_correct": true,
  "duration_ms": 2410,
  "test_design_version": "pilot-2026-06",
  "scoring_model_version": "frequency-band-2026-06",
  "created_at": "2026-06-24T12:30:00Z"
}
```

This lets us re-score old sessions after better calibration exists.

## Stage 1: long stratified launch test

At launch, we do not know real item difficulty. We only know frequency rank.

So the first test should be longer and deliberately broad:

```text
60-80 real words
5-10 quality checks
several frequency bands
randomized order
occasional meaning checks after claimed-known answers
```

Example bands:

```text
1-500
501-2,000
2,001-5,000
5,001-10,000
10,001-20,000
20,001-50,000
```

Each test samples across the whole curve. Within each band, prefer items with the fewest clean responses.

Why not fully adaptive yet: an adaptive test keeps choosing words near the current user estimate. That is good for scoring one user, but bad for calibrating a whole item bank. It over-tests common middle items and leaves many easy, rare, or edge items with too little data.

## Stage 1 scoring

Use frequency-band scoring.

For each band:

```text
hit_rate = known_real_items / tested_real_items
```

Correct yes/no overclaiming with pseudowords:

```text
false_alarm = fake_words_claimed_known / fake_words_seen
corrected_hit_rate = (hit_rate - false_alarm) / (1 - false_alarm)
```

Clamp `corrected_hit_rate` to `[0, 1]`.

Then estimate:

```text
vocab_size = sum(band_size * corrected_hit_rate)
```

This score is provisional. It can be useful to the learner, but the uncertainty range should be wide.

## Stage 2: first calibration round

After enough clean response data, estimate real item difficulty.

Use a Rasch model:

```text
P(response_i = 1 | theta_user, b_item) = logistic(theta_user - b_item)
```

Where:

- `theta_user` = learner ability
- `b_item` = item difficulty
- `response_i` = binary known/unknown outcome

Binary scoring rule:

- known and no check required: `1`
- known and meaning check correct: `1`
- no, not sure, skipped: `0`
- known but meaning check failed: `0`
- fake words: excluded from ability model, used for quality filtering

Filter bad sessions before calibration:

- high fake-word false-positive rate
- very fast answers
- many failed meaning checks
- incomplete sessions
- repeated suspicious patterns

Filter bad items after first fit:

- too few clean responses
- poor item-total correlation
- poor Rasch fit, e.g. infit/outfit far outside `0.7-1.3`
- broken distractors
- ambiguous sentence or lemma
- behavior wildly inconsistent with nearby ranks and no good reason

Then refit and publish a calibrated item-bank version.

## Video explainers

Useful background videos:

- [What is Item Response Theory?](https://www.youtube.com/watch?v=P8huS6PPxJA) - quick conceptual intro before the Rasch-specific parts.
- [What is the Rasch Model?](https://www.youtube.com/watch?v=Qsk8PaDS9oM) - direct explanation of the calibration model used here.
- [Understanding Item Response Theory: Key Concepts & Applications](https://www.youtube.com/watch?v=B-oLR7XRVCU) - longer seminar-style overview.
- [Better Measurement with Item Response Theory](https://www.youtube.com/watch?v=HoMVasu2tg8) - deeper lecture with more implementation context.

## Stage 3: re-score old sessions

Every calibration round should revise old estimates.

Do not overwrite old scores. Add a new score record:

```json
{
  "session_id": "sess-123",
  "old_scoring_model": "frequency-band-2026-06",
  "new_scoring_model": "rasch-2026-09",
  "estimate": {
    "center": 4650,
    "lower": 3900,
    "upper": 5500
  },
  "created_at": "2026-09-15T09:00:00Z"
}
```

Re-scoring uses the old raw responses with the new item difficulties:

```text
posterior(theta) proportional to
prior(theta) * product(P(response_i | theta, b_item))
```

Then convert `theta` to vocabulary size.

## Stage 4: hybrid production test

Once enough items are calibrated, switch to a hybrid test.

```text
25-35 calibrated scored items
5-15 calibration items
3-6 quality checks
```

The calibrated scored items produce the user-visible score.

The calibration tail improves future versions and should not affect the current displayed score until those items are calibrated.

Calibration-tail selection should prioritize:

- low response count
- high difficulty uncertainty
- weak coverage in a rank band
- items suspected of bad fit

Keep the tail tolerable:

```text
40% near estimated level
30% easier
30% harder
```

This keeps user experience sane while still improving the bank.

## Stage 5: mostly adaptive test

When the bank is stable, reduce the calibration tail.

```text
25-35 adaptive scored items
2-5 calibration items
2-4 quality checks
```

Adaptive loop:

```text
start with prior over theta
repeat:
  choose unused calibrated item with high information near theta
  ask item
  score response as 0 or 1
  update posterior over theta
  stop when item limit hit or confidence interval is narrow enough
return vocabulary estimate, range, reliability flags
```

Keep a small calibration trickle forever. User mix, item behavior, and content quality drift.

## Ability to word count

After calibration, each item has both:

```text
frequency_rank
rasch_difficulty
```

Fit a monotone curve:

```text
difficulty = f(log(rank))
```

Then estimate vocabulary size by expected known words:

```text
vocab_size(theta) = sum over ranks r of logistic(theta - f(log(r)))
```

This is better than a hard cutoff like "knows every word up to rank X". Real learner knowledge is patchy.

## Sample-size targets

Size by clean responses per item.

```text
users_needed = real_items * target_responses_per_item / real_items_per_user
```

Example with 250 real items and 30 real answers per test:

| Calibration level | Clean responses/item | Users |
|---|---:|---:|
| rough pilot | 100 | about 834 |
| useful product calibration | 250-400 | about 2,100-3,400 |
| strong public scoring | 500-1,000 | about 4,200-8,400 |

Practical targets:

- 500-1,000 users: first pilot read
- 2,000-3,000 users: useful calibrated product
- 5,000+ users: strong public scoring

## When to reduce test length

Reduce heavy calibration only when most production items have:

- `250-400+` clean responses
- acceptable difficulty standard error, e.g. `< 0.25 logits`
- acceptable fit, e.g. infit/outfit around `0.7-1.3`
- stable estimates under resampling
- user-facing confidence interval narrow enough for the product promise

## Final rollout

| Stage | Test shape | Score shown |
|---|---|---|
| 1. Launch | long stratified | provisional frequency-band estimate |
| 2. First calibration | long or semi-balanced | first Rasch model, bad items removed |
| 3. Re-score | old raw sessions | revised calibrated estimates |
| 4. Hybrid | adaptive core plus calibration tail | calibrated estimate |
| 5. Mature | mostly adaptive plus small trickle | short CAT estimate with CI |

Last updated June 24, 2026.
