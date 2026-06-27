(ns jamiepratt.vocab-test-client-side.db-test
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [jamiepratt.vocab-test-client-side.db :as db]
   [next.jdbc :as jdbc])
  (:import
   [java.nio.file Files]))

(defn- temp-dir []
  (.toFile (Files/createTempDirectory "polish-lexicon-bundle-" (make-array java.nio.file.attribute.FileAttribute 0))))

(defn- write-file! [root relative-path content]
  (let [file (io/file root relative-path)]
    (.mkdirs (.getParentFile file))
    (spit file content :encoding "UTF-8")
    file))

(defn- table-content [header rows]
  (str (str/join "\t" header) "\n"
       (when (seq rows)
         (str (str/join "\n" rows) "\n"))))

(defn- data-row-count [content]
  (max 0 (dec (count (str/split-lines content)))))

(defn- migration-statements [relative-path]
  (->> (slurp (io/file relative-path) :encoding "UTF-8")
       (#(str/split % #"(?m)^--;;\s*$"))
       (map str/trim)
       (remove str/blank?)))

(defn- run-migration-file! [conn relative-path]
  (jdbc/with-transaction [tx conn]
    (doseq [statement (migration-statements relative-path)]
      (jdbc/execute! tx [statement]))))

(defn- write-bundle! [tables]
  (let [root (temp-dir)
        table-entries (mapv (fn [{:keys [table header rows]}]
                              (let [file (str "tsv/" table ".tsv")
                                    content (table-content header rows)]
                                (write-file! root file content)
                                {:table table
                                 :file file
                                 :rows (data-row-count content)
                                 :sha256 (db/file-sha256 (io/file root file))
                                 :header header}))
                            tables)
        manifest {:schema_name "polish_lexicon"
                  :bundle_name "test-bundle"
                  :selection_scope "test"
                  :source_git_commit_sha "test"
                  :encoding "utf-8"
                  :delimiter "\t"
                  :header true
                  :null_value "\\N"
                  :tables table-entries}]
    (write-file! root "manifest.json" (json/write-str manifest))
    root))

(defn- small-bundle! []
  (write-bundle!
   [{:table "lemmas"
     :header ["lemma_id" "lemma" "total_frequency_sn_sum" "total_frequency_sn_sum_rank"
              "total_subtlex_frequency" "lemma_subtlex_pos_count" "surface_form_count"
              "nkjp_frequency" "nkjp_frequency_rank"]
     :rows ["1\tkot\t10.0\t1\t10\t1\t1\t\\N\t\\N"]}
    {:table "lemma_subtlex_pos"
     :header ["lemma_subtlex_pos_id" "lemma_id" "lemma" "subtlex_pos"
              "subtlex_frequency" "contextual_diversity_count" "contextual_diversity"]
     :rows ["1\t1\tkot\tsubst\t10\t10\t1.0"]}
    {:table "surface_forms"
     :header ["surface_form_id" "surface_form"]
     :rows ["1\tkot"]}
    {:table "surface_subtlex_metrics"
     :header ["surface_form_id" "spellcheck" "alphabetical" "nchar" "subtlex_frequency"
              "subtlex_frequency_rank" "capitalized_frequency" "contextual_diversity"
              "contextual_diversity_count" "dominant_pos" "dominant_pos_frequency"
              "dominant_lemma_pos" "dominant_lemma_pos_frequency"
              "dominant_lemma_pos_total_frequency" "all_pos" "all_pos_frequency"
              "all_lemma_pos" "all_lemma_pos_frequency" "all_lemma_pos_total_frequency"
              "lg_frequency" "lg_million_frequency" "zipf_frequency"
              "lg_contextual_diversity" "frequency_sn_sum" "zipf_frequency_sn_sum"
              "avg_zipf_freq_sn" "avg_zipf_freq_sn_rank"]
     :rows ["1\t1\t1\t3\t10\t1\t0\t1.0\t10\tsubst\t10\tkot:subst\t10\t10\tsubst\t10\tkot:subst\t10\t10\t1.0\t1.0\t5.0\t1.0\t10\t5.0\t5.0\t1"]}
    {:table "surface_nkjp_metrics"
     :header ["surface_form_id" "nkjp_frequency" "nkjp_frequency_rank" "occurrence_pct" "per_million"]
     :rows ["1\t\\N\t\\N\t\\N\t\\N"]}
    {:table "surface_form_lemma_links"
     :header ["surface_form_lemma_link_id" "surface_form_id" "lemma_id" "lemma_subtlex_pos_id"]
     :rows ["1\t1\t1\t1"]}
    {:table "surface_form_lemma_frequency_ranks"
     :header ["surface_form_lemma_frequency_rank_id" "surface_form_id" "lemma_id"
              "surface_form" "lemma" "surface_frequency_sn_sum"
              "surface_lemma_link_subtlex_pos" "surface_lemma_link_frequency"
              "surface_lemma_link_total_frequency" "surface_lemma_link_frequency_share"
              "surface_lemma_link_frequency_sn_sum" "surface_avg_zipf_freq_sn"
              "surface_avg_zipf_freq_sn_rank" "lemma_total_frequency_sn_sum"
              "lemma_total_frequency_sn_sum_rank"]
     :rows ["1\t1\t1\tkot\tkot\t10\tsubst\t10\t10\t1.0\t10.0\t5.0\t1\t10.0\t1"]}
    {:table "freedict_entries"
     :header ["freedict_entry_id" "lemma_id" "source_entry_index" "headword" "entry_key"]
     :rows ["1\t1\t1\tkot\tkot"]}
    {:table "freedict_entry_pos"
     :header ["freedict_entry_pos_id" "freedict_entry_id" "pos"]
     :rows ["1\t1\tnoun"]}
    {:table "freedict_pronunciations"
     :header ["freedict_pronunciation_id" "freedict_entry_id" "pronunciation"]
     :rows ["1\t1\tkot"]}
    {:table "freedict_senses"
     :header ["freedict_sense_id" "freedict_entry_id" "sense_index"]
     :rows ["1\t1\t1"]}
    {:table "freedict_sense_translations"
     :header ["freedict_sense_translation_id" "freedict_sense_id" "translation"]
     :rows ["1\t1\tcat"]}
    {:table "freedict_sense_definitions"
     :header ["freedict_sense_definition_id" "freedict_sense_id" "definition"]
     :rows ["1\t1\tcat"]}
    {:table "example_sentences"
     :header ["example_sentence_id" "sentence" "sentence_translation" "surface_form_id"
              "lemma_id" "lemma_subtlex_pos_id" "word_translation"]
     :rows ["1\tKot pije wodę.\tThe cat drinks water.\t1\t1\t1\tcat"
            "2\tKot śpi.\tThe cat sleeps.\t1\t1\t1\tcat"]}
    {:table "lemma_pos_distractors"
     :header ["lemma_pos_distractor_id" "lemma_subtlex_pos_id"
              "distractor_translation" "is_default" "import_order"]
     :rows ["1\t1\tdog\ttrue\t1"
            "2\t1\tbird\ttrue\t2"
            "3\t1\tfish\ttrue\t3"
            "4\t1\ttree\ttrue\t4"
            "5\t1\tcar\tfalse\t5"
            "6\t1\tchair\tfalse\t6"
            "7\t1\tcup\tfalse\t7"
            "8\t1\troad\tfalse\t8"]}
    {:table "example_sentence_distractor_assignments"
     :header ["example_sentence_id" "lemma_pos_distractor_id"]
     :rows ["2\t5"
            "2\t6"
            "2\t7"
            "2\t8"]}
    {:table "rejected_lemmas"
     :header ["rejected_lemma_id" "lemma" "subtlex_pos" "spelling" "reason_code"
              "subtlex_frequency" "contextual_diversity_count" "contextual_diversity"
              "total_frequency_sn_sum"]
     :rows []}
    {:table "rejected_surfaces"
     :header ["rejected_surface_id" "surface_form" "reason_code" "subtlex_frequency"
              "capitalized_frequency" "avg_zipf_freq_sn"]
     :rows []}
    {:table "rejected_freedict_rows"
     :header ["rejected_freedict_id" "lemma" "freedict_headword" "entry_key"
              "source_entry_index" "source_pos" "sense_index" "reason_code"
              "translation" "definition"]
     :rows []}
    {:table "nkjp_build_stats"
     :header ["stage" "rows" "count"]
     :rows ["test\t1\t1"]}]))

(deftest converts-postgres-urls-to-jdbc-urls
  (is (= "jdbc:postgresql://localhost:5432/vocab?sslmode=require&user=j%40m&password=p%3Ass"
         (db/database-url->jdbc-url
          "postgres://j%40m:p%3Ass@localhost:5432/vocab?sslmode=require")))
  (is (= "jdbc:postgresql://localhost/vocab"
         (db/database-url->jdbc-url "postgresql://localhost/vocab")))
  (is (= "jdbc:postgresql://host/vocab?sslmode=require&channelBinding=require&user=u&password=p"
         (db/database-url->jdbc-url
          "postgresql://u:p@host/vocab?sslmode=require&channel_binding=require")))
  (is (= "jdbc:postgresql://localhost/vocab"
         (db/database-url->jdbc-url "jdbc:postgresql://localhost/vocab"))))

(deftest validates-manifest-headers-and-load-order
  (let [bundle (write-bundle! [{:table "alpha"
                                :header ["id" "name"]
                                :rows ["1\tkot"]}
                               {:table "beta"
                                :header ["id" "alpha_id"]
                                :rows ["1\t1"]}])
        manifest (db/validate-bundle! bundle)]
    (is (= ["alpha" "beta"] (db/load-order manifest)))))

(deftest rejects-header-mismatches-before-db-work
  (let [bundle (temp-dir)
        file "tsv/alpha.tsv"
        content "id\tlabel\n1\tkot\n"]
    (write-file! bundle file content)
    (write-file! bundle "manifest.json"
                 (json/write-str
                  {:schema_name "polish_lexicon"
                   :bundle_name "test-bundle"
                   :selection_scope "test"
                   :source_git_commit_sha "test"
                   :encoding "utf-8"
                   :delimiter "\t"
                   :header true
                   :null_value "\\N"
                   :tables [{:table "alpha"
                             :file file
                             :rows 1
                             :sha256 (db/file-sha256 (io/file bundle file))
                             :header ["id" "name"]}]}))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"TSV header mismatch"
                          (db/validate-bundle! bundle)))))

