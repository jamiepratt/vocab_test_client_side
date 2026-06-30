# Validation

Bundle: `handoff/polish-lexicon-import-v2`
Source git commit: `908004b89602fb107120bea5741693b6185fdd60`
Generated: `2026-06-30T11:42:53+00:00`
Selection scope: `example-referenced-lemmas-with-connected-surfaces`

## Selection Summary

- Example rows: 1,830
- Referenced lemma IDs: 1,830
- Direct example surface IDs: 1,753
- Connected retained surface IDs: 26,687
- Rejected/audit tables: header-only

## Command Results

The handoff builder performed deterministic TSV scoping, example target/source consistency checks when supplied, manifest hash/count generation, and direct missing-reference checks. It does not run destructive database operations.

### `python3 -m unittest discover -s tests`

```text
Ran 17 tests in 0.049s
OK
```

### `python3 scripts/polish_lexicon_import.py validate-examples --examples curated/example_sentences.tsv --example-targets curated/example_targets.tsv --strict-item-bank`

```text
curated/example_sentences.tsv: 1830 example rows valid
curated/example_targets.tsv: 1830 target rows valid
```

### `python3 scripts/polish_lexicon_import.py validate-examples --import-dir handoff/polish-lexicon-import-v2/tsv --examples handoff/polish-lexicon-import-v2/tsv/example_sentences.tsv --example-targets curated/example_targets.tsv --strict-item-bank`

```text
handoff/polish-lexicon-import-v2/tsv/example_sentences.tsv: 1830 example rows valid
curated/example_targets.tsv: 1830 target rows valid
```

### `manifest hash/count verification for every handoff TSV and support file`

```text
manifest written: 18 TSVs, 4 support files
independent sha256/header/row-count check: 0 errors
```

### `direct check for missing example lemma/surface refs`

```text
example rows: 1830
lemma rows: 1830
surface rows: 26687
missing example lemma refs: 0
missing example surface refs: 0
```

### `temporary PostgreSQL load validation from handoff root`

```text
database: subtlex_v2_validation
schema.sql: OK
load.sql: OK
example rows: 1830
missing example lemma refs: 0
missing example surface refs: 0
duplicate/answer distractor rows: 0
database dropped after validation: OK
```

## Final Row Counts

- `tsv/lemmas.tsv` (`lemmas`): 1,830 rows
- `tsv/lemma_subtlex_pos.tsv` (`lemma_subtlex_pos`): 1,896 rows
- `tsv/surface_forms.tsv` (`surface_forms`): 26,687 rows
- `tsv/surface_subtlex_metrics.tsv` (`surface_subtlex_metrics`): 26,687 rows
- `tsv/surface_nkjp_metrics.tsv` (`surface_nkjp_metrics`): 26,687 rows
- `tsv/surface_form_lemma_links.tsv` (`surface_form_lemma_links`): 27,219 rows
- `tsv/surface_form_lemma_frequency_ranks.tsv` (`surface_form_lemma_frequency_ranks`): 27,169 rows
- `tsv/freedict_entries.tsv` (`freedict_entries`): 1,944 rows
- `tsv/freedict_entry_pos.tsv` (`freedict_entry_pos`): 1,941 rows
- `tsv/freedict_pronunciations.tsv` (`freedict_pronunciations`): 1,884 rows
- `tsv/freedict_senses.tsv` (`freedict_senses`): 3,265 rows
- `tsv/freedict_sense_translations.tsv` (`freedict_sense_translations`): 5,605 rows
- `tsv/freedict_sense_definitions.tsv` (`freedict_sense_definitions`): 3,827 rows
- `tsv/example_sentences.tsv` (`example_sentences`): 1,830 rows
- `tsv/rejected_lemmas.tsv` (`rejected_lemmas`): 0 rows
- `tsv/rejected_surfaces.tsv` (`rejected_surfaces`): 0 rows
- `tsv/rejected_freedict_rows.tsv` (`rejected_freedict_rows`): 0 rows
- `tsv/nkjp_build_stats.tsv` (`nkjp_build_stats`): 8 rows
