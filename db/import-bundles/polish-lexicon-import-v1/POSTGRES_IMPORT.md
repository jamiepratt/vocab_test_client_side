# PostgreSQL Import

Run from this handoff bundle root:

```sh
psql "$DATABASE_URL" -f schema.sql
psql "$DATABASE_URL" -f load.sql
```

The SQL creates and reloads the dedicated `polish_lexicon` schema. TSV paths in
`load.sql` are relative to the handoff root and point at `tsv/*.tsv`.

## Validation

```sql
SELECT count(*) FROM polish_lexicon.lemmas;
SELECT count(*) FROM polish_lexicon.example_sentences;
SELECT count(*) FROM polish_lexicon.example_sentences e
LEFT JOIN polish_lexicon.lemmas l USING (lemma_id)
WHERE l.lemma_id IS NULL;
SELECT count(*) FROM polish_lexicon.example_sentences e
LEFT JOIN polish_lexicon.surface_forms s USING (surface_form_id)
WHERE s.surface_form_id IS NULL;
SELECT count(*) FROM polish_lexicon.freedict_sense_definitions WHERE definition ~ '^\s*=';
SELECT count(*) FROM polish_lexicon.surface_form_lemma_links l
LEFT JOIN polish_lexicon.lemma_subtlex_pos p USING (lemma_subtlex_pos_id)
WHERE p.lemma_subtlex_pos_id IS NULL;
```

Expected lemma count: `1,830`.
Expected example count: `1,830`.
Expected missing example refs: `0`.
Expected blocked final definitions: `0`.
