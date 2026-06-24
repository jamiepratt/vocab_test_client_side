(ns jamiepratt.vocab-test-client-side.scoring-test
  (:require
   [clojure.test :refer [deftest is]]
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
