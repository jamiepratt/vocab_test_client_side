# Polish Vocabulary Size Testing Methodology

## Summary

Estimate recognised Polish lemma vocabulary from sentence-context test items over a
10,000-lemma inventory, including absolute beginners who may recognise fewer
than 500 lemmas in context.

Use CEFR self-selection only as a starting prior. The test should adapt by moving
to easier or harder lemma bands when the user's answers show that the selected
band is outside their measurable range.

Do not promise exact precision such as "within 50 lemmas" for short tests. An
80-item block supports a useful banded estimate, not a fine-grained census.

## Vocabulary Scale

Use recognised lemmas in sentence context, not surface forms and not productive
vocabulary.

Use two separate ranks:

- `lemma_inventory_rank`: reporting rank over the 10,000 selected lemmas. Compute
  it from link-apportioned linear SUBTLEX `freq.sn.sum` across linked lexical
  surface forms, not from summed Zipf/log values and not from assigning a full
  ambiguous surface count to every linked lemma.
- `surface_form_difficulty_rank`: item difficulty proxy for the exact highlighted
  form in the sentence. Use that surface form's own `avg.zipf.freq.sn` or rank
  until calibrated item difficulty data exists.

Use ranked `(surface_form_id, lemma_id)` candidate pairs for item presentation.
Surface-form frequency controls item order/difficulty; lemma inventory rank
controls the vocabulary-size denominator and reported estimate. Do not reorder
the lemma inventory itself by surface forms.

Initial CEFR-to-lemma bands:

| Level | Estimated known lemmas |
| --- | ---: |
| Absolute beginner / pre-A1 | 0-500 |
| A1 | 500-1,000 |
| A2 | 1,000-2,000 |
| B1 | 2,000-3,000 |
| B2 | 3,000-5,000 |
| C1 | 5,000-8,000 |
| C2 | 8,000-10,000+ |

These ranges are rough onboarding labels, not formal CEFR definitions. CEFR is a
proficiency framework, not a lemma-count standard.

There is no CEFR `A3`.

Use `absolute beginner` or `pre-A1` for users below the A1 vocabulary range. Do
not present this as a CEFR level; it is a product label for test routing and
results.

## Test Shape

Start with the level the user selects.

Each test block contains 80 scored real-lemma items, plus optional control items.
Each real item presents:

- one Polish sentence;
- one highlighted target form;
- answer choices or a known/unknown response;
- scoring against the target lemma's meaning in that sentence context.

Prefer multiple-choice or validated known/unknown responses over unsupported
self-report. Add pseudowords or impossible distractors only as attention checks;
do not count them as real vocabulary evidence.

Tell users not to guess. The scoring model should initially trust that
instruction, then infer guessing behaviour from incorrect selected answers.

## Progressive Band Testing

The first block should cover the selected level plus nearby lower and upper
difficulty.

If the user is outside that block's measurable range, continue immediately with a
new block:

- too many correct: continue with harder items;
- too many incorrect: continue with easier items;
- middle range: stop and report an estimate.

Do not discard previous answers. Combine all answered real items into the final
estimate.

Do not give vocabulary credit for untested lower strata just because the user
started at a high selected level. If a high-start block is not a high-confidence
pass and lower strata have no evidence, add a lower anchor block when available
instead of reporting a prior-filled estimate.

User-facing wording:

> Your result is above the range this block was designed to measure. Continue
> with harder items for a better estimate.

or:

> Your result is below the range this block was designed to measure. Continue
> with easier items for a better estimate.

## Block Decision Rules

For an 80-item block:

| Correct rate | Interpretation | Action |
| ---: | --- | --- |
| 0-15% | block too hard | continue lower |
| 15-85% | block informative | estimate from current evidence |
| 85-100% | block too easy | continue higher |

Use the thresholds as product rules, not psychometric truth. They prevent false
precision at the edges, where an 80-item block cannot distinguish well.

For high-start sessions, `85-100%` correct may also mark lower untested strata as
assumed known for reporting. Scores below that threshold should not receive
prior-only credit for lower strata; continue with a lower anchor block if the
current evidence would otherwise leave the lower range unmeasured.

For the lowest beginner block, `0-15%` correct should not create an impossible
lower block. Report a floor-range estimate such as `0-100`, `0-150`, or
`under 200` depending on the item mix and model confidence.

## Suggested Bands

Use overlapping surface-form difficulty spans so adjacent tests share evidence
around boundaries. These spans control presented item difficulty; the reported
result is still estimated over linked lemma inventory strata.

| User selects | Initial item difficulty span | Width |
| --- | ---: | ---: |
| Absolute beginner / pre-A1 | 0-500 | 500 |
| A1 | 0-2,000 | 2,000 |
| A2 | 500-3,000 | 2,500 |
| B1 | 1,000-5,000 | 4,000 |
| B2 | 2,000-8,000 | 6,000 |
| C1 | 3,000-10,000 | 7,000 |
| C2 | 5,000-10,000 | 5,000 |

