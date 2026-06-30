DROP SCHEMA IF EXISTS polish_lexicon CASCADE;
CREATE SCHEMA polish_lexicon;

CREATE TABLE polish_lexicon.lemmas (
  lemma_id integer PRIMARY KEY,
  lemma text NOT NULL UNIQUE,
  total_frequency_sn_sum numeric NOT NULL,
  total_frequency_sn_sum_rank integer NOT NULL,
  total_subtlex_frequency bigint NOT NULL,
  lemma_subtlex_pos_count integer NOT NULL CHECK (lemma_subtlex_pos_count > 0),
  surface_form_count integer NOT NULL CHECK (surface_form_count > 0),
  nkjp_frequency bigint,
  nkjp_frequency_rank integer
);

CREATE TABLE polish_lexicon.lemma_subtlex_pos (
  lemma_subtlex_pos_id integer PRIMARY KEY,
  lemma_id integer NOT NULL REFERENCES polish_lexicon.lemmas(lemma_id) ON DELETE CASCADE,
  lemma text NOT NULL,
  subtlex_pos text NOT NULL,
  subtlex_frequency bigint NOT NULL,
  contextual_diversity_count bigint NOT NULL,
  contextual_diversity numeric NOT NULL,
  UNIQUE (lemma_id, subtlex_pos),
  UNIQUE (lemma, subtlex_pos)
);

CREATE TABLE polish_lexicon.surface_forms (
  surface_form_id integer PRIMARY KEY,
  surface_form text NOT NULL UNIQUE
);

CREATE TABLE polish_lexicon.surface_subtlex_metrics (
  surface_form_id integer PRIMARY KEY REFERENCES polish_lexicon.surface_forms(surface_form_id) ON DELETE CASCADE,
  spellcheck smallint NOT NULL,
  alphabetical smallint NOT NULL,
  nchar integer NOT NULL,
  subtlex_frequency bigint NOT NULL,
  subtlex_frequency_rank integer NOT NULL,
  capitalized_frequency bigint NOT NULL,
  contextual_diversity numeric NOT NULL,
  contextual_diversity_count bigint NOT NULL,
  dominant_pos text,
  dominant_pos_frequency bigint,
  dominant_lemma_pos text,
  dominant_lemma_pos_frequency bigint,
  dominant_lemma_pos_total_frequency bigint,
  all_pos text,
  all_pos_frequency text,
  all_lemma_pos text,
  all_lemma_pos_frequency text,
  all_lemma_pos_total_frequency text,
  lg_frequency numeric,
  lg_million_frequency numeric,
  zipf_frequency numeric,
  lg_contextual_diversity numeric,
  frequency_sn_sum bigint,
  zipf_frequency_sn_sum numeric,
  avg_zipf_freq_sn numeric NOT NULL,
  avg_zipf_freq_sn_rank integer NOT NULL
);

CREATE TABLE polish_lexicon.surface_nkjp_metrics (
  surface_form_id integer PRIMARY KEY REFERENCES polish_lexicon.surface_forms(surface_form_id) ON DELETE CASCADE,
  nkjp_frequency bigint,
  nkjp_frequency_rank integer,
  occurrence_pct numeric,
  per_million numeric
);

CREATE TABLE polish_lexicon.surface_form_lemma_links (
  surface_form_lemma_link_id integer PRIMARY KEY,
  surface_form_id integer NOT NULL REFERENCES polish_lexicon.surface_forms(surface_form_id) ON DELETE CASCADE,
  lemma_id integer NOT NULL REFERENCES polish_lexicon.lemmas(lemma_id) ON DELETE CASCADE,
  lemma_subtlex_pos_id integer NOT NULL REFERENCES polish_lexicon.lemma_subtlex_pos(lemma_subtlex_pos_id) ON DELETE CASCADE,
  UNIQUE (surface_form_id, lemma_subtlex_pos_id)
);

