ALTER TABLE polish_lexicon.lemma_subtlex_pos
  ADD CONSTRAINT lemma_subtlex_pos_id_lemma_id_key UNIQUE (lemma_subtlex_pos_id, lemma_id);
--;;

ALTER TABLE polish_lexicon.example_sentences
  ADD COLUMN lemma_subtlex_pos_id integer;
--;;

CREATE TEMP TABLE pvsm001_example_lemma_pos ON COMMIT DROP AS
WITH ranked_links AS (
  SELECT e.example_sentence_id,
         l.lemma_subtlex_pos_id,
         count(l.lemma_subtlex_pos_id) OVER (PARTITION BY e.example_sentence_id) AS link_count,
         row_number() OVER (
           PARTITION BY e.example_sentence_id
           ORDER BY p.subtlex_frequency DESC NULLS LAST,
                    p.contextual_diversity_count DESC NULLS LAST,
                    l.lemma_subtlex_pos_id
         ) AS link_rank
  FROM polish_lexicon.example_sentences e
  LEFT JOIN polish_lexicon.surface_form_lemma_links l
    ON l.surface_form_id = e.surface_form_id
   AND l.lemma_id = e.lemma_id
  LEFT JOIN polish_lexicon.lemma_subtlex_pos p
    ON p.lemma_subtlex_pos_id = l.lemma_subtlex_pos_id
)
SELECT example_sentence_id,
       link_count,
       lemma_subtlex_pos_id
FROM ranked_links
WHERE link_rank = 1;
--;;

DO $$
DECLARE
  bad_count integer;
BEGIN
  SELECT count(*) INTO bad_count
  FROM pvsm001_example_lemma_pos
  WHERE link_count < 1
     OR lemma_subtlex_pos_id IS NULL;

  IF bad_count > 0 THEN
    RAISE EXCEPTION 'Example sentences do not map to any lemma/POS link: % rows', bad_count;
  END IF;
END $$;
--;;

UPDATE polish_lexicon.example_sentences e
SET lemma_subtlex_pos_id = p.lemma_subtlex_pos_id
FROM pvsm001_example_lemma_pos p
WHERE p.example_sentence_id = e.example_sentence_id;
--;;

ALTER TABLE polish_lexicon.example_sentences
  ALTER COLUMN lemma_subtlex_pos_id SET NOT NULL;
--;;

CREATE TEMP TABLE pvsm001_old_example_distractors ON COMMIT DROP AS
SELECT e.example_sentence_id,
       e.lemma_subtlex_pos_id,
       ARRAY(
         SELECT distractor_value
         FROM unnest(ARRAY[
           e.distractor_1_translation,
           e.distractor_2_translation,
           e.distractor_3_translation,
           e.distractor_4_translation
         ]) AS distractor(distractor_value)
         ORDER BY lower(distractor_value), distractor_value
       ) AS distractors,
       ARRAY(
         SELECT lower(distractor_value)
         FROM unnest(ARRAY[
           e.distractor_1_translation,
           e.distractor_2_translation,
           e.distractor_3_translation,
           e.distractor_4_translation
         ]) AS distractor(distractor_value)
         ORDER BY lower(distractor_value), distractor_value
       ) AS distractor_keys
FROM polish_lexicon.example_sentences e;
--;;

CREATE TEMP TABLE pvsm001_default_sets ON COMMIT DROP AS
SELECT DISTINCT ON (lemma_subtlex_pos_id)
       lemma_subtlex_pos_id,
       distractors,
       distractor_keys
FROM (
  SELECT lemma_subtlex_pos_id,
         distractors,
         distractor_keys,
         count(*) AS set_count,
         min(example_sentence_id) AS first_example_sentence_id
  FROM pvsm001_old_example_distractors
  GROUP BY lemma_subtlex_pos_id, distractors, distractor_keys
) sets
ORDER BY lemma_subtlex_pos_id,
         set_count DESC,
         array_to_string(distractor_keys, E'\t'),
         first_example_sentence_id;
--;;

CREATE TABLE polish_lexicon.lemma_pos_distractors (
  lemma_pos_distractor_id integer PRIMARY KEY,
  lemma_subtlex_pos_id integer NOT NULL REFERENCES polish_lexicon.lemma_subtlex_pos(lemma_subtlex_pos_id) ON DELETE CASCADE,
  distractor_translation text NOT NULL CHECK (btrim(distractor_translation) <> ''),
  is_default boolean NOT NULL DEFAULT false,
  import_order integer NOT NULL CHECK (import_order > 0),
  UNIQUE (lemma_subtlex_pos_id, import_order)
);
--;;

CREATE UNIQUE INDEX lemma_pos_distractors_lemma_pos_translation_ci_idx
  ON polish_lexicon.lemma_pos_distractors(lemma_subtlex_pos_id, lower(distractor_translation));
--;;

INSERT INTO polish_lexicon.lemma_pos_distractors
  (lemma_pos_distractor_id, lemma_subtlex_pos_id, distractor_translation, is_default, import_order)
SELECT row_number() OVER (ORDER BY lemma_subtlex_pos_id, lower(distractor_translation), distractor_translation)::integer,
       lemma_subtlex_pos_id,
       distractor_translation,
       is_default,
       row_number() OVER (PARTITION BY lemma_subtlex_pos_id
                          ORDER BY lower(distractor_translation), distractor_translation)::integer
