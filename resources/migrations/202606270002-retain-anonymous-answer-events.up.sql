CREATE TABLE polish_lexicon.answer_events (
  answer_event_id bigserial PRIMARY KEY,
  anonymous_session_id uuid NOT NULL,
  submitted_at timestamptz NOT NULL DEFAULT now(),
  test_block_id text NOT NULL CHECK (btrim(test_block_id) <> ''),
  target_lemma_id integer NOT NULL CHECK (target_lemma_id > 0),
  target_surface_form_id integer CHECK (target_surface_form_id > 0),
  candidate_rank integer NOT NULL CHECK (candidate_rank > 0),
  inventory_stratum integer NOT NULL CHECK (inventory_stratum > 0),
  lemma_rank integer NOT NULL CHECK (lemma_rank > 0),
  surface_difficulty_rank integer CHECK (surface_difficulty_rank > 0),
  calibrated_difficulty numeric,
  item_type text NOT NULL CHECK (btrim(item_type) <> ''),
  choice_count integer NOT NULL CHECK (choice_count > 0),
  guess_rate numeric NOT NULL CHECK (guess_rate >= 0 AND guess_rate <= 1),
  selected_answer text NOT NULL CHECK (btrim(selected_answer) <> ''),
  correct boolean NOT NULL,
  response_time_ms integer NOT NULL CHECK (response_time_ms >= 0),
  attention_check_status text NOT NULL CHECK (btrim(attention_check_status) <> ''),
  CHECK (surface_difficulty_rank IS NOT NULL OR calibrated_difficulty IS NOT NULL)
);
--;;

CREATE INDEX answer_events_anonymous_session_id_idx
  ON polish_lexicon.answer_events(anonymous_session_id);
--;;

CREATE INDEX answer_events_target_lemma_id_idx
  ON polish_lexicon.answer_events(target_lemma_id);
--;;

CREATE INDEX answer_events_test_block_id_idx
  ON polish_lexicon.answer_events(test_block_id);
