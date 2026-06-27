# Staged Legacy DB Import

Use this flow for the committed legacy bundle at:

```text
db/import-bundles/polish-lexicon-import-v1
```

Approved manifest SHA:

```text
aa11facf36e6b7eb232dfe6b0a9a900f05407cd607efa5802bfd0d726fd93965
```

The bundle is intentionally imported only at the pre-normalization schema commit. Current code stays strict: it expects normalized `example_sentences`, `lemma_pos_distractors`, and `example_sentence_distractor_assignments` bundle tables.

## Local Reset

These commands drop `polish_lexicon` and `schema_migrations`, import the legacy bundle at the inserted pre-normalization bundle commit, then migrate forward with current code:

```sh
npm run db:local:staged-legacy
npm run db:test:staged-legacy
```

Expected final counts:

```text
example_sentences=1830
lemma_pos_distractors=7320
example_sentence_distractor_assignments=0
```

Stop `npm run dev` before resetting a database it is using, then restart it after the count check passes.

## Production

Use only the protected manual workflow:

```text
Sync Production DB
```

Required secret:

```text
NEON_DIRECT_DATABASE_URL
```

Required input:

```text
manifest_sha=aa11facf36e6b7eb232dfe6b0a9a900f05407cd607efa5802bfd0d726fd93965
```

The workflow fails if the input SHA does not match the committed bundle manifest. It resets the target schema, imports with `--replace` at the pre-normalization import commit, runs current migrations forward, and verifies the final normalized row counts.
