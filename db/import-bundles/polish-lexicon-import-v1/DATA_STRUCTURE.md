# Data Structure

This handoff is a scoped subset of `data/database-import`: all example sentences,
only their referenced lemmas, and every retained surface connected to those
lemmas through the link/rank tables. IDs are original generated IDs; they are not
renumbered.

## Selection

- Example rows: 1,830
- Referenced lemmas: 1,830
- Direct example surfaces: 1,753
- Connected retained surfaces: 26,687
- Rejected/audit tables are header-only in this scoped handoff.

## Tables

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
