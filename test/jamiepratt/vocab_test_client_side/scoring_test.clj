(ns jamiepratt.vocab-test-client-side.scoring-test
  (:require
   [clojure.test :refer [deftest is]]
   [jamiepratt.vocab-test-client-side.data :as data]
   [jamiepratt.vocab-test-client-side.questions :as questions]
   [jamiepratt.vocab-test-client-side.scoring :as scoring]))

(defn answer-for [index question result]
  {:question-index index
   :word (:word question)
   :word-class (:word-class question)
   :band (:band question)
   :selected (case result
               :correct (:correct question)
               :wrong (first (:wrong question))
               :dk "don't know")
   :correct (:correct question)
   :result result})

(defn block-answers [block-id correct-count]
  (mapv (fn [index]
          {:question-index index
           :adaptive-block-id block-id
           :item-type "sentence-context-lemma"
           :band :B1
           :result (if (< index correct-count) :correct :dk)})
        (range 80)))

(defn evidence-questions [block-id]
  (mapv (fn [index]
          {:question-index index
           :adaptive-block-id block-id
           :item-type "sentence-context-lemma"
           :band :B1
           :word (str (name block-id) "-" index)
           :correct (str "meaning-" index)})
        (range 80)))

(defn lemma-answer
  ([stratum result]
   (lemma-answer stratum result nil))
  ([stratum result opts]
   (merge {:adaptive-block-id :pre-a1
           :item-type "sentence-context-lemma"
           :inventory-stratum stratum
           :lemma-rank (inc (* 1000 (dec stratum)))
           :choice-count 5
           :guess-rate 0.2
           :result result}
          opts)))

(deftest reports-stable-lemma-likely-ranges
  (let [answers (vec (concat (repeat 16 (lemma-answer 1 :correct))
                             (repeat 4 (lemma-answer 1 :dk))
                             (repeat 8 (lemma-answer 2 :correct))
                             (repeat 12 (lemma-answer 2 :wrong))))
        first-result (scoring/summarize-results [] answers)
        second-result (scoring/summarize-results [] answers)
        {:keys [lower upper]} (:likely-range first-result)]
    (is (= (select-keys first-result [:lemma-estimate :likely-range])
           (select-keys second-result [:lemma-estimate :likely-range])))
    (is (= "recognized Polish lemmas" (:estimate-unit first-result)))
    (is (<= 0 lower (:lemma-estimate first-result) upper data/lemma-inventory-size))
    (is (< (:lemma-estimate first-result) data/lemma-inventory-size))))

(deftest adjusts-for-forced-choice-guessing
  (let [binary-result (scoring/summarize-results
                       []
                       (vec (repeat 20 (lemma-answer 1 :correct
                                                     {:guess-rate 0}))))
        forced-choice-result (scoring/summarize-results
                              []
                              (vec (repeat 20 (lemma-answer 1 :correct
                                                            {:choice-count 5
                                                             :guess-rate 0.2}))))]
    (is (< (:lemma-estimate forced-choice-result)
           (:lemma-estimate binary-result)))
    (is (< (get-in forced-choice-result [:likely-range :lower])
           (get-in binary-result [:likely-range :lower])))))

(deftest reports-borderline-level-band-when-range-crosses-boundary
  (let [answers (vec (concat (repeat 24 (lemma-answer 1 :correct
                                                      {:guess-rate 0}))
                             (repeat 16 (lemma-answer 1 :dk))))
        result (scoring/summarize-results [] answers)]
    (is (= "borderline: Absolute beginner / pre-A1 to A1"
           (:level-band result)))))

(deftest gates-live-estimate-until-enough-real-answers
  (let [early-result (scoring/summarize-results
                      []
                      (vec (repeat 29 (lemma-answer 1 :correct
                                                    {:guess-rate 0}))))
        ready-result (scoring/summarize-results
                      []
                      (vec (repeat 30 (lemma-answer 1 :correct
                                                    {:guess-rate 0}))))]
    (is (= {:ready? false
            :label "Still calibrating"}
           (:live-estimate early-result)))
    (is (= true (get-in ready-result [:live-estimate :ready?])))
    (is (re-matches #"Current estimate: about [0-9,]+ recognized Polish lemmas"
                    (get-in ready-result [:live-estimate :label])))
    (is (re-matches #"Likely range: [0-9,]+-[0-9,]+"
                    (get-in ready-result [:live-estimate :range-label])))))

