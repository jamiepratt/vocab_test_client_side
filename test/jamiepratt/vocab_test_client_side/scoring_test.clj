(ns jamiepratt.vocab-test-client-side.scoring-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [jamiepratt.vocab-test-client-side.data :as data]
   [jamiepratt.vocab-test-client-side.scoring :as scoring]))

(defn answer-for [index question result]
  {:question-index index
   :adaptive-block-id (:adaptive-block-id question)
   :item-type (:item-type question)
   :word (:word question)
   :band (:band question)
   :selected (case result
               :correct (:correct question)
               :wrong "wrong answer"
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

(defn scored-answers [block-id stratum correct-count wrong-count dk-count]
  (vec (concat
        (repeat correct-count
                (lemma-answer stratum :correct {:adaptive-block-id block-id}))
        (repeat wrong-count
                (lemma-answer stratum :wrong {:adaptive-block-id block-id}))
        (repeat dk-count
                (lemma-answer stratum :dk {:adaptive-block-id block-id})))))

(defn assert-bounded-estimate [result]
  (is (<= 0
          (get-in result [:likely-range :lower])
          (:lemma-estimate result)
          (get-in result [:likely-range :upper])
          data/lemma-inventory-size)))

(defn assert-summary-counts
  [result {:keys [total correct wrong dk accuracy-pct]}]
  (is (= total (:total result)))
  (is (= total (:answered result)))
  (is (= correct (:correct result)))
  (is (= wrong (:wrong result)))
  (is (= dk (:dk result)))
  (is (= accuracy-pct (:accuracy-pct result)))
  (is (= (+ wrong dk) (count (:review-answers result))))
  (is (every? #(not= :correct (:result %)) (:review-answers result)))
  (assert-bounded-estimate result))

(def zero-band-stats
  {:B1 {:answered 0 :correct 0 :wrong 0 :dk 0 :proportion 0 :pct 0}
   :B2 {:answered 0 :correct 0 :wrong 0 :dk 0 :proportion 0 :pct 0}
   :B3 {:answered 0 :correct 0 :wrong 0 :dk 0 :proportion 0 :pct 0}
   :B4 {:answered 0 :correct 0 :wrong 0 :dk 0 :proportion 0 :pct 0}
   :B5 {:answered 0 :correct 0 :wrong 0 :dk 0 :proportion 0 :pct 0}
   :B6 {:answered 0 :correct 0 :wrong 0 :dk 0 :proportion 0 :pct 0}})

(def pending-live-estimate
  {:ready? false
   :label (str "Not enough questions answered to make an estimate yet, answer at least "
               data/live-estimate-min-real-answers
               " questions and estimate is updated live as you answer each question.")})

(defn posterior-statuses [result]
  (into {} (map (juxt :id :status) (:posterior-strata result))))

(deftest summarizes-primary-answer-distributions-from-input-counts
  (doseq [{:keys [label correct wrong dk] :as scenario}
          [{:label "all don't know"
            :correct 0
            :wrong 0
            :dk 80
            :accuracy-pct 0}
           {:label "all correct"
            :correct 80
            :wrong 0
            :dk 0
            :accuracy-pct 100}
           {:label "all wrong"
            :correct 0
            :wrong 80
            :dk 0
            :accuracy-pct 0}
           {:label "half correct"
            :correct 40
            :wrong 0
            :dk 40
            :accuracy-pct 50}
           {:label "20 percent correct"
            :correct 16
            :wrong 0
            :dk 64
            :accuracy-pct 20}
           {:label "75 percent correct"
            :correct 60
            :wrong 0
            :dk 20
            :accuracy-pct 75}
           {:label "85 percent correct"
            :correct 68
            :wrong 0
            :dk 12
            :accuracy-pct 85}]]
    (testing label
      (assert-summary-counts
       (scoring/summarize-results
        []
        (scored-answers :pre-a1 1 correct wrong dk))
       (assoc scenario :total 80)))))

(deftest accuracy-percent-rounds-from-answer-ratios
  (doseq [{:keys [label correct wrong dk expected]}
          [{:label "one of three rounds down"
            :correct 1
            :wrong 0
            :dk 2
            :expected 33}
           {:label "two of three rounds up"
            :correct 2
            :wrong 0
            :dk 1
            :expected 67}
           {:label "one of two is exact half"
            :correct 1
            :wrong 0
            :dk 1
            :expected 50}
           {:label "zero correct stays zero"
            :correct 0
            :wrong 1
            :dk 2
            :expected 0}]]
    (testing label
      (let [result (scoring/summarize-results
                    []
                    (scored-answers :pre-a1 1 correct wrong dk))]
        (is (= expected (:accuracy-pct result)))
        (is (= (+ correct wrong dk) (:total result)))
        (assert-bounded-estimate result)))))

(deftest ignores-non-vocabulary-control-answers
  (let [real-answers (scored-answers :pre-a1 1 10 5 15)
        control-answers [{:adaptive-block-id :pre-a1
                          :item-type "attention-check"
                          :vocabulary-evidence? false
                          :result :correct}
                         {:adaptive-block-id :pre-a1
                          :item-type "attention-check"
                          :vocabulary-evidence? false
                          :result :wrong}
                         {:adaptive-block-id :pre-a1
                          :item-type "attention-check"
                          :vocabulary-evidence? false
                          :result :dk}]
        result (scoring/summarize-results
                []
                (into real-answers control-answers))]
    (assert-summary-counts result
                           {:total 30
                            :correct 10
                            :wrong 5
                            :dk 15
                            :accuracy-pct 33})))

(deftest handles-empty-answer-sets
  (let [result (scoring/summarize-results [] [])]
    (assert-summary-counts result
                           {:total 0
                            :correct 0
                            :wrong 0
                            :dk 0
                            :accuracy-pct 0})
    (is (= [] (:review-answers result)))
    (is (= "Absolute beginner / pre-A1" (:level-band result)))
    (is (= {:ready? false
            :label (str "Not enough questions answered to make an estimate yet, answer at least "
                        data/live-estimate-min-real-answers
                        " questions and estimate is updated live as you answer each question.")}
           (:live-estimate result)))))

(deftest estimates-rise-with-more-correct-evidence
  (let [correct-counts [0 16 40 60 68 80]
        estimates (mapv (fn [correct]
                          (:lemma-estimate
                           (scoring/summarize-results
                            []
                            (scored-answers :pre-a1 1 correct 0 (- 80 correct)))))
                        correct-counts)]
    (is (apply <= estimates))))

(deftest wrong-answers-are-not-scored-like-dont-know-answers
  (let [all-dk-result (scoring/summarize-results
                       []
                       (scored-answers :pre-a1 1 0 0 80))
        all-wrong-result (scoring/summarize-results
                          []
                          (scored-answers :pre-a1 1 0 80 0))]
    (assert-bounded-estimate all-dk-result)
    (assert-bounded-estimate all-wrong-result)
    (is (<= (:lemma-estimate all-wrong-result)
            (:lemma-estimate all-dk-result)))
    (is (= 0 (:accuracy-pct all-dk-result) (:accuracy-pct all-wrong-result)))
    (is (= 80 (count (:review-answers all-dk-result))))
    (is (= 80 (count (:review-answers all-wrong-result))))))

(deftest characterizes-empty-summary-output
  (is (= {:likely-range {:lower 0 :upper 0}
          :posterior-strata []
          :level-band "Absolute beginner / pre-A1"
          :dk 0
          :live-estimate pending-live-estimate
          :wrong 0
          :total 0
          :review-answers []
          :estimate-label nil
          :lemma-estimate 0
          :answered 0
          :accuracy-pct 0
          :estimate-unit "recognized Polish lemmas"
          :scoring-model-version "latent-guessing-v1"
          :guessing-posterior {:mean 0.0659783741987188
                               :lower 0.006172839506172839
                               :upper 0.29012345679012347}
          :adaptive-decision nil
          :correct 0
          :band-stats zero-band-stats}
         (scoring/summarize-results [] []))))

(deftest characterizes-small-mixed-summary-output
  (let [questions (evidence-questions :pre-a1)
        answers (mapv answer-for
                      (range)
                      questions
                      [:correct :correct :wrong :dk :correct])]
    (is (= {:likely-range {:lower 208 :upper 906}
            :posterior-strata [{:mean 0.5833333916245692
                                :lower 0.2082294264339152
                                :upper 0.9064837905236908
                                :id 1
                                :status :observed
                                :width 1000}]
            :level-band "borderline: Absolute beginner / pre-A1 to A1"
            :dk 1
            :live-estimate pending-live-estimate
            :wrong 1
            :total 5
            :review-answers [{:question-index 2
                              :adaptive-block-id :pre-a1
                              :item-type "sentence-context-lemma"
                              :word "pre-a1-2"
                              :band :B1
                              :selected "wrong answer"
                              :correct "meaning-2"
                              :result :wrong}
                             {:question-index 3
                              :adaptive-block-id :pre-a1
                              :item-type "sentence-context-lemma"
                              :word "pre-a1-3"
                              :band :B1
                              :selected "don't know"
                              :correct "meaning-3"
                              :result :dk}]
            :estimate-label nil
            :lemma-estimate 583
            :answered 5
            :accuracy-pct 60
            :estimate-unit "recognized Polish lemmas"
            :scoring-model-version "latent-guessing-v1"
            :guessing-posterior {:mean 0.1424991469627994
                                 :lower 0.006172839506172839
                                 :upper 0.4012345679012346}
            :adaptive-decision nil
            :correct 3
            :band-stats (assoc zero-band-stats
                               :B1 {:answered 5
                                    :correct 3
                                    :wrong 1
                                    :dk 1
                                    :proportion 3/5
                                    :pct 60})}
           (scoring/summarize-results questions answers)))))

(deftest characterizes-live-ready-all-correct-summary-output
  (let [questions (evidence-questions :pre-a1)
        answers (mapv answer-for
                      (range)
                      questions
                      (repeat 30 :correct))]
    (is (= {:likely-range {:lower 919 :upper 999}
            :posterior-strata [{:mean 0.982163758438128
                                :lower 0.9189526184538653
                                :upper 0.9987531172069826
                                :id 1
                                :status :observed
                                :width 1000}]
            :level-band "A1"
            :dk 0
            :live-estimate {:ready? true
                            :label "Current estimate: about 982 recognized Polish lemmas"
                            :range-label "Likely range: 919-999"}
            :wrong 0
            :total 30
            :review-answers []
            :estimate-label nil
            :lemma-estimate 982
            :answered 30
            :accuracy-pct 100
            :estimate-unit "recognized Polish lemmas"
            :scoring-model-version "latent-guessing-v1"
            :guessing-posterior {:mean 0.06597837419871878
                                 :lower 0.006172839506172839
                                 :upper 0.29012345679012347}
            :adaptive-decision nil
            :correct 30
            :band-stats (assoc zero-band-stats
                               :B1 {:answered 30
                                    :correct 30
                                    :wrong 0
                                    :dk 0
                                    :proportion 1
                                    :pct 100})}
           (scoring/summarize-results questions answers)))))

(deftest characterizes-adaptive-route-higher-decision-output
  (is (= {:request {:level "a1" :block 0}
          :real-item-count 80
          :surface-rank-end 2000
          :next-block-id :a2
          :surface-rank-start 1
          :estimate-label nil
          :label "A1"
          :id :a1
          :accuracy-pct 85
          :action :route-higher
          :correct-rate 17/20
          :lower-block-id :pre-a1
          :correct 68
          :higher-block-id :a2}
         (scoring/adaptive-block-decision
          (data/adaptive-block :a1)
          (scored-answers :a1 1 68 0 12)))))

(deftest latent-guessing-keeps-dont-know-sessions-low-guess
  (let [result (scoring/summarize-results
                []
                (scored-answers :pre-a1 1 20 0 60))]
    (is (< (get-in result [:guessing-posterior :mean]) 0.2))
    (is (> (:lemma-estimate result) 200))
    (is (= scoring/scoring-model-version (:scoring-model-version result)))))

(deftest latent-guessing-treats-random-looking-wrongs-as-guessing
  (let [random-result (scoring/summarize-results
                       []
                       (scored-answers :pre-a1 1 16 64 0))]
    (is (> (get-in random-result [:guessing-posterior :mean]) 0.65))
    (is (< (:lemma-estimate random-result) 100))))

(deftest mixed-wrong-and-dont-know-estimates-between-clear-cases
  (let [no-guess-result (scoring/summarize-results
                         []
                         (scored-answers :pre-a1 1 16 0 64))
        mixed-result (scoring/summarize-results
                      []
                      (scored-answers :pre-a1 1 16 32 32))
        random-result (scoring/summarize-results
                       []
                       (scored-answers :pre-a1 1 16 64 0))]
    (is (< (:lemma-estimate random-result)
           (:lemma-estimate mixed-result)
           (:lemma-estimate no-guess-result)))))

(deftest random-high-start-does-not-get-lower-prior-credit
  (let [result (scoring/summarize-results
                []
                (scored-answers :b2 5 16 64 0))]
    (is (= [5] (mapv :id (:posterior-strata result))))
    (is (= {5 :observed} (posterior-statuses result)))
    (is (< (:lemma-estimate result) 1000))))

(deftest high-pass-high-start-credits-lower-strata-as-assumed-known
  (let [result (scoring/summarize-results
                []
                (scored-answers :b2 5 80 0 0))]
    (is (= [1 2 3 4 5] (mapv :id (:posterior-strata result))))
    (is (= {1 :assumed-known-lower
            2 :assumed-known-lower
            3 :assumed-known-lower
            4 :assumed-known-lower
            5 :observed}
           (posterior-statuses result)))
    (is (> (:lemma-estimate result) 4500))))

(deftest out-of-range-strata-are-capped-for-vocabulary-estimates
  (let [result (scoring/summarize-results
                []
                (scored-answers :c2-plus 12 80 0 0))]
    (is (= [1 2 3 4 5 6 7 8 9 10]
           (mapv :id (:posterior-strata result))))
    (is (<= (:lemma-estimate result) data/lemma-inventory-size))))

(deftest lower-anchor-routing-happens-once-and-avoids-completed-loops
  (let [moderate-b2 (scored-answers :b2 5 40 0 40)
        moderate-b1 (into moderate-b2
                          (scored-answers :b1 4 40 0 40))
        low-b2 (scored-answers :b2 5 12 0 68)
        high-b1 (into low-b2
                      (scored-answers :b1 4 68 0 12))]
    (is (= :route-lower
           (:action (scoring/adaptive-block-decision
                     (data/adaptive-block :b2)
                     moderate-b2
                     []))))
    (is (= :report
           (:action (scoring/adaptive-block-decision
                     (data/adaptive-block :b1)
                     moderate-b1
                     [{:id :b2}]))))
    (let [decision (scoring/adaptive-block-decision
                    (data/adaptive-block :b1)
                    high-b1
                    [{:id :b2}])]
      (is (= :report (:action decision)))
      (is (nil? (:next-block-id decision))))))

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

(deftest does-not-force-guessing-penalty-without-wrong-evidence
  (let [binary-result (scoring/summarize-results
                       []
                       (vec (repeat 20 (lemma-answer 1 :correct
                                                     {:guess-rate 0}))))
        forced-choice-result (scoring/summarize-results
                              []
                              (vec (repeat 20 (lemma-answer 1 :correct
                                                            {:choice-count 5
                                                             :guess-rate 0.2}))))]
    (is (<= (:lemma-estimate forced-choice-result)
            (:lemma-estimate binary-result)))
    (is (<= (- (:lemma-estimate binary-result)
               (:lemma-estimate forced-choice-result))
            25))))

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
            :label (str "Not enough questions answered to make an estimate yet, answer at least "
                        data/live-estimate-min-real-answers
                        " questions and estimate is updated live as you answer each question.")}
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
  (let [questions (evidence-questions :pre-a1)
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