If a user exits the selected difficulty span, the next block should shift in the
required direction and overlap the previous block. Example:

1. User selects B1.
2. First block samples surface-form difficulty ranks 1,000-5,000.
3. User scores 74/80.
4. Continue with harder surface-form items, e.g. 3,000-8,000.
5. Combine both blocks for the final estimate.

Absolute beginner routing:

1. User selects absolute beginner/pre-A1, or selects A1 and scores below the
   informative range.
2. Test the `0-500` surface-form difficulty span with very high-frequency forms
   in sentence context.
3. If the user scores 0-15%, stop at the floor and report a broad estimate below
   the measured range.
4. If the user scores 85-100%, continue upward with an overlapping block such as
   `250-1,000`.

## Accuracy Expectations

With 80 randomly sampled items over the full 10,000-lemma inventory, worst-case
95% error is roughly +/-1,100 lemmas.

Within a bounded band, 80 items give roughly:

- 95% error: about +/-11% of the tested band width;
- 1-sigma error: about +/-5.6% of the tested band width.

Approximate 95% error by initial band:

| User selects | Band width | Approx. 95% error |
| --- | ---: | ---: |
| Absolute beginner / pre-A1 | 500 | +/-55 |
| A1 | 2,000 | +/-220 |
| A2 | 2,500 | +/-275 |
| B1 | 4,000 | +/-440 |
| B2 | 6,000 | +/-660 |
| C1 | 7,000 | +/-770 |
| C2 | 5,000 | +/-550 |

These are best-case sampling numbers. Real error will be larger when items are
ambiguous, distractors are weak, sentence context gives away the answer, or lemma
difficulty is poorly calibrated.

## Progressive Uncertainty Display

Yes: after enough scored real items, the test can show the current estimate and
how wide the uncertainty interval is. This lets users stop once the estimate is
good enough for their purpose.

Start showing the live range after a minimum number of real items, such as 20 or
30. Before that, use wording like `Still calibrating` rather than showing a
misleadingly jumpy interval.

For a uniformly sampled block, a simple v1 estimate is:

- `known_in_band = correct_rate * band_width`;
- `vocab_estimate = band_start + known_in_band`;
- `approx_95_half_width = 1.96 * band_width * sqrt(p * (1 - p) / n)`.

The worst-case half-width occurs around `p = 0.5`, so a useful planning shortcut
is:

`approx_95_half_width ~= 0.98 * band_width / sqrt(n)`

Example over the `0-500` absolute-beginner band:

| Scored items | Worst-case approx. 95% half-width |
| ---: | ---: |
| 25 | +/-98 lemmas |
| 50 | +/-69 lemmas |
| 80 | +/-55 lemmas |

For stratified blocks, calculate the estimate per bin and add them:

- estimate each bin as `bin_correct_rate * bin_width`;
- sum bin estimates to get `known_in_band`;
- combine bin variances, weighted by bin width.

Use a Bayesian posterior interval for the live range. This is an anytime
estimate: users may look after every item and stop when the range is narrow
enough, without invalidating the posterior update. Do not call it a frequentist
confidence interval. In product UI, prefer `credible range`, `likely range`, or
`uncertainty range`.

Define persistent lemma-inventory rank strata for the whole inventory, such as
250- or 500-lemma bins. Every scored item belongs to exactly one stratum. Blocks
may overlap, but the statistical model should update these fixed strata, not
temporary block boundaries.

For each stratum `s`:

- `W_s` = stratum width in lemmas;
- `theta_s` = proportion of lemmas in that stratum the user recognises in
  context;
- prior: `theta_s ~ Beta(0.5, 0.5)`.

For known/unknown or otherwise validated binary recognition items:

- after `x_s` recognised out of `n_s` scored real items;
- posterior: `theta_s ~ Beta(0.5 + x_s, 0.5 + n_s - x_s)`.

For forced-choice multiple-choice items, model guessing as a hidden session
behaviour, not as a fixed assumption that every unknown answer is guessed.

Let:

- `q` = probability the user guesses when they do not know, instead of choosing
  `I don't know`;
- prior: `q ~ Beta(0.5, 8.0)`, a strong no-guess prior;
- `r_i = 1 / k_i`, the random-choice hit rate for item `i`;
- `theta_s` = known proportion for the item's lemma-inventory stratum.

The response likelihood for an item in stratum `s` is:

- `P(correct_i | theta_s, q) = theta_s + (1 - theta_s) * q * r_i`;
- `P(wrong_i | theta_s, q) = (1 - theta_s) * q * (1 - r_i)`;
- `P(dk_i | theta_s, q) = (1 - theta_s) * (1 - q)`.

This keeps early correct answers close to known evidence when the user follows
instructions. Incorrect selected answers raise posterior mass for `q`, which
discounts correct multiple-choice answers as possible lucky guesses and widens
the estimate range.

This is not conjugate beta. Implement it with deterministic grids or quadrature:

