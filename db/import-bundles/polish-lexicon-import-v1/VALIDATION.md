# Validation

Bundle: `handoff/polish-lexicon-import-v1`
Source git commit: `b1e3ea9215f75a8aaca5667656a1c03b3cb027a0`
Generated: `2026-06-26T13:59:02+00:00`
Selection scope: `example-referenced-lemmas-with-connected-surfaces`

## Selection Summary

- Example rows: 1,830
- Referenced lemma IDs: 1,830
- Direct example surface IDs: 1,753
- Connected retained surface IDs: 26,687
- Rejected/audit tables: header-only

## Command Outputs

### `python3 -m unittest discover -s tests`

```text
............
----------------------------------------------------------------------
Ran 12 tests in 0.014s

OK
```

### Scoped handoff generation

```text
wrote /Users/jamiep/Documents/subtlex/handoff/polish-lexicon-import-v1
example rows: 1830
lemma rows: 1830
surface rows: 26687
link rows: 27219
rank rows: 27169
```

### `python3 scripts/polish_lexicon_import.py validate-examples --examples curated/example_sentences.tsv --example-targets curated/example_targets.tsv --strict-item-bank`

```text
curated/example_sentences.tsv: 1830 example rows valid
curated/example_targets.tsv: 1830 target rows valid
```

### `python3 scripts/polish_lexicon_import.py validate-examples --import-dir handoff/polish-lexicon-import-v1/tsv --examples handoff/polish-lexicon-import-v1/tsv/example_sentences.tsv --example-targets curated/example_targets.tsv --strict-item-bank`

```text
handoff/polish-lexicon-import-v1/tsv/example_sentences.tsv: 1830 example rows valid
curated/example_targets.tsv: 1830 target rows valid
```

### Manifest hash/count verification

```text
manifest verified: 18 TSVs, 4 support files
selection: {'example_rows': 1830, 'referenced_lemma_ids': 1830, 'direct_example_surface_ids': 1753, 'connected_surface_ids': 26687, 'audit_tables': 'header-only'}
```

### Direct example FK check

```text
example rows: 1830
lemma rows: 1830
surface rows: 26687
missing example lemma refs: 0
missing example surface refs: 0
```

### Optional temporary PostgreSQL load validation

```text
temp database: subtlex_handoff_scoped_validation_1782482380
createdb: ok
schema.sql: ok
load.sql: ok (18 COPY statements)
key checks: {"lemmas": 1830, "surface_forms": 26687, "example_sentences": 1830, "missing_example_lemmas": 0, "missing_example_surfaces": 0, "orphan_surface_form_lemma_links": 0, "freedict_sense_definitions_blocked": 0}
dropdb: ok
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
