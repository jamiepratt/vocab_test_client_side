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
    (validate-table-entry! entry))
  manifest)

(defn- tsv-header [file]
  (with-open [reader (io/reader file :encoding "UTF-8")]
    (if-let [line (.readLine reader)]
      (str/split line #"\t" -1)
      (fail "TSV file is empty." {:file (str file)}))))

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

(defn validate-bundle! [bundle-dir]
  (let [root (canonical-file bundle-dir)
        manifest-file (bundle-file root "manifest.json")
        manifest (validate-manifest-shape! (read-manifest root))]
    (doseq [entry (:tables manifest)]
      (validate-table-file! root entry))
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
                               "AND l.lemma_id = e.lemma_id)"))]
    (when (pos? missing-refs)
      (fail "Example sentences have missing lemma/surface references."
            {:missing-references missing-refs}))
    (when (pos? unlinked-targets)
      (fail "Example sentence lemma/surface pairs are not linked."
            {:unlinked-targets unlinked-targets}))))

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