CREATE TABLE polish_lexicon.surface_form_lemma_frequency_ranks (
  surface_form_lemma_frequency_rank_id integer PRIMARY KEY,
  surface_form_id integer NOT NULL REFERENCES polish_lexicon.surface_forms(surface_form_id) ON DELETE CASCADE,
  lemma_id integer NOT NULL REFERENCES polish_lexicon.lemmas(lemma_id) ON DELETE CASCADE,
  surface_form text NOT NULL,
  lemma text NOT NULL,
  surface_frequency_sn_sum bigint NOT NULL,
  surface_lemma_link_subtlex_pos text NOT NULL,
  surface_lemma_link_frequency bigint NOT NULL,
  surface_lemma_link_total_frequency bigint NOT NULL,
  surface_lemma_link_frequency_share numeric NOT NULL,
  surface_lemma_link_frequency_sn_sum numeric NOT NULL,
  surface_avg_zipf_freq_sn numeric NOT NULL,
  surface_avg_zipf_freq_sn_rank integer NOT NULL,
  lemma_total_frequency_sn_sum numeric NOT NULL,
  lemma_total_frequency_sn_sum_rank integer NOT NULL,
  UNIQUE (surface_form_id, lemma_id)
);

CREATE TABLE polish_lexicon.freedict_entries (
  freedict_entry_id integer PRIMARY KEY,
  lemma_id integer NOT NULL REFERENCES polish_lexicon.lemmas(lemma_id) ON DELETE CASCADE,
  source_entry_index integer NOT NULL,
  headword text NOT NULL,
  entry_key text NOT NULL,
  UNIQUE (lemma_id, source_entry_index)
);

CREATE TABLE polish_lexicon.freedict_entry_pos (
  freedict_entry_pos_id integer PRIMARY KEY,
  freedict_entry_id integer NOT NULL REFERENCES polish_lexicon.freedict_entries(freedict_entry_id) ON DELETE CASCADE,
  pos text NOT NULL,
  UNIQUE (freedict_entry_id, pos)
);

CREATE TABLE polish_lexicon.freedict_pronunciations (
  freedict_pronunciation_id integer PRIMARY KEY,
  freedict_entry_id integer NOT NULL REFERENCES polish_lexicon.freedict_entries(freedict_entry_id) ON DELETE CASCADE,
  pronunciation text NOT NULL,
  UNIQUE (freedict_entry_id, pronunciation)
);

CREATE TABLE polish_lexicon.freedict_senses (
  freedict_sense_id integer PRIMARY KEY,
  freedict_entry_id integer NOT NULL REFERENCES polish_lexicon.freedict_entries(freedict_entry_id) ON DELETE CASCADE,
  sense_index integer NOT NULL,
  UNIQUE (freedict_entry_id, sense_index)
);

CREATE TABLE polish_lexicon.freedict_sense_translations (
  freedict_sense_translation_id integer PRIMARY KEY,
  freedict_sense_id integer NOT NULL REFERENCES polish_lexicon.freedict_senses(freedict_sense_id) ON DELETE CASCADE,
  translation text NOT NULL,
  UNIQUE (freedict_sense_id, translation)
);

CREATE TABLE polish_lexicon.freedict_sense_definitions (
  freedict_sense_definition_id integer PRIMARY KEY,
  freedict_sense_id integer NOT NULL REFERENCES polish_lexicon.freedict_senses(freedict_sense_id) ON DELETE CASCADE,
  definition text NOT NULL,
  CHECK (definition !~ '^\s*=')
);