- retain a theta grid per stratum;
- add a session-level `q` grid;
- multiply item likelihoods in log space;
- normalise with log-sum-exp;
- report the posterior for vocabulary size and for `q`.

To produce the live vocabulary-size interval:

1. Combine the joint posterior over `q` and observed stratum `theta_s` values.
2. Compute `known_lemmas = sum(W_s * theta_s_sample)` across reported strata.
3. Add lower unobserved strata only when they are explicitly marked assumed known
   by a high-confidence pass.
4. Use the posterior median or mean as the point estimate.
5. Use the 2.5th and 97.5th percentiles as the 95% credible range.

Use a fixed random seed or deterministic quantile approximation so the displayed
range does not jitter between page renders.

For untested strata inside the reported range, avoid prior-only vocabulary
credit. Either gather evidence with another block, mark lower strata as assumed
known after a high-confidence pass, or omit those strata from the estimate and
make the range visibly broad.

This matters because users may stop when the displayed range looks good. Repeated
peeking makes ordinary fixed-sample confidence intervals overconfident; the
Bayesian posterior is the planned v1 solution.

Recommended UI:

> Current estimate: about 420 recognised lemmas
> Current likely range: 320-520
> Answer more items to narrow the range, or stop now.

Do not show more precision than the current interval supports. If the current
range is `320-520`, do not foreground a point estimate like `421`.

## Reporting

Report estimates as ranges.

Good:

> Estimated recognised Polish lemmas: about 2,700
> Likely range: 2,250-3,150
> Level band: high B1 / low B2

Avoid:

> You know exactly 2,714 words.

If the uncertainty interval crosses a level boundary, say `borderline` instead of
assigning a single exact level.

For very low estimates, use non-shaming language and avoid implying failure.

Example:

> Estimated recognised Polish lemmas: under 200
> Likely range: 0-200
> Level band: absolute beginner / pre-A1

## Sampling Strategy

For a non-adaptive v1, stratify each block by surface-form difficulty first:

- build the pool from ranked `(surface_form_id, lemma_id)` candidate pairs;
- split the target surface-form difficulty span into 8 bins;
- sample 10 candidate pairs per bin;
- avoid repeated lemmas;
- if a candidate surface form is too ambiguous, unnatural in a short sentence, or
  gives away the answer, use the next suitable candidate from the same
  surface-form difficulty bin;
- avoid sentences where context makes the answer obvious without knowing the
  target lemma;
- record the linked lemma inventory rank and lemma stratum for every item so the
  result can still be estimated over lemmas, not surface forms;
- balance part of speech where possible.

Because surface-form sampling overrepresents lemmas with many forms, do not treat
surface-form-bin correct rates as a direct lemma vocabulary estimate. For v1,
enforce unique target lemmas per block and update the fixed lemma-inventory strata
using the linked `lemma_id`. If the sampled items do not cover enough lemma
inventory strata for a stable estimate, continue with another block or report a
wider uncertainty range.

For later versions, use calibrated item difficulty and choose items adaptively
around the current estimate.

For the `0-500` surface-form difficulty block, avoid relying only on the
absolute most frequent function words. Include common concrete nouns, basic
verbs, adjectives, numbers, pronouns, question words, and everyday adverbs so
the test measures usable recognition, not just memorised grammar particles.

## Result Combination

Every scored answer should be retained with:

- user/session id;
- test block id;
- target lemma id;
- target surface form id;
- surface-form/lemma candidate rank;
- inventory rank stratum id;
- lemma inventory rank;
- surface-form difficulty rank or calibrated item difficulty;
- item type and number of choices;
- random-choice hit rate for the item;
- selected answer;
- correctness;
- response time;
- attention-check status.

The final estimate should use all scored real items from the session, including
items from blocks that were later judged too easy or too hard.

The scoring output should retain model metadata: scoring model version,
posterior vocabulary centre/range, reported strata and whether each was observed
or assumed known, plus the posterior centre/range for session guessing `q`.

## Precision Limits

Within 50 lemmas over a 10,000-lemma inventory means +/-0.5 percentage points.
At 95% confidence, that requires thousands of randomly sampled items in the
worst case.

Short tests should therefore be positioned as vocabulary-size estimates with
uncertainty bands, not exact measurements.

## Implementation Notes

- Keep CEFR labels user-facing and approximate.
- Offer an explicit absolute-beginner/pre-A1 entry path.
- Keep internal scoring lemma-based.
- Rank lemma inventory with link-apportioned linear `freq.sn.sum`; present items
  by exact surface-form frequency/rank.
- Use a latent session-level guessing posterior with a strong no-guess prior.
- Use Bayesian posterior credible ranges for live progress and final estimates.
- Do not award lower-stratum prior credit after high-start selection unless the
  user has a high-confidence pass or a lower anchor block supplies evidence.
- Treat each answer as evidence, even when a follow-up block is needed.
- Prefer continuing the current test over asking users to restart.
- Make uncertainty visible in the result UI.