(deftest rejects-sha-mismatches-before-db-work
  (let [bundle (write-bundle! [{:table "alpha"
                                :header ["id" "name"]
                                :rows ["1\tkot"]}])]
    (write-file! bundle "tsv/alpha.tsv" "id\tlabel\n1\tkot\n")
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"SHA-256 mismatch"
                          (db/validate-bundle! bundle)))))

(deftest rejects-legacy-example-sentence-distractor-columns
  (let [bundle (write-bundle!
                [{:table "example_sentences"
                  :header ["example_sentence_id" "sentence" "sentence_translation" "surface_form_id"
                           "lemma_id" "word_translation" "distractor_1_translation"
                           "distractor_2_translation" "distractor_3_translation"
                           "distractor_4_translation"]
                  :rows []}])]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"legacy distractor columns"
                          (db/validate-bundle! bundle)))))

(deftest rejects-case-insensitive-lemma-pos-distractor-duplicates
  (let [bundle (write-bundle!
                [{:table "lemma_pos_distractors"
                  :header ["lemma_pos_distractor_id" "lemma_subtlex_pos_id"
                           "distractor_translation" "is_default" "import_order"]
                  :rows ["1\t1\tDog\ttrue\t1"
                         "2\t1\tdog\ttrue\t2"]}])]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Duplicate lemma/POS distractor translation"
                          (db/validate-bundle! bundle)))))