CREATE TABLE polish_lexicon.example_sentences (
  example_sentence_id integer PRIMARY KEY,
  sentence text NOT NULL CHECK (btrim(sentence) <> ''),
  sentence_translation text NOT NULL CHECK (btrim(sentence_translation) <> ''),
  surface_form_id integer NOT NULL REFERENCES polish_lexicon.surface_forms(surface_form_id) ON DELETE CASCADE,
  lemma_id integer NOT NULL REFERENCES polish_lexicon.lemmas(lemma_id) ON DELETE CASCADE,
  word_translation text NOT NULL CHECK (btrim(word_translation) <> ''),
  distractor_1_translation text NOT NULL CHECK (btrim(distractor_1_translation) <> ''),
  distractor_2_translation text NOT NULL CHECK (btrim(distractor_2_translation) <> ''),
  distractor_3_translation text NOT NULL CHECK (btrim(distractor_3_translation) <> ''),
  distractor_4_translation text NOT NULL CHECK (btrim(distractor_4_translation) <> ''),
  UNIQUE (lemma_id),
  CHECK (lower(word_translation) <> lower(distractor_1_translation)),
  CHECK (lower(word_translation) <> lower(distractor_2_translation)),
  CHECK (lower(word_translation) <> lower(distractor_3_translation)),
  CHECK (lower(word_translation) <> lower(distractor_4_translation)),
  CHECK (lower(distractor_1_translation) <> lower(distractor_2_translation)),
  CHECK (lower(distractor_1_translation) <> lower(distractor_3_translation)),
  CHECK (lower(distractor_1_translation) <> lower(distractor_4_translation)),
  CHECK (lower(distractor_2_translation) <> lower(distractor_3_translation)),
  CHECK (lower(distractor_2_translation) <> lower(distractor_4_translation)),
  CHECK (lower(distractor_3_translation) <> lower(distractor_4_translation))
);

CREATE TABLE polish_lexicon.rejected_lemmas (
  rejected_lemma_id integer PRIMARY KEY,
  lemma text NOT NULL,
  subtlex_pos text,
  spelling text,
  reason_code text NOT NULL,
  subtlex_frequency bigint,
  contextual_diversity_count bigint,
  contextual_diversity numeric,
  total_frequency_sn_sum numeric
);

CREATE TABLE polish_lexicon.rejected_surfaces (
  rejected_surface_id integer PRIMARY KEY,
  surface_form text NOT NULL,
  reason_code text NOT NULL,
  subtlex_frequency bigint,
  capitalized_frequency bigint,
  avg_zipf_freq_sn numeric
);

CREATE TABLE polish_lexicon.rejected_freedict_rows (
  rejected_freedict_id integer PRIMARY KEY,
  lemma text NOT NULL,
  freedict_headword text,
  entry_key text,
  source_entry_index integer,
  source_pos text,
  sense_index integer,
  reason_code text NOT NULL,
  translation text,
  definition text
);

CREATE TABLE polish_lexicon.nkjp_build_stats (
  stage text PRIMARY KEY,
  rows bigint NOT NULL,
  count bigint NOT NULL
);

CREATE INDEX lemma_subtlex_pos_lemma_id_idx ON polish_lexicon.lemma_subtlex_pos(lemma_id);
CREATE INDEX surface_form_lemma_links_lemma_id_idx ON polish_lexicon.surface_form_lemma_links(lemma_id);
CREATE INDEX surface_form_lemma_links_surface_form_id_idx ON polish_lexicon.surface_form_lemma_links(surface_form_id);
CREATE INDEX surface_form_lemma_frequency_ranks_lemma_id_idx ON polish_lexicon.surface_form_lemma_frequency_ranks(lemma_id);
CREATE INDEX surface_form_lemma_frequency_ranks_surface_rank_idx ON polish_lexicon.surface_form_lemma_frequency_ranks(surface_avg_zipf_freq_sn_rank);
CREATE INDEX freedict_entries_lemma_id_idx ON polish_lexicon.freedict_entries(lemma_id);
CREATE INDEX rejected_lemmas_reason_code_idx ON polish_lexicon.rejected_lemmas(reason_code);
CREATE INDEX rejected_surfaces_reason_code_idx ON polish_lexicon.rejected_surfaces(reason_code);
CREATE INDEX rejected_freedict_reason_code_idx ON polish_lexicon.rejected_freedict_rows(reason_code);