FROM (
  SELECT DISTINCT d.lemma_subtlex_pos_id,
         distractor.distractor_translation,
         EXISTS (
           SELECT 1
           FROM pvsm001_default_sets defaults
           WHERE defaults.lemma_subtlex_pos_id = d.lemma_subtlex_pos_id
             AND lower(distractor.distractor_translation) = ANY(defaults.distractor_keys)
         ) AS is_default
  FROM pvsm001_old_example_distractors d
  CROSS JOIN LATERAL unnest(d.distractors) AS distractor(distractor_translation)
) distinct_distractors;
--;;

CREATE TABLE polish_lexicon.example_sentence_distractor_assignments (
  example_sentence_id integer NOT NULL REFERENCES polish_lexicon.example_sentences(example_sentence_id) ON DELETE CASCADE,
  lemma_pos_distractor_id integer NOT NULL REFERENCES polish_lexicon.lemma_pos_distractors(lemma_pos_distractor_id) ON DELETE CASCADE,
  PRIMARY KEY (example_sentence_id, lemma_pos_distractor_id)
);
--;;

INSERT INTO polish_lexicon.example_sentence_distractor_assignments
  (example_sentence_id, lemma_pos_distractor_id)
SELECT snapshot.example_sentence_id,
       distractors.lemma_pos_distractor_id
FROM pvsm001_old_example_distractors snapshot
JOIN pvsm001_default_sets defaults
  ON defaults.lemma_subtlex_pos_id = snapshot.lemma_subtlex_pos_id
CROSS JOIN LATERAL unnest(snapshot.distractor_keys) AS distractor_key(value)
JOIN polish_lexicon.lemma_pos_distractors distractors
  ON distractors.lemma_subtlex_pos_id = snapshot.lemma_subtlex_pos_id
 AND lower(distractors.distractor_translation) = distractor_key.value
WHERE snapshot.distractor_keys <> defaults.distractor_keys;
--;;

DO $$
DECLARE
  mismatch_count integer;
BEGIN
  WITH assigned_examples AS (
    SELECT DISTINCT example_sentence_id
    FROM polish_lexicon.example_sentence_distractor_assignments
  ),
  effective_distractors AS (
    SELECT snapshot.example_sentence_id,
           ARRAY_AGG(distractors.distractor_translation
                     ORDER BY lower(distractors.distractor_translation), distractors.distractor_translation) AS distractors
    FROM pvsm001_old_example_distractors snapshot
    JOIN polish_lexicon.example_sentence_distractor_assignments assignments
      ON assignments.example_sentence_id = snapshot.example_sentence_id
    JOIN polish_lexicon.lemma_pos_distractors distractors
      ON distractors.lemma_pos_distractor_id = assignments.lemma_pos_distractor_id
    GROUP BY snapshot.example_sentence_id

    UNION ALL

    SELECT snapshot.example_sentence_id,
           ARRAY_AGG(distractors.distractor_translation
                     ORDER BY lower(distractors.distractor_translation), distractors.distractor_translation) AS distractors
    FROM pvsm001_old_example_distractors snapshot
    JOIN polish_lexicon.lemma_pos_distractors distractors
      ON distractors.lemma_subtlex_pos_id = snapshot.lemma_subtlex_pos_id
     AND distractors.is_default
    WHERE NOT EXISTS (
      SELECT 1
      FROM assigned_examples assigned
      WHERE assigned.example_sentence_id = snapshot.example_sentence_id
    )
    GROUP BY snapshot.example_sentence_id
  )
  SELECT count(*) INTO mismatch_count
  FROM pvsm001_old_example_distractors snapshot
  JOIN effective_distractors effective
    ON effective.example_sentence_id = snapshot.example_sentence_id
  WHERE effective.distractors <> snapshot.distractors;

  IF mismatch_count > 0 THEN
    RAISE EXCEPTION 'Normalized effective distractors do not match old example distractor snapshots: % rows', mismatch_count;
  END IF;
END $$;
--;;

ALTER TABLE polish_lexicon.example_sentences
  DROP CONSTRAINT IF EXISTS example_sentences_lemma_id_key,
  ADD CONSTRAINT example_sentences_lemma_pos_fk
    FOREIGN KEY (lemma_subtlex_pos_id, lemma_id)
    REFERENCES polish_lexicon.lemma_subtlex_pos(lemma_subtlex_pos_id, lemma_id)
    ON DELETE CASCADE,
  ADD CONSTRAINT example_sentences_surface_lemma_pos_fk
    FOREIGN KEY (surface_form_id, lemma_subtlex_pos_id)
    REFERENCES polish_lexicon.surface_form_lemma_links(surface_form_id, lemma_subtlex_pos_id)
    ON DELETE CASCADE;
--;;

ALTER TABLE polish_lexicon.example_sentences
  DROP COLUMN distractor_1_translation,
  DROP COLUMN distractor_2_translation,
  DROP COLUMN distractor_3_translation,
  DROP COLUMN distractor_4_translation;
--;;

CREATE INDEX example_sentences_lemma_subtlex_pos_id_idx
  ON polish_lexicon.example_sentences(lemma_subtlex_pos_id);
--;;

CREATE INDEX lemma_pos_distractors_lemma_subtlex_pos_id_idx
  ON polish_lexicon.lemma_pos_distractors(lemma_subtlex_pos_id);
--;;

CREATE INDEX example_sentence_distractor_assignments_distractor_id_idx
  ON polish_lexicon.example_sentence_distractor_assignments(lemma_pos_distractor_id);
