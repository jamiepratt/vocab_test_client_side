(ns jamiepratt.vocab-test-client-side.db
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [migratus.core :as migratus]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs])
  (:import
   [java.math BigInteger]
   [java.net URI URLDecoder URLEncoder]
   [java.nio.charset StandardCharsets]
   [java.security MessageDigest]
   [java.sql Connection]
   [java.util UUID]
   [java.util.regex Pattern]
   [org.postgresql.copy CopyManager]
   [org.postgresql.core BaseConnection]))

(def schema-name "polish_lexicon")

(defn- fail [message data]
  (throw (ex-info message data)))

(defn- url-decode [s]
  (URLDecoder/decode s (.name StandardCharsets/UTF_8)))

(defn- url-encode [s]
  (URLEncoder/encode s (.name StandardCharsets/UTF_8)))

(def jdbc-query-param-aliases
  {"channel_binding" "channelBinding"})

(defn- normalize-jdbc-query-param [raw-param]
  (let [[raw-key raw-value] (str/split raw-param #"=" 2)
        key (get jdbc-query-param-aliases
                 (url-decode raw-key)
                 (url-decode raw-key))]
    (str (url-encode key)
         (when raw-value
           (str "=" raw-value)))))

(defn- normalize-jdbc-query-params [raw-query]
  (map normalize-jdbc-query-param (str/split raw-query #"&")))

(defn- host-for-jdbc [host]
  (cond
    (str/blank? host) (fail "DATABASE_URL is missing a host." {})
    (str/includes? host ":") (str "[" host "]")
    :else host))

(defn database-url->jdbc-url [database-url]
  (let [database-url (some-> database-url str/trim)]
    (cond
      (str/blank? database-url)
      (fail "DATABASE_URL is required." {})

      (str/starts-with? database-url "jdbc:postgresql://")
      database-url

      :else
      (let [uri (URI. database-url)
            scheme (.getScheme uri)]
        (when-not (#{"postgres" "postgresql"} scheme)
          (fail "DATABASE_URL must use postgres://, postgresql://, or jdbc:postgresql://."
                {:scheme scheme}))
        (let [[raw-user raw-password] (some-> (.getRawUserInfo uri)
                                              (str/split #":" 2))
              params (cond-> []
                       (.getRawQuery uri) (into (normalize-jdbc-query-params (.getRawQuery uri)))
                       raw-user (conj (str "user=" (url-encode (url-decode raw-user))))
                       raw-password (conj (str "password=" (url-encode (url-decode raw-password)))))
              authority (str (host-for-jdbc (.getHost uri))
                             (when (not= -1 (.getPort uri))
                               (str ":" (.getPort uri))))
              path (or (.getRawPath uri) "")]
          (str "jdbc:postgresql://" authority path
               (when (seq params)
                 (str "?" (str/join "&" params)))))))))

(defn migration-config [database-url]
  {:store :database
   :migration-dir "migrations"
   :tx-handles-ddl? true
   :db {:connection-uri (database-url->jdbc-url database-url)}})

(defn migrate! [database-url]
  (migratus/migrate (migration-config database-url)))

(defn rollback! [database-url]
  (migratus/rollback (migration-config database-url)))

(defn- canonical-file [file]
  (.getCanonicalFile (io/file file)))

(defn- bundle-file [bundle-dir relative-path]
  (let [relative-path (str relative-path)]
    (when (or (str/blank? relative-path)
              (.isAbsolute (io/file relative-path)))
      (fail "Bundle paths must be relative." {:path relative-path}))
    (let [root (.toPath (canonical-file bundle-dir))
          child (.toPath (canonical-file (io/file (.toFile root) relative-path)))]
      (when-not (.startsWith child root)
        (fail "Bundle path escapes bundle root." {:path relative-path}))
      (.toFile child))))

(defn file-sha256 [file]
  (let [digest (MessageDigest/getInstance "SHA-256")
        buffer (byte-array 8192)]
    (with-open [in (io/input-stream file)]
      (loop []
        (let [read-count (.read in buffer)]
          (when (pos? read-count)
            (.update digest buffer 0 read-count)
            (recur)))))
    (format "%064x" (BigInteger. 1 (.digest digest)))))

(defn read-manifest [bundle-dir]
  (let [root (canonical-file bundle-dir)]
    (when-not (.isDirectory root)
      (fail "Bundle directory does not exist." {:bundle-dir (str bundle-dir)}))
    (with-open [reader (io/reader (bundle-file root "manifest.json")
                                  :encoding "UTF-8")]
      (json/read reader :key-fn keyword))))

(defn- valid-ident! [value label]
  (let [value (str value)]
    (when-not (re-matches #"[A-Za-z_][A-Za-z0-9_]*" value)
      (fail (str "Invalid " label ".") {:value value}))
    value))

(defn- validate-table-entry! [entry]
  (doseq [field [:table :file :rows :sha256 :header]]
    (when-not (contains? entry field)
      (fail "Manifest table entry is missing a field."
            {:table (:table entry) :field field})))
  (valid-ident! (:table entry) "table name")
  (doseq [column (:header entry)]
    (valid-ident! column "column name"))
  (when-not (and (integer? (:rows entry))
                 (not (neg? (:rows entry))))
    (fail "Manifest row count must be a non-negative integer."
          {:table (:table entry) :rows (:rows entry)}))
  (when-not (re-matches #"[0-9a-f]{64}" (str (:sha256 entry)))
    (fail "Manifest SHA-256 must be lowercase hex."
          {:table (:table entry) :sha256 (:sha256 entry)}))
  entry)

(def legacy-example-distractor-columns
  #{"distractor_1_translation"
    "distractor_2_translation"
    "distractor_3_translation"
    "distractor_4_translation"})

(def normalized-table-headers
  {"example_sentences"
   ["example_sentence_id"
    "sentence"
    "sentence_translation"
    "surface_form_id"
    "lemma_id"
    "lemma_subtlex_pos_id"
    "word_translation"]
   "lemma_pos_distractors"
   ["lemma_pos_distractor_id"
    "lemma_subtlex_pos_id"
    "distractor_translation"
    "is_default"
    "import_order"]
   "example_sentence_distractor_assignments"
   ["example_sentence_id"
    "lemma_pos_distractor_id"]})

(def normalized-distractor-table-names
  (set (keys normalized-table-headers)))

(def required-distractor-count 4)

(defn- validate-example-sentence-header! [{:keys [table header]}]
  (when (= "example_sentences" table)
    (let [header-set (set header)
          legacy-columns (seq (filter header-set legacy-example-distractor-columns))]
      (when legacy-columns
        (fail "Manifest example_sentences uses legacy distractor columns."
              {:columns (vec legacy-columns)}))
      (when-not (contains? header-set "lemma_subtlex_pos_id")
        (fail "Manifest example_sentences is missing lemma_subtlex_pos_id."
              {:table table})))))

(defn- validate-normalized-table-header! [{:keys [table header]}]
  (when-let [expected (get normalized-table-headers table)]
    (when-not (= expected header)
      (fail "Manifest normalized table header is not the target schema."
            {:table table :expected expected :actual header}))))

(defn- validate-manifest-shape! [manifest]
  (when-not (= schema-name (:schema_name manifest))
    (fail "Manifest schema_name is not polish_lexicon."
          {:schema-name (:schema_name manifest)}))
  (when-not (= "utf-8" (some-> (:encoding manifest) str/lower-case))
    (fail "Manifest encoding must be utf-8." {:encoding (:encoding manifest)}))
  (when-not (= "\t" (:delimiter manifest))
    (fail "Manifest delimiter must be a tab." {:delimiter (:delimiter manifest)}))
  (when-not (true? (:header manifest))
    (fail "Manifest header must be true." {:header (:header manifest)}))
  (when-not (= "\\N" (:null_value manifest))
    (fail "Manifest null_value must be \\\\N." {:null-value (:null_value manifest)}))
  (when-not (seq (:tables manifest))
    (fail "Manifest tables must not be empty." {}))
  (doseq [entry (:tables manifest)]
    (validate-table-entry! entry)
    (validate-example-sentence-header! entry))
  (doseq [entry (:tables manifest)]
    (validate-normalized-table-header! entry))
  manifest)

(defn- tsv-header [file]
  (with-open [reader (io/reader file :encoding "UTF-8")]
    (if-let [line (.readLine reader)]
      (str/split line #"\t" -1)
      (fail "TSV file is empty." {:file (str file)}))))

(defn- parse-tsv-row [line]
  (let [length (count line)]
    (loop [index 0
           field []
           fields []
           quoted? false]
      (if (= index length)
        (conj fields (apply str field))
        (let [ch (.charAt line index)]
          (cond
            (and quoted? (= ch \") (< (inc index) length) (= (.charAt line (inc index)) \"))
            (recur (+ index 2) (conj field \") fields true)

            (and quoted? (= ch \"))
            (recur (inc index) field fields false)

            quoted?
            (recur (inc index) (conj field ch) fields true)

            (= ch \")
            (recur (inc index) field fields true)

            (= ch \tab)
            (recur (inc index) [] (conj fields (apply str field)) false)

            :else
            (recur (inc index) (conj field ch) fields false)))))))

(defn- table-rows [bundle-dir {:keys [file header]}]
  (with-open [reader (io/reader (bundle-file bundle-dir file) :encoding "UTF-8")]
    (.readLine reader)
    (doall
     (map #(zipmap header (parse-tsv-row %))
          (line-seq reader)))))

(defn- canonical-translation [value]
  (some-> value str/trim str/lower-case))

(defn- truthy? [value]
  (= "true" (some-> value str/trim str/lower-case)))

(defn- target-surface-count [sentence surface-form]
  (if (or (str/blank? sentence) (str/blank? surface-form))
    0
    (let [pattern (re-pattern (str "(?iu)(?<!\\p{L})"
                                   (Pattern/quote surface-form)
                                   "(?!\\p{L})"))]
      (count (re-seq pattern sentence)))))

(defn- validate-file-hash! [bundle-dir {:keys [file sha256]}]
  (let [actual-file (bundle-file bundle-dir file)]
    (when-not (.isFile actual-file)
      (fail "Bundle file is missing." {:file file}))
    (when-not (= sha256 (file-sha256 actual-file))
      (fail "Bundle file SHA-256 mismatch." {:file file :expected sha256}))
    actual-file))

(defn- validate-table-file! [bundle-dir entry]
  (let [file (validate-file-hash! bundle-dir entry)
        actual-header (tsv-header file)]
    (when-not (= (:header entry) actual-header)
      (fail "TSV header mismatch."
            {:table (:table entry)
             :expected (:header entry)
             :actual actual-header}))))

(defn- validate-lemma-pos-distractor-rows! [bundle-dir {:keys [table] :as entry}]
  (when (= "lemma_pos_distractors" table)
    (loop [seen #{}
           rows (table-rows bundle-dir entry)]
      (when-let [row (first rows)]
        (let [key [(get row "lemma_subtlex_pos_id")
                   (canonical-translation (get row "distractor_translation"))]]
          (when (contains? seen key)
            (fail "Duplicate lemma/POS distractor translation."
                  {:lemma-subtlex-pos-id (first key)
                   :distractor-translation (second key)}))
          (recur (conj seen key) (rest rows)))))))

(defn- validate-table-data! [bundle-dir entry]
  (validate-lemma-pos-distractor-rows! bundle-dir entry))

(defn- table-entry [manifest table]
  (some #(when (= table (:table %)) %) (:tables manifest)))

(defn- require-table-entry! [manifest table]
  (or (table-entry manifest table)
      (fail "Manifest is missing normalized distractor table." {:table table})))

(defn- validate-assignment-row! [examples-by-id distractors-by-id row]
  (let [example-id (get row "example_sentence_id")
        distractor-id (get row "lemma_pos_distractor_id")
        example (get examples-by-id example-id)
        distractor (get distractors-by-id distractor-id)]
    (when-not example
      (fail "Distractor assignment references a missing example sentence."
            {:example-sentence-id example-id}))
    (when-not distractor
      (fail "Distractor assignment references a missing lemma/POS distractor."
            {:lemma-pos-distractor-id distractor-id}))
    (when-not (= (get example "lemma_subtlex_pos_id")
                 (get distractor "lemma_subtlex_pos_id"))
      (fail "Distractor assignment does not match the example lemma/POS."
            {:example-sentence-id example-id
             :lemma-pos-distractor-id distractor-id}))))

(defn- validate-example-effective-distractors! [defaults-by-lemma-pos
                                                assignments-by-example
                                                distractors-by-id
                                                example]
  (let [example-id (get example "example_sentence_id")
        lemma-pos-id (get example "lemma_subtlex_pos_id")
        default-set (get defaults-by-lemma-pos lemma-pos-id #{})
        assigned-ids (get assignments-by-example example-id)
        assigned-set (set (map #(canonical-translation
                                 (get (distractors-by-id %) "distractor_translation"))
                               assigned-ids))
        effective-set (if (seq assigned-ids) assigned-set default-set)
        correct (canonical-translation (get example "word_translation"))]
    (when (< (count default-set) required-distractor-count)
      (fail "Example sentence lemma/POS has fewer than four default distractors."
            {:example-sentence-id example-id
             :lemma-subtlex-pos-id lemma-pos-id
             :default-distractors (count default-set)}))
    (when (and (seq assigned-ids) (= assigned-set default-set))
      (fail "Example sentence assignments duplicate the lemma/POS default distractors."
            {:example-sentence-id example-id}))
    (when (< (count effective-set) required-distractor-count)
      (fail "Example sentence has fewer than four effective distractors."
            {:example-sentence-id example-id
             :effective-distractors (count effective-set)}))
    (when (contains? effective-set correct)
      (fail "Example sentence effective distractors include the correct translation."
            {:example-sentence-id example-id
             :word-translation correct}))))

(defn- validate-example-target-surface! [surface-forms-by-id example]
  (when-let [surface-form (get-in surface-forms-by-id
                                  [(get example "surface_form_id") "surface_form"])]
    (let [occurrences (target-surface-count (get example "sentence") surface-form)]
      (when-not (= 1 occurrences)
        (fail "Example sentence target surface does not occur exactly once."
              {:example-sentence-id (get example "example_sentence_id")
               :surface-form surface-form
               :occurrences occurrences})))))

(defn- validate-normalized-distractor-data! [bundle-dir manifest]
  (when (some #(table-entry manifest %) normalized-distractor-table-names)
    (let [examples (table-rows bundle-dir (require-table-entry! manifest "example_sentences"))
          distractors (table-rows bundle-dir (require-table-entry! manifest "lemma_pos_distractors"))
          assignments (table-rows bundle-dir (require-table-entry! manifest "example_sentence_distractor_assignments"))
          surface-forms-by-id (some->> (table-entry manifest "surface_forms")
                                       (table-rows bundle-dir)
                                       (into {} (map (juxt #(get % "surface_form_id") identity))))
          examples-by-id (into {} (map (juxt #(get % "example_sentence_id") identity)) examples)
          distractors-by-id (into {} (map (juxt #(get % "lemma_pos_distractor_id") identity)) distractors)
          defaults-by-lemma-pos (reduce (fn [defaults row]
                                          (if (truthy? (get row "is_default"))
                                            (update defaults
                                                    (get row "lemma_subtlex_pos_id")
                                                    (fnil conj #{})
                                                    (canonical-translation (get row "distractor_translation")))
                                            defaults))
                                        {}
                                        distractors)
          assignments-by-example (reduce (fn [by-example row]
                                           (update by-example
                                                   (get row "example_sentence_id")
                                                   (fnil conj [])
                                                   (get row "lemma_pos_distractor_id")))
                                         {}
                                         assignments)]
      (doseq [row assignments]
        (validate-assignment-row! examples-by-id distractors-by-id row))
      (doseq [example examples]
        (validate-example-target-surface! surface-forms-by-id example)
        (validate-example-effective-distractors! defaults-by-lemma-pos
                                                 assignments-by-example
                                                 distractors-by-id
                                                 example)))))

(defn validate-bundle! [bundle-dir]
  (let [root (canonical-file bundle-dir)
        manifest-file (bundle-file root "manifest.json")
        manifest (validate-manifest-shape! (read-manifest root))]
    (doseq [entry (:tables manifest)]
      (validate-table-file! root entry)
      (validate-table-data! root entry))
    (validate-normalized-distractor-data! root manifest)
    (doseq [entry (:support_files manifest)]
      (validate-file-hash! root entry))
    (assoc manifest
           :bundle-dir root
           :manifest-sha256 (file-sha256 manifest-file))))

(defn load-order [manifest]
  (mapv :table (:tables manifest)))

(defn- qident [identifier]
  (str "\"" (str/replace (valid-ident! identifier "identifier") "\"" "\"\"") "\""))

(defn- qtable [table]
  (str (qident schema-name) "." (qident table)))

(defn- scalar-long [conn sql]
  (long (:c (jdbc/execute-one! conn [sql]
                               {:builder-fn rs/as-unqualified-lower-maps}))))

(defn- table-row-count [conn table]
  (scalar-long conn (str "SELECT count(*) AS c FROM " (qtable table))))

(defn db-row-counts [conn manifest]
  (into {}
        (map (fn [{:keys [table]}]
               [table (table-row-count conn table)]))
        (:tables manifest)))

(defn- expected-row-counts [manifest]
  (into {}
        (map (fn [{:keys [table rows]}] [table (long rows)]))
        (:tables manifest)))

(defn verify-row-counts! [conn manifest]
  (let [expected (expected-row-counts manifest)
        actual (db-row-counts conn manifest)
        mismatches (keep (fn [[table expected-count]]
                           (let [actual-count (get actual table)]
                             (when (not= expected-count actual-count)
                               {:table table
                                :expected expected-count
                                :actual actual-count})))
                         expected)]
    (when (seq mismatches)
      (fail "Imported row counts do not match manifest." {:mismatches mismatches}))
    actual))

(defn- verify-key-checks! [conn]
  (let [missing-refs (scalar-long
                      conn
                      (str "SELECT count(*) AS c "
                           "FROM " (qtable "example_sentences") " e "
                           "LEFT JOIN " (qtable "lemmas") " l ON l.lemma_id = e.lemma_id "
                           "LEFT JOIN " (qtable "surface_forms") " s ON s.surface_form_id = e.surface_form_id "
                           "WHERE l.lemma_id IS NULL OR s.surface_form_id IS NULL"))
        unlinked-targets (scalar-long
                          conn
                          (str "SELECT count(*) AS c "
                               "FROM " (qtable "example_sentences") " e "
                               "WHERE NOT EXISTS ("
                               "SELECT 1 FROM " (qtable "surface_form_lemma_links") " l "
                               "WHERE l.surface_form_id = e.surface_form_id "
                               "AND l.lemma_id = e.lemma_id)"))
        missing-lemma-pos-refs (scalar-long
                                conn
                                (str "SELECT count(*) AS c "
                                     "FROM " (qtable "example_sentences") " e "
                                     "LEFT JOIN " (qtable "lemma_subtlex_pos") " p "
                                     "ON p.lemma_subtlex_pos_id = e.lemma_subtlex_pos_id "
                                     "AND p.lemma_id = e.lemma_id "
                                     "WHERE p.lemma_subtlex_pos_id IS NULL"))
        unlinked-target-lemma-pos (scalar-long
                                   conn
                                   (str "SELECT count(*) AS c "
                                        "FROM " (qtable "example_sentences") " e "
                                        "WHERE NOT EXISTS ("
                                        "SELECT 1 FROM " (qtable "surface_form_lemma_links") " l "
                                        "WHERE l.surface_form_id = e.surface_form_id "
                                        "AND l.lemma_id = e.lemma_id "
                                        "AND l.lemma_subtlex_pos_id = e.lemma_subtlex_pos_id)"))
        mismatched-assignments (scalar-long
                                conn
                                (str "SELECT count(*) AS c "
                                     "FROM " (qtable "example_sentence_distractor_assignments") " a "
                                     "JOIN " (qtable "example_sentences") " e "
                                     "ON e.example_sentence_id = a.example_sentence_id "
                                     "JOIN " (qtable "lemma_pos_distractors") " d "
                                     "ON d.lemma_pos_distractor_id = a.lemma_pos_distractor_id "
                                     "WHERE d.lemma_subtlex_pos_id <> e.lemma_subtlex_pos_id"))
        examples-with-insufficient-defaults (scalar-long
                                             conn
                                             (str "WITH default_counts AS ("
                                                  "SELECT lemma_subtlex_pos_id, count(*) AS c "
                                                  "FROM " (qtable "lemma_pos_distractors") " "
                                                  "WHERE is_default "
                                                  "GROUP BY lemma_subtlex_pos_id) "
                                                  "SELECT count(*) AS c "
                                                  "FROM " (qtable "example_sentences") " e "
                                                  "LEFT JOIN default_counts d "
                                                  "ON d.lemma_subtlex_pos_id = e.lemma_subtlex_pos_id "
                                                  "WHERE coalesce(d.c, 0) < " required-distractor-count))
        examples-with-insufficient-effective-distractors
        (scalar-long
         conn
         (str "WITH assignment_counts AS ("
              "SELECT example_sentence_id, count(*) AS c "
              "FROM " (qtable "example_sentence_distractor_assignments") " "
              "GROUP BY example_sentence_id), "
              "default_counts AS ("
              "SELECT lemma_subtlex_pos_id, count(*) AS c "
              "FROM " (qtable "lemma_pos_distractors") " "
              "WHERE is_default "
              "GROUP BY lemma_subtlex_pos_id) "
              "SELECT count(*) AS c "
              "FROM " (qtable "example_sentences") " e "
              "LEFT JOIN assignment_counts a "
              "ON a.example_sentence_id = e.example_sentence_id "
              "LEFT JOIN default_counts d "
              "ON d.lemma_subtlex_pos_id = e.lemma_subtlex_pos_id "
              "WHERE CASE WHEN coalesce(a.c, 0) > 0 THEN a.c ELSE coalesce(d.c, 0) END < "
              required-distractor-count))
        default-duplicate-assignments
        (scalar-long
         conn
         (str "WITH assigned_sets AS ("
              "SELECT e.example_sentence_id, "
              "array_agg(lower(d.distractor_translation) ORDER BY lower(d.distractor_translation)) AS assigned_keys "
              "FROM " (qtable "example_sentences") " e "
              "JOIN " (qtable "example_sentence_distractor_assignments") " a "
              "ON a.example_sentence_id = e.example_sentence_id "
              "JOIN " (qtable "lemma_pos_distractors") " d "
              "ON d.lemma_pos_distractor_id = a.lemma_pos_distractor_id "
              "GROUP BY e.example_sentence_id), "
              "default_sets AS ("
              "SELECT lemma_subtlex_pos_id, "
              "array_agg(lower(distractor_translation) ORDER BY lower(distractor_translation)) AS default_keys "
              "FROM " (qtable "lemma_pos_distractors") " "
              "WHERE is_default "
              "GROUP BY lemma_subtlex_pos_id) "
              "SELECT count(*) AS c "
              "FROM assigned_sets assigned "
              "JOIN " (qtable "example_sentences") " e "
              "ON e.example_sentence_id = assigned.example_sentence_id "
              "JOIN default_sets defaults "
              "ON defaults.lemma_subtlex_pos_id = e.lemma_subtlex_pos_id "
              "WHERE assigned.assigned_keys = defaults.default_keys"))
        correct-translation-distractors
        (scalar-long
         conn
         (str "WITH assigned_examples AS ("
              "SELECT DISTINCT example_sentence_id "
              "FROM " (qtable "example_sentence_distractor_assignments") "), "
              "effective_distractors AS ("
              "SELECT e.example_sentence_id, e.word_translation, d.distractor_translation "
              "FROM " (qtable "example_sentences") " e "
              "JOIN " (qtable "example_sentence_distractor_assignments") " a "
              "ON a.example_sentence_id = e.example_sentence_id "
              "JOIN " (qtable "lemma_pos_distractors") " d "
              "ON d.lemma_pos_distractor_id = a.lemma_pos_distractor_id "
              "UNION ALL "
              "SELECT e.example_sentence_id, e.word_translation, d.distractor_translation "
              "FROM " (qtable "example_sentences") " e "
              "JOIN " (qtable "lemma_pos_distractors") " d "
              "ON d.lemma_subtlex_pos_id = e.lemma_subtlex_pos_id "
              "AND d.is_default "
              "WHERE NOT EXISTS ("
              "SELECT 1 FROM assigned_examples assigned "
              "WHERE assigned.example_sentence_id = e.example_sentence_id)) "
              "SELECT count(*) AS c "
              "FROM effective_distractors "
              "WHERE lower(btrim(word_translation)) = lower(btrim(distractor_translation))"))]
    (when (pos? missing-refs)
      (fail "Example sentences have missing lemma/surface references."
            {:missing-references missing-refs}))
    (when (pos? unlinked-targets)
      (fail "Example sentence lemma/surface pairs are not linked."
            {:unlinked-targets unlinked-targets}))
    (when (pos? missing-lemma-pos-refs)
      (fail "Example sentences have missing lemma/POS references."
            {:missing-lemma-pos-references missing-lemma-pos-refs}))
    (when (pos? unlinked-target-lemma-pos)
      (fail "Example sentence surface/lemma/POS triples are not linked."
            {:unlinked-target-lemma-pos unlinked-target-lemma-pos}))
    (when (pos? mismatched-assignments)
      (fail "Example sentence distractor assignments use a different lemma/POS."
            {:mismatched-assignments mismatched-assignments}))
    (when (pos? examples-with-insufficient-defaults)
      (fail "Example sentence lemma/POS defaults have too few distractors."
            {:examples-with-insufficient-defaults examples-with-insufficient-defaults
             :minimum required-distractor-count}))
    (when (pos? examples-with-insufficient-effective-distractors)
      (fail "Example sentences have too few effective distractors."
            {:examples-with-insufficient-effective-distractors
             examples-with-insufficient-effective-distractors
             :minimum required-distractor-count}))
    (when (pos? default-duplicate-assignments)
      (fail "Example sentence distractor assignments duplicate default sets."
            {:default-duplicate-assignments default-duplicate-assignments}))
    (when (pos? correct-translation-distractors)
      (fail "Example sentence effective distractors include correct translations."
            {:correct-translation-distractors correct-translation-distractors}))))

(defn- nonempty-target-tables [conn manifest]
  (->> (:tables manifest)
       (keep (fn [{:keys [table]}]
               (let [rows (table-row-count conn table)]
                 (when (pos? rows)
                   {:table table :rows rows}))))
       vec))

(defn- truncate-target-tables! [conn manifest]
  (let [tables (map (comp qtable :table) (:tables manifest))]
    (when (seq tables)
      (jdbc/execute! conn [(str "TRUNCATE TABLE " (str/join ", " tables))]))))

(defn- base-connection ^BaseConnection [^Connection conn]
  (if (instance? BaseConnection conn)
    conn
    (.unwrap conn BaseConnection)))

(defn- copy-sql [{:keys [table header]}]
  (str "COPY " (qtable table)
       " (" (str/join ", " (map qident header)) ") "
       "FROM STDIN WITH (FORMAT csv, HEADER true, DELIMITER E'\\t', NULL '\\N')"))

(defn- copy-table! [^CopyManager copy-manager bundle-dir entry]
  (with-open [in (io/input-stream (bundle-file bundle-dir (:file entry)))]
    (.copyIn copy-manager (copy-sql entry) in)))

(defn- copy-tables! [conn manifest]
  (let [copy-manager (CopyManager. (base-connection conn))]
    (doseq [entry (:tables manifest)]
      (copy-table! copy-manager (:bundle-dir manifest) entry))))

(defn- record-import! [conn manifest row-counts]
  (jdbc/execute-one!
   conn
   [(str "INSERT INTO " (qtable "import_manifests") " "
         "(manifest_sha256, bundle_name, source_git_commit_sha, row_counts) "
         "VALUES (?, ?, ?, CAST(? AS jsonb)) "
         "ON CONFLICT (manifest_sha256) DO UPDATE SET "
         "bundle_name = EXCLUDED.bundle_name, "
         "source_git_commit_sha = EXCLUDED.source_git_commit_sha, "
         "row_counts = EXCLUDED.row_counts, "
         "imported_at = now()")
    (:manifest-sha256 manifest)
    (:bundle_name manifest)
    (:source_git_commit_sha manifest)
    (json/write-str row-counts)]))

(defn- verify-import-record! [conn manifest]
  (let [latest (:manifest_sha256
                (jdbc/execute-one!
                 conn
                 [(str "SELECT manifest_sha256 FROM " (qtable "import_manifests") " "
                       "ORDER BY imported_at DESC, import_manifest_id DESC LIMIT 1")]
                 {:builder-fn rs/as-unqualified-lower-maps}))]
    (when-not (= (:manifest-sha256 manifest) latest)
      (fail "Latest import manifest hash does not match bundle."
            {:expected (:manifest-sha256 manifest) :actual latest}))))

(defn import-bundle! [database-url bundle-dir {:keys [replace?]}]
  (let [manifest (validate-bundle! bundle-dir)
        jdbc-url (database-url->jdbc-url database-url)]
    (with-open [conn (jdbc/get-connection {:connection-uri jdbc-url})]
      (jdbc/with-transaction [tx conn]
        (when-not replace?
          (when-let [tables (seq (nonempty-target-tables tx manifest))]
            (fail "Target tables already contain data; rerun with --replace."
                  {:tables tables})))
        (when replace?
          (truncate-target-tables! tx manifest))
        (copy-tables! tx manifest)
        (let [row-counts (verify-row-counts! tx manifest)]
          (verify-key-checks! tx)
          (record-import! tx manifest row-counts)
          {:manifest-sha256 (:manifest-sha256 manifest)
           :tables (count (:tables manifest))
           :row-counts row-counts})))))

(defn verify-bundle! [database-url bundle-dir]
  (let [manifest (validate-bundle! bundle-dir)
        jdbc-url (database-url->jdbc-url database-url)]
    (with-open [conn (jdbc/get-connection {:connection-uri jdbc-url})]
      (let [row-counts (verify-row-counts! conn manifest)]
        (verify-key-checks! conn)
        (verify-import-record! conn manifest)
        {:manifest-sha256 (:manifest-sha256 manifest)
         :tables (count (:tables manifest))
         :row-counts row-counts}))))

(def answer-event-required-fields
  [:anonymous-session-id
   :test-block-id
   :target-lemma-id
   :candidate-rank
   :lemma-inventory-stratum
   :lemma-rank
   :item-type
   :choice-count
   :guess-rate
   :selected-answer
   :correct
   :response-time-ms
   :attention-check-status])

(defn- event-field [event field]
  (if (contains? event field)
    (get event field)
    (get event (name field))))

(defn- blank-value? [value]
  (or (nil? value)
      (and (string? value) (str/blank? value))))

(defn- require-present! [event field]
  (when-not (or (contains? event field)
                (contains? event (name field)))
    (fail (str "Answer event is missing " (name field) ".")
          {:field field}))
  (let [value (event-field event field)]
    (when (blank-value? value)
      (fail (str "Answer event is missing " (name field) ".")
            {:field field}))
    value))

(defn- require-uuid! [event field]
  (let [value (require-present! event field)]
    (try
      (UUID/fromString (str value))
      (catch IllegalArgumentException _
        (fail (str "Answer event " (name field) " must be a UUID.")
              {:field field :value value})))
    value))

(defn- require-positive-int! [event field]
  (let [value (require-present! event field)]
    (when-not (and (integer? value) (pos? value))
      (fail (str "Answer event " (name field) " must be a positive integer.")
            {:field field :value value}))
    value))

(defn- optional-positive-int! [event field]
  (let [value (event-field event field)]
    (when (some? value)
      (when-not (and (integer? value) (pos? value))
        (fail (str "Answer event " (name field) " must be a positive integer.")
              {:field field :value value})))
    value))

(defn- require-nonnegative-int! [event field]
  (let [value (require-present! event field)]
    (when-not (and (integer? value) (not (neg? value)))
      (fail (str "Answer event " (name field) " must be a non-negative integer.")
            {:field field :value value}))
    value))

(defn- require-probability! [event field]
  (let [value (require-present! event field)]
    (when-not (and (number? value) (<= 0 value 1))
      (fail (str "Answer event " (name field) " must be between 0 and 1.")
            {:field field :value value}))
    value))

(defn- optional-number! [event field]
  (let [value (event-field event field)]
    (when (some? value)
      (when-not (number? value)
        (fail (str "Answer event " (name field) " must be numeric.")
              {:field field :value value})))
    value))

(defn- require-boolean! [event field]
  (let [value (require-present! event field)]
    (when-not (instance? Boolean value)
      (fail (str "Answer event " (name field) " must be boolean.")
            {:field field :value value}))
    value))

(defn- require-nonblank-text! [event field]
  (str (require-present! event field)))

(defn validate-answer-event! [event]
  (doseq [field answer-event-required-fields]
    (require-present! event field))
  (let [surface-difficulty-rank (optional-positive-int! event :surface-difficulty-rank)
        calibrated-difficulty (optional-number! event :calibrated-difficulty)]
    (when (and (nil? surface-difficulty-rank)
               (nil? calibrated-difficulty))
      (fail "Answer event is missing surface-difficulty-rank or calibrated-difficulty."
            {:field :surface-difficulty-rank}))
    {:anonymous-session-id (require-uuid! event :anonymous-session-id)
     :test-block-id (require-nonblank-text! event :test-block-id)
     :target-lemma-id (require-positive-int! event :target-lemma-id)
     :target-surface-form-id (optional-positive-int! event :target-surface-form-id)
     :candidate-rank (require-positive-int! event :candidate-rank)
     :lemma-inventory-stratum (require-positive-int! event :lemma-inventory-stratum)
     :lemma-rank (require-positive-int! event :lemma-rank)
     :surface-difficulty-rank surface-difficulty-rank
     :calibrated-difficulty calibrated-difficulty
     :item-type (require-nonblank-text! event :item-type)
     :choice-count (require-positive-int! event :choice-count)
     :guess-rate (require-probability! event :guess-rate)
     :selected-answer (require-nonblank-text! event :selected-answer)
     :correct (require-boolean! event :correct)
     :response-time-ms (require-nonnegative-int! event :response-time-ms)
     :attention-check-status (require-nonblank-text! event :attention-check-status)}))

(defn- answer-event-insert-sql []
  (str "INSERT INTO " (qtable "answer_events") " "
       "(anonymous_session_id, test_block_id, target_lemma_id, target_surface_form_id, "
       "candidate_rank, lemma_inventory_stratum, lemma_rank, surface_difficulty_rank, "
       "calibrated_difficulty, item_type, choice_count, guess_rate, selected_answer, "
       "correct, response_time_ms, attention_check_status) "
       "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
       "RETURNING answer_event_id, anonymous_session_id, submitted_at, test_block_id, "
       "target_lemma_id, target_surface_form_id, candidate_rank, lemma_inventory_stratum, "
       "lemma_rank, surface_difficulty_rank, calibrated_difficulty, item_type, "
       "choice_count, guess_rate, selected_answer, correct, response_time_ms, "
       "attention_check_status"))

(defn- answer-event-params [event]
  [(UUID/fromString (str (:anonymous-session-id event)))
   (:test-block-id event)
   (:target-lemma-id event)
   (:target-surface-form-id event)
   (:candidate-rank event)
   (:lemma-inventory-stratum event)
   (:lemma-rank event)
   (:surface-difficulty-rank event)
   (:calibrated-difficulty event)
   (:item-type event)
   (:choice-count event)
   (:guess-rate event)
   (:selected-answer event)
   (:correct event)
   (:response-time-ms event)
   (:attention-check-status event)])

(defn record-answer-event!
  ([event]
   (let [database-url (or (System/getenv "DATABASE_URL")
                          (fail "DATABASE_URL is required." {:status 503}))
         jdbc-url (database-url->jdbc-url database-url)]
     (with-open [conn (jdbc/get-connection {:connection-uri jdbc-url})]
       (record-answer-event! conn event))))
  ([conn event]
   (let [event (validate-answer-event! event)]
     (jdbc/execute-one! conn
                        (into [(answer-event-insert-sql)]
                              (answer-event-params event))
                        {:builder-fn rs/as-unqualified-kebab-maps}))))

(defn- sentence-question-candidates-sql []
  (str "WITH candidate_examples AS ("
       "SELECT e.example_sentence_id, "
       "e.sentence, "
       "e.sentence_translation, "
       "s.surface_form AS target_surface, "
       "e.surface_form_id AS target_surface_form_id, "
       "e.lemma_id, "
       "e.lemma_subtlex_pos_id, "
       "p.lemma, "
       "p.subtlex_pos, "
       "l.total_frequency_sn_sum_rank AS lemma_inventory_rank, "
       "r.surface_avg_zipf_freq_sn_rank AS surface_difficulty_rank, "
       "e.word_translation AS correct_translation, "
       "(SELECT count(*) FROM " (qtable "surface_form_lemma_links") " link "
       "WHERE link.surface_form_id = e.surface_form_id "
       "AND link.lemma_id = e.lemma_id) AS lemma_pos_link_count "
       "FROM " (qtable "example_sentences") " e "
       "JOIN " (qtable "surface_forms") " s "
       "ON s.surface_form_id = e.surface_form_id "
       "JOIN " (qtable "lemmas") " l "
       "ON l.lemma_id = e.lemma_id "
       "JOIN " (qtable "lemma_subtlex_pos") " p "
       "ON p.lemma_subtlex_pos_id = e.lemma_subtlex_pos_id "
       "JOIN " (qtable "surface_form_lemma_frequency_ranks") " r "
       "ON r.surface_form_id = e.surface_form_id "
       "AND r.lemma_id = e.lemma_id "
       "WHERE r.surface_avg_zipf_freq_sn_rank >= ? "
       "ORDER BY r.surface_avg_zipf_freq_sn_rank, e.example_sentence_id "
       "LIMIT ?) "
       "SELECT candidate_examples.*, "
       "'default' AS distractor_source, "
       "d.distractor_translation, "
       "d.import_order AS distractor_order "
       "FROM candidate_examples "
       "JOIN " (qtable "lemma_pos_distractors") " d "
       "ON d.lemma_subtlex_pos_id = candidate_examples.lemma_subtlex_pos_id "
       "AND d.is_default "
       "UNION ALL "
       "SELECT candidate_examples.*, "
       "'assignment' AS distractor_source, "
       "d.distractor_translation, "
       "d.import_order AS distractor_order "
       "FROM candidate_examples "
       "JOIN " (qtable "example_sentence_distractor_assignments") " a "
       "ON a.example_sentence_id = candidate_examples.example_sentence_id "
       "JOIN " (qtable "lemma_pos_distractors") " d "
       "ON d.lemma_pos_distractor_id = a.lemma_pos_distractor_id "
       "ORDER BY surface_difficulty_rank, example_sentence_id, distractor_source, distractor_order"))

(defn sentence-question-candidate-rows [{:keys [surface-rank-start candidate-limit]}]
  (let [database-url (or (System/getenv "DATABASE_URL")
                         (fail "DATABASE_URL is required." {:status 503}))
        jdbc-url (database-url->jdbc-url database-url)]
    (with-open [conn (jdbc/get-connection {:connection-uri jdbc-url})]
      (jdbc/execute! conn
                     [(sentence-question-candidates-sql)
                      surface-rank-start
                      candidate-limit]
                     {:builder-fn rs/as-unqualified-kebab-maps}))))

(defn- database-url-env []
  (or (System/getenv "DATABASE_URL")
      (fail "DATABASE_URL is required." {})))

(defn- parse-import-args [args]
  (let [bundle-dir (first args)
        flags (set (rest args))]
    (when-not bundle-dir
      (fail "Import requires a bundle directory." {}))
    (when-not (every? #{"--replace"} flags)
      (fail "Unknown import flag." {:flags flags}))
    {:bundle-dir bundle-dir
     :replace? (contains? flags "--replace")}))

(defn- print-result! [verb result]
  (println (format "%s %s tables; manifest %s"
                   verb
                   (:tables result)
                   (:manifest-sha256 result))))

(defn -main [& args]
  (try
    (case (first args)
      "migrate"
      (do
        (migrate! (database-url-env))
        (println "Migrations applied."))

      "rollback"
      (do
        (rollback! (database-url-env))
        (println "Rolled back latest migration."))

      "import"
      (let [{:keys [bundle-dir replace?]} (parse-import-args (rest args))]
        (print-result! "Imported"
                       (import-bundle! (database-url-env)
                                       bundle-dir
                                       {:replace? replace?})))

      "verify"
      (let [bundle-dir (second args)]
        (when-not bundle-dir
          (fail "Verify requires a bundle directory." {}))
        (print-result! "Verified"
                       (verify-bundle! (database-url-env) bundle-dir)))

      (fail "Usage: clojure -M:db migrate|rollback|import <bundle> [--replace]|verify <bundle>"
            {:args args}))
    (catch Throwable t
      (binding [*out* *err*]
        (println (or (ex-message t) (str t))))
      (System/exit 1))))
