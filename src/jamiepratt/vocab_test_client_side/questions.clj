(ns jamiepratt.vocab-test-client-side.questions
  (:require
   [clojure.string :as str])
  (:import
   [java.util.regex Pattern]))

(def sentence-block-size 80)

(def required-distractor-count 4)

(def item-type "sentence-context-lemma")

(def level-priors
  {"absolute-beginner" {:level "pre-a1" :surface-rank-start 1}
   "pre-a1" {:level "pre-a1" :surface-rank-start 1}
   "a1" {:level "A1" :surface-rank-start 401}
   "a2" {:level "A2" :surface-rank-start 1001}
   "b1" {:level "B1" :surface-rank-start 2001}
   "b2" {:level "B2" :surface-rank-start 4001}
   "c1" {:level "C1" :surface-rank-start 8001}
   "c2" {:level "C2" :surface-rank-start 8001}})

(defn- parse-long-param [value default]
  (if (str/blank? value)
    default
    (parse-long value)))

(defn block-request [params]
  (let [raw-level (str/lower-case (str/trim (or (get params "level") "pre-a1")))
        prior (get level-priors raw-level (get level-priors "pre-a1"))
        block (max 0 (parse-long-param (get params "block") 0))
        surface-rank-start (+ (:surface-rank-start prior)
                              (* block sentence-block-size))]
    {:level (:level prior)
     :requested-level raw-level
     :level-role "starting-prior"
     :measurement-unit "lemma"
     :block block
     :block-size sentence-block-size
     :surface-rank-start surface-rank-start
     :surface-rank-end (dec (+ surface-rank-start sentence-block-size))
     :candidate-limit (* sentence-block-size 4)}))

(defn- canonical-translation [value]
  (some-> value str str/trim str/lower-case))

(defn- item-id [row]
  (str "example-sentence:" (:example-sentence-id row)))

(defn- invalid-item [row reason data]
  (merge {:item-id (item-id row)
          :example-sentence-id (:example-sentence-id row)
          :reason reason}
         data))

(defn- target-pattern [target-surface]
  (re-pattern (str "(?iu)(?<!\\p{L})"
                   (Pattern/quote target-surface)
                   "(?!\\p{L})")))

(defn- highlight-span [sentence target-surface]
  (when-not (or (str/blank? sentence) (str/blank? target-surface))
    (let [matcher (re-matcher (target-pattern target-surface) sentence)]
      (loop [spans []]
        (if (.find matcher)
          (recur (conj spans {:start (.start matcher)
                              :end (.end matcher)}))
          (when (= 1 (count spans))
            (first spans)))))))

(defn- rows-by-effective-distractor-source [rows]
  (let [assigned (filter #(= "assignment" (:distractor-source %)) rows)]
    (if (seq assigned)
      assigned
      (filter #(= "default" (:distractor-source %)) rows))))

(defn- ordered-distractors [rows]
  (->> rows
       (sort-by (juxt #(or (:distractor-order %) Long/MAX_VALUE)
                      #(canonical-translation (:distractor-translation %))))
       (mapv :distractor-translation)))

(defn- duplicate-correct-distractors [correct distractors]
  (let [correct-key (canonical-translation correct)]
    (filterv #(= correct-key (canonical-translation %)) distractors)))

(defn- inventory-stratum [lemma-inventory-rank]
  (inc (quot (dec (long lemma-inventory-rank)) 1000)))

(defn- sentence-item [rows]
  (let [row (first rows)
        span (highlight-span (:sentence row) (:target-surface row))
        effective-distractors (ordered-distractors
                               (rows-by-effective-distractor-source rows))
        duplicate-correct (duplicate-correct-distractors
                           (:correct-translation row)
                           effective-distractors)]
    (cond
      (not= 1 (:lemma-pos-link-count row))
      {:invalid (invalid-item row "nondeterministic-lemma-pos-mapping"
                              {:link-count (:lemma-pos-link-count row)})}

      (nil? span)
      {:invalid (invalid-item row "missing-or-ambiguous-highlight-span"
                              {:target-surface (:target-surface row)})}

      (< (count effective-distractors) required-distractor-count)
      {:invalid (invalid-item row "insufficient-effective-distractors"
                              {:distractor-count (count effective-distractors)})}

      (seq duplicate-correct)
      {:invalid (invalid-item row "duplicate-correct-answer-in-distractors"
                              {:duplicates duplicate-correct})}

      :else
      (let [choice-count (inc (count effective-distractors))
            stratum-id (inventory-stratum (:lemma-inventory-rank row))]
        {:item {:item-id (item-id row)
                :sentence (:sentence row)
                :target-surface (:target-surface row)
                :target-surface-form-id (:target-surface-form-id row)
                :highlight-span span
                :lemma-id (:lemma-id row)
                :lemma-pos-id (:lemma-subtlex-pos-id row)
                :lemma-inventory-rank (:lemma-inventory-rank row)
                :surface-difficulty-rank (:surface-difficulty-rank row)
                :inventory-stratum stratum-id
                :fixed-stratum stratum-id
                :correct-translation (:correct-translation row)
                :distractors effective-distractors
                :item-type item-type
                :choice-count choice-count
                :guess-rate (double (/ 1 choice-count))}}))))

(defn sentence-block [params rows]
  (let [request (block-request params)
        built (->> rows
                   (group-by :example-sentence-id)
                   vals
                   (sort-by (comp (juxt :surface-difficulty-rank
                                        :example-sentence-id)
                                  first))
                   (map sentence-item))
        items (->> built
                   (keep :item)
                   (take sentence-block-size)
                   vec)
        invalid-items (->> built
                           (keep :invalid)
                           vec)]
    (assoc request
           :items items
           :invalid-items invalid-items)))