(deftest rejects-assignments-that-duplicate-default-distractors
  (let [bundle (write-bundle!
                [{:table "example_sentences"
                  :header ["example_sentence_id" "sentence" "sentence_translation" "surface_form_id"
                           "lemma_id" "lemma_subtlex_pos_id" "word_translation"]
                  :rows ["1\tKot pije wodę.\tThe cat drinks water.\t1\t1\t1\tcat"]}
                 {:table "lemma_pos_distractors"
                  :header ["lemma_pos_distractor_id" "lemma_subtlex_pos_id"
                           "distractor_translation" "is_default" "import_order"]
                  :rows ["1\t1\tdog\ttrue\t1"
                         "2\t1\tbird\ttrue\t2"
                         "3\t1\tfish\ttrue\t3"
                         "4\t1\ttree\ttrue\t4"]}
                 {:table "example_sentence_distractor_assignments"
                  :header ["example_sentence_id" "lemma_pos_distractor_id"]
                  :rows ["1\t1"
                         "1\t2"
                         "1\t3"
                         "1\t4"]}])]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"duplicate the lemma/POS default distractors"
                          (db/validate-bundle! bundle)))))

(deftest rejects-ambiguous-target-surface-fallback
  (let [bundle (write-bundle!
                [{:table "surface_forms"
                  :header ["surface_form_id" "surface_form"]
                  :rows ["1\tkot"]}
                 {:table "example_sentences"
                  :header ["example_sentence_id" "sentence" "sentence_translation" "surface_form_id"
                           "lemma_id" "lemma_subtlex_pos_id" "word_translation"]
                  :rows ["1\tKot widzi kot.\tThe cat sees a cat.\t1\t1\t1\tcat"]}
                 {:table "lemma_pos_distractors"
                  :header ["lemma_pos_distractor_id" "lemma_subtlex_pos_id"
                           "distractor_translation" "is_default" "import_order"]
                  :rows ["1\t1\tdog\ttrue\t1"
                         "2\t1\tbird\ttrue\t2"
                         "3\t1\tfish\ttrue\t3"
                         "4\t1\ttree\ttrue\t4"]}
                 {:table "example_sentence_distractor_assignments"
                  :header ["example_sentence_id" "lemma_pos_distractor_id"]
                  :rows []}])]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"target surface does not occur exactly once"
                          (db/validate-bundle! bundle)))))

