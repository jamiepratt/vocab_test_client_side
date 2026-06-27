ALTER TABLE polish_lexicon.example_sentences
  ADD COLUMN distractor_1_translation text,
  ADD COLUMN distractor_2_translation text,
  ADD COLUMN distractor_3_translation text,
  ADD COLUMN distractor_4_translation text;
--;;

WITH assigned_examples AS (
  SELECT DISTINCT example_sentence_id
  FROM polish_lexicon.example_sentence_distractor_assignments
),
effective_rows AS (
  SELECT e.example_sentence_id,
         d.distractor_translation,
         row_number() OVER (PARTITION BY e.example_sentence_id ORDER BY d.import_order, d.lemma_pos_distractor_id) AS distractor_index
  FROM polish_lexicon.example_sentences e
  JOIN polish_lexicon.example_sentence_distractor_assignments a
    ON a.example_sentence_id = e.example_sentence_id
  JOIN polish_lexicon.lemma_pos_distractors d
    ON d.lemma_pos_distractor_id = a.lemma_pos_distractor_id

  UNION ALL

  SELECT e.example_sentence_id,
         d.distractor_translation,
         row_number() OVER (PARTITION BY e.example_sentence_id ORDER BY d.import_order, d.lemma_pos_distractor_id) AS distractor_index
  FROM polish_lexicon.example_sentences e
  JOIN polish_lexicon.lemma_pos_distractors d
    ON d.lemma_subtlex_pos_id = e.lemma_subtlex_pos_id
   AND d.is_default
  WHERE NOT EXISTS (
    SELECT 1
    FROM assigned_examples assigned
    WHERE assigned.example_sentence_id = e.example_sentence_id
  )
),
pivoted AS (
  SELECT example_sentence_id,
         max(distractor_translation) FILTER (WHERE distractor_index = 1) AS distractor_1_translation,
         max(distractor_translation) FILTER (WHERE distractor_index = 2) AS distractor_2_translation,
         max(distractor_translation) FILTER (WHERE distractor_index = 3) AS distractor_3_translation,
         max(distractor_translation) FILTER (WHERE distractor_index = 4) AS distractor_4_translation
  FROM effective_rows
  GROUP BY example_sentence_id
)
UPDATE polish_lexicon.example_sentences e
SET distractor_1_translation = p.distractor_1_translation,
    distractor_2_translation = p.distractor_2_translation,
    distractor_3_translation = p.distractor_3_translation,
    distractor_4_translation = p.distractor_4_translation
FROM pivoted p
WHERE p.example_sentence_id = e.example_sentence_id;
--;;

DO $$
DECLARE
  bad_count integer;
BEGIN
  SELECT count(*) INTO bad_count
  FROM polish_lexicon.example_sentences
  WHERE distractor_1_translation IS NULL
     OR distractor_2_translation IS NULL
     OR distractor_3_translation IS NULL
     OR distractor_4_translation IS NULL;

  IF bad_count > 0 THEN
    RAISE EXCEPTION 'Cannot roll back normalized distractors with fewer than four effective distractors: % rows', bad_count;
  END IF;
END $$;
--;;

ALTER TABLE polish_lexicon.example_sentences
  ALTER COLUMN distractor_1_translation SET NOT NULL,
  ALTER COLUMN distractor_2_translation SET NOT NULL,
  ALTER COLUMN distractor_3_translation SET NOT NULL,
  ALTER COLUMN distractor_4_translation SET NOT NULL,
  ADD CONSTRAINT example_sentences_word_not_distractor_1_check CHECK (lower(word_translation) <> lower(distractor_1_translation)),
  ADD CONSTRAINT example_sentences_word_not_distractor_2_check CHECK (lower(word_translation) <> lower(distractor_2_translation)),
  ADD CONSTRAINT example_sentences_word_not_distractor_3_check CHECK (lower(word_translation) <> lower(distractor_3_translation)),
  ADD CONSTRAINT example_sentences_word_not_distractor_4_check CHECK (lower(word_translation) <> lower(distractor_4_translation)),
  ADD CONSTRAINT example_sentences_distractor_1_not_2_check CHECK (lower(distractor_1_translation) <> lower(distractor_2_translation)),
  ADD CONSTRAINT example_sentences_distractor_1_not_3_check CHECK (lower(distractor_1_translation) <> lower(distractor_3_translation)),
  ADD CONSTRAINT example_sentences_distractor_1_not_4_check CHECK (lower(distractor_1_translation) <> lower(distractor_4_translation)),
  ADD CONSTRAINT example_sentences_distractor_2_not_3_check CHECK (lower(distractor_2_translation) <> lower(distractor_3_translation)),
  ADD CONSTRAINT example_sentences_distractor_2_not_4_check CHECK (lower(distractor_2_translation) <> lower(distractor_4_translation)),
  ADD CONSTRAINT example_sentences_distractor_3_not_4_check CHECK (lower(distractor_3_translation) <> lower(distractor_4_translation));
--;;

DROP INDEX IF EXISTS polish_lexicon.example_sentence_distractor_assignments_distractor_id_idx;
--;;

DROP INDEX IF EXISTS polish_lexicon.lemma_pos_distractors_lemma_subtlex_pos_id_idx;
--;;

DROP INDEX IF EXISTS polish_lexicon.example_sentences_lemma_subtlex_pos_id_idx;
--;;

DROP TABLE polish_lexicon.example_sentence_distractor_assignments;
--;;

DROP TABLE polish_lexicon.lemma_pos_distractors;
--;;

ALTER TABLE polish_lexicon.example_sentences
  DROP CONSTRAINT IF EXISTS example_sentences_surface_lemma_pos_fk,
  DROP CONSTRAINT IF EXISTS example_sentences_lemma_pos_fk,
  DROP COLUMN lemma_subtlex_pos_id,
  ADD CONSTRAINT example_sentences_lemma_id_key UNIQUE (lemma_id);
--;;

ALTER TABLE polish_lexicon.lemma_subtlex_pos
  DROP CONSTRAINT IF EXISTS lemma_subtlex_pos_id_lemma_id_key;
