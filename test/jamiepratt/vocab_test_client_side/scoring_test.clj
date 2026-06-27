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
    (is (= 0 (:adjusted-estimate result)))
    (is (= :B1 (:ceiling-band result)))
    (is (= "You used \"don't know\" honestly. This estimate is probably accurate or slightly conservative."
           (:honesty-note result)))))

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