(deftest validates-normalized-example-distractor-bundle
  (let [manifest (db/validate-bundle! (small-bundle!))
        tables (into {} (map (juxt :table identity)) (:tables manifest))]
    (is (= ["example_sentence_id" "sentence" "sentence_translation" "surface_form_id"
            "lemma_id" "lemma_subtlex_pos_id" "word_translation"]
           (get-in tables ["example_sentences" :header])))
    (is (= 8 (get-in tables ["lemma_pos_distractors" :rows])))
    (is (= 4 (get-in tables ["example_sentence_distractor_assignments" :rows])))))

(deftest normalizes-legacy-example-distractors-without-changing-effective-set
  (when-let [database-url (not-empty (System/getenv "TEST_DATABASE_URL"))]
    (let [jdbc-url (db/database-url->jdbc-url database-url)]
      (try
        (with-open [conn (jdbc/get-connection {:connection-uri jdbc-url})]
          (jdbc/execute! conn ["DROP SCHEMA IF EXISTS polish_lexicon CASCADE"])
          (jdbc/execute! conn ["DROP TABLE IF EXISTS schema_migrations"])
          (run-migration-file! conn "resources/migrations/202606260001-polish-lexicon-schema.up.sql")
          (jdbc/execute! conn ["INSERT INTO polish_lexicon.lemmas
                                (lemma_id, lemma, total_frequency_sn_sum, total_frequency_sn_sum_rank,
                                 total_subtlex_frequency, lemma_subtlex_pos_count, surface_form_count,
                                 nkjp_frequency, nkjp_frequency_rank)
                                VALUES (1, 'kot', 10.0, 1, 10, 1, 1, NULL, NULL)"])
          (jdbc/execute! conn ["INSERT INTO polish_lexicon.lemma_subtlex_pos
                                (lemma_subtlex_pos_id, lemma_id, lemma, subtlex_pos,
                                 subtlex_frequency, contextual_diversity_count, contextual_diversity)
                                VALUES (1, 1, 'kot', 'subst', 10, 10, 1.0)"])
          (jdbc/execute! conn ["INSERT INTO polish_lexicon.surface_forms
                                (surface_form_id, surface_form)
                                VALUES (1, 'kot')"])
          (jdbc/execute! conn ["INSERT INTO polish_lexicon.surface_form_lemma_links
                                (surface_form_lemma_link_id, surface_form_id, lemma_id, lemma_subtlex_pos_id)
                                VALUES (1, 1, 1, 1)"])
          (jdbc/execute! conn ["INSERT INTO polish_lexicon.example_sentences
                                (example_sentence_id, sentence, sentence_translation, surface_form_id,
                                 lemma_id, word_translation, distractor_1_translation,
                                 distractor_2_translation, distractor_3_translation,
                                 distractor_4_translation)
                                VALUES (1, 'Kot pije wodę.', 'The cat drinks water.', 1,
                                        1, 'cat', 'dog', 'bird', 'fish', 'tree')"])
          (run-migration-file! conn "resources/migrations/202606270001-normalize-example-sentence-distractors.up.sql")
          (testing "legacy fixed columns are removed"
            (is (zero?
                 (:c (jdbc/execute-one!
                      conn
                      ["SELECT count(*) AS c
                        FROM information_schema.columns
                        WHERE table_schema = 'polish_lexicon'
                          AND table_name = 'example_sentences'
                          AND column_name LIKE 'distractor_%_translation'"])))))
          (testing "effective normalized defaults match the old unordered set"
            (is (= "bird,dog,fish,tree"
                   (:distractors
                    (jdbc/execute-one!
                     conn
                     ["SELECT string_agg(d.distractor_translation, ',' ORDER BY lower(d.distractor_translation), d.distractor_translation) AS distractors
                       FROM polish_lexicon.example_sentences e
                       JOIN polish_lexicon.lemma_pos_distractors d
                         ON d.lemma_subtlex_pos_id = e.lemma_subtlex_pos_id
                        AND d.is_default
                       WHERE e.example_sentence_id = 1"])))))
          (is (zero?
               (:c (jdbc/execute-one!
                    conn
                    ["SELECT count(*) AS c
                      FROM polish_lexicon.example_sentence_distractor_assignments"])))))
        (finally
          (with-open [conn (jdbc/get-connection {:connection-uri jdbc-url})]
            (jdbc/execute! conn ["DROP SCHEMA IF EXISTS polish_lexicon CASCADE"])
            (jdbc/execute! conn ["DROP TABLE IF EXISTS schema_migrations"])))))))

(deftest imports-and-verifies-against-test-database
  (when-let [database-url (not-empty (System/getenv "TEST_DATABASE_URL"))]
    (let [jdbc-url (db/database-url->jdbc-url database-url)
          bundle (small-bundle!)]
      (try
        (with-open [conn (jdbc/get-connection {:connection-uri jdbc-url})]
          (jdbc/execute! conn ["DROP SCHEMA IF EXISTS polish_lexicon CASCADE"])
          (jdbc/execute! conn ["DROP TABLE IF EXISTS schema_migrations"]))
        (db/migrate! database-url)
        (let [import-result (db/import-bundle! database-url bundle {:replace? true})
              verify-result (db/verify-bundle! database-url bundle)]
          (testing "import summary"
            (is (= 20 (:tables import-result)))
            (is (= 1 (get-in import-result [:row-counts "lemmas"])))
            (is (= 2 (get-in import-result [:row-counts "example_sentences"])))
            (is (= 8 (get-in import-result [:row-counts "lemma_pos_distractors"])))
            (is (= 4 (get-in import-result [:row-counts "example_sentence_distractor_assignments"]))))
          (testing "verify sees the same manifest"
            (is (= (:manifest-sha256 import-result)
                   (:manifest-sha256 verify-result)))))
        (finally
          (with-open [conn (jdbc/get-connection {:connection-uri jdbc-url})]
            (jdbc/execute! conn ["DROP SCHEMA IF EXISTS polish_lexicon CASCADE"])
            (jdbc/execute! conn ["DROP TABLE IF EXISTS schema_migrations"])))))))
