# PostgreSQL Import

Run from the repository root:

```sh
cd data/database-import
psql "$DATABASE_URL" -f schema.sql
psql "$DATABASE_URL" -f load.sql
```

The SQL creates and reloads the dedicated `polish_lexicon` schema.

## Validation

```sql
SELECT count(*) FROM polish_lexicon.lemmas;
SELECT count(*) FROM polish_lexicon.freedict_sense_definitions WHERE definition ~ '^\s*=';
SELECT count(*) FROM polish_lexicon.surface_form_lemma_links l
LEFT JOIN polish_lexicon.lemma_subtlex_pos p USING (lemma_subtlex_pos_id)
WHERE p.lemma_subtlex_pos_id IS NULL;
SELECT count(*) FROM polish_lexicon.surface_form_lemma_frequency_ranks;
SELECT reason_code, count(*) FROM polish_lexicon.rejected_lemmas GROUP BY reason_code ORDER BY reason_code;
```

Expected lemma count: `10,000`.
Expected blocked final definitions: `0`.