(deftest final-floor-result-uses-non-shaming-low-copy
  (let [decision (scoring/adaptive-block-decision
                  (data/starting-block :absolute-beginner)
                  (block-answers :pre-a1 1))
        result (scoring/summarize-results
                []
                (vec (repeat 80 (lemma-answer 1 :dk)))
                decision)]
    (is (= "under 200" (:estimate-label result)))
    (is (= {:lower 0 :upper 200} (:likely-range result)))
    (is (= "Absolute beginner / pre-A1" (:level-band result)))
    (is (< (:lemma-estimate result) 200))))

(deftest summarizes-low-score-results
  (let [questions (questions/all)
        answers (mapv (fn [index question]
                        (answer-for index question
                                    (case index
                                      0 :correct
                                      1 :wrong
                                      :dk)))
                      (range)
                      questions)
        result (scoring/summarize-results questions answers)]
    (is (= 80 (:total result)))
    (is (= 1 (:correct result)))
    (is (= 1 (:wrong result)))
    (is (= 78 (:dk result)))
    (is (= 1 (:accuracy-pct result)))
    (is (= "recognized Polish lemmas" (:estimate-unit result)))
    (is (<= 0
            (get-in result [:likely-range :lower])
            (:lemma-estimate result)
            (get-in result [:likely-range :upper])
            data/lemma-inventory-size))))

(deftest routes-blocks-from-thresholds
  (let [block {:id :a1
               :lower-block-id :pre-a1
               :higher-block-id :a2}]
    (is (= :route-lower
           (:action (scoring/adaptive-block-decision block (block-answers :a1 12)))))
    (is (= :report
           (:action (scoring/adaptive-block-decision block (block-answers :a1 40)))))
    (is (= :route-higher
           (:action (scoring/adaptive-block-decision block (block-answers :a1 68)))))))

(deftest adaptive-block-metadata-selects-starting-spans
  (let [floor-block (data/starting-block :absolute-beginner)
        higher-block (data/adaptive-block (:higher-block-id floor-block))
        a1-block (data/starting-block :a1)]
    (is (= 80 (:real-item-count floor-block)))
    (is (= {:level "absolute-beginner" :block 0}
           (:request floor-block)))
    (is (= [1 500]
           [(:surface-rank-start floor-block)
            (:surface-rank-end floor-block)]))
    (is (:floor? floor-block))
    (is (data/overlapping-blocks? floor-block higher-block))
    (is (= {:level "a1" :block 0}
           (:request a1-block)))
    (is (= [1 2000]
           [(:surface-rank-start a1-block)
            (:surface-rank-end a1-block)]))))

(deftest lowest-block-reports-floor-range
  (let [decision (scoring/adaptive-block-decision
                  (data/starting-block :absolute-beginner)
                  (block-answers :pre-a1 1))]
    (is (= :report-floor (:action decision)))
    (is (nil? (:next-block-id decision)))
    (is (= "under 200" (:estimate-label decision)))))

(deftest summarizes-combined-real-evidence-across-blocks
  (let [questions (into (evidence-questions :pre-a1)
                        (evidence-questions :pre-a1-plus))
        control-answer {:question-index 160
                        :adaptive-block-id :pre-a1-plus
                        :item-type "attention-check"
                        :vocabulary-evidence? false
                        :band :B1
                        :result :correct}
        answers (conj (into (block-answers :pre-a1 80)
                            (block-answers :pre-a1-plus 40))
                      control-answer)
        result (scoring/summarize-results questions answers)]
    (is (= 160 (:total result)))
    (is (= 160 (:answered result)))
    (is (= 120 (:correct result)))
    (is (= 40 (:dk result)))))

(deftest lemma-estimate-combines-evidence-across-blocks
  (let [first-block (vec (repeat 20 (lemma-answer 1 :correct
                                                  {:adaptive-block-id :pre-a1
                                                   :guess-rate 0})))
        next-block (vec (repeat 20 (lemma-answer 2 :correct
                                                 {:adaptive-block-id :pre-a1-plus
                                                  :guess-rate 0})))
        first-result (scoring/summarize-results [] first-block)
        combined-result (scoring/summarize-results [] (into first-block next-block))]
    (is (= 40 (:answered combined-result)))
    (is (> (:lemma-estimate combined-result)
           (:lemma-estimate first-result)))
    (is (<= 0
            (get-in combined-result [:likely-range :lower])
            (:lemma-estimate combined-result)
            (get-in combined-result [:likely-range :upper])
            data/lemma-inventory-size))))
