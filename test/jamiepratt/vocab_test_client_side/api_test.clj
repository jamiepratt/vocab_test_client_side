(ns jamiepratt.vocab-test-client-side.api-test
  (:require
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is testing]]
   [jamiepratt.vocab-test-client-side.api :as api]))

(defn- json-body [response]
  (json/read-str (:body response)))

(def base-sentence-row
  {:example-sentence-id 101
   :sentence "Kot pije wodę."
   :target-surface "Kot"
   :lemma-id 11
   :lemma-subtlex-pos-id 111
   :lemma "kot"
   :subtlex-pos "subst"
   :lemma-inventory-rank 42
   :surface-difficulty-rank 17
   :correct-translation "cat"
   :lemma-pos-link-count 1})

(defn- sentence-rows
  ([distractor-source distractors]
   (sentence-rows base-sentence-row distractor-source distractors))
  ([row distractor-source distractors]
   (map-indexed (fn [index distractor]
                  (assoc row
                         :distractor-source distractor-source
                         :distractor-translation distractor
                         :distractor-order (inc index)))
                distractors)))

(defn- valid-sentence-rows [row]
  (sentence-rows row "default" ["dog" "bird" "fish" "tree"]))

(deftest questions-endpoint-serves-vocabulary-json
  (let [response (api/handler {:request-method :get
                               :uri "/api/questions"
                               :headers {"origin" "http://localhost:8000"}})
        body (json-body response)]
    (testing "HTTP contract"
      (is (= 200 (:status response)))
      (is (= "application/json; charset=utf-8"
             (get-in response [:headers "Content-Type"])))
      (is (= "http://localhost:8000"
             (get-in response [:headers "Access-Control-Allow-Origin"]))))
    (testing "question payload"
      (is (= 80 (count body)))
      (is (= {"word" "woda"
              "word-class" "noun"
              "band" "B1"
              "correct" "water"
              "wrong" ["fire" "air" "earth"]}
             (select-keys (first body)
                          ["word" "word-class" "band" "correct" "wrong"]))))))

(deftest sentence-question-block-serves-default-distractors
  (let [handler (api/make-handler
                 {:sentence-question-rows
                  (fn [_]
                    (sentence-rows "default" ["dog" "bird" "fish" "tree"]))})
        response (handler {:request-method :get
                           :uri "/api/sentence-question-blocks"
                           :headers {"origin" "http://localhost:8000"}})
        body (json-body response)
        item (first (get body "items"))]
    (testing "HTTP contract"
      (is (= 200 (:status response)))
      (is (= "application/json; charset=utf-8"
             (get-in response [:headers "Content-Type"])))
      (is (= "http://localhost:8000"
             (get-in response [:headers "Access-Control-Allow-Origin"]))))
    (testing "sentence block contract"
      (is (= "pre-a1" (get body "level")))
      (is (= 0 (get body "block")))
      (is (= [] (get body "invalid-items")))
      (is (= {"item-id" "example-sentence:101"
              "sentence" "Kot pije wodę."
              "target-surface" "Kot"
              "highlight-span" {"start" 0 "end" 3}
              "lemma-id" 11
              "lemma-pos-id" 111
              "lemma-inventory-rank" 42
              "surface-difficulty-rank" 17
              "fixed-stratum" 1
              "correct-translation" "cat"
              "distractors" ["dog" "bird" "fish" "tree"]
              "item-type" "sentence-context-lemma"
              "choice-count" 5
              "guess-rate" 0.2}
             (select-keys item
                          ["item-id" "sentence" "target-surface" "highlight-span"
                           "lemma-id" "lemma-pos-id" "lemma-inventory-rank"
                           "surface-difficulty-rank" "fixed-stratum"
                           "correct-translation" "distractors" "item-type"
                           "choice-count" "guess-rate"]))))))

(deftest sentence-question-block-prefers-assigned-distractors
  (let [handler (api/make-handler
                 {:sentence-question-rows
                  (fn [_]
                    (concat
                     (sentence-rows "default" ["dog" "bird" "fish" "tree"])
                     (sentence-rows "assignment" ["chair" "cup" "road" "car"])))})
        response (handler {:request-method :get
                           :uri "/api/sentence-question-blocks"
                           :headers {}})
        item (first (get (json-body response) "items"))]
    (is (= 200 (:status response)))
    (is (= ["chair" "cup" "road" "car"]
           (get item "distractors")))))

(deftest sentence-question-block-rejects-duplicate-correct-distractor
  (let [handler (api/make-handler
                 {:sentence-question-rows
                  (fn [_]
                    (sentence-rows "default" ["dog" "CAT" "fish" "tree"]))})
        response (handler {:request-method :get
                           :uri "/api/sentence-question-blocks"
                           :headers {}})
        body (json-body response)]
    (is (= 200 (:status response)))
    (is (= [] (get body "items")))
    (is (= ["duplicate-correct-answer-in-distractors"]
           (mapv #(get % "reason") (get body "invalid-items"))))))

(deftest sentence-question-block-reports-invalid-rows-before-serving
  (let [ambiguous-row (assoc base-sentence-row
                             :example-sentence-id 102
                             :sentence "Kot widzi kot."
                             :surface-difficulty-rank 18)
        nondeterministic-row (assoc base-sentence-row
                                    :example-sentence-id 103
                                    :surface-difficulty-rank 19
                                    :lemma-pos-link-count 2)
        valid-row (assoc base-sentence-row
                         :example-sentence-id 104
                         :sentence "Kot śpi."
                         :surface-difficulty-rank 20)
        handler (api/make-handler
                 {:sentence-question-rows
                  (fn [_]
                    (concat
                     (valid-sentence-rows ambiguous-row)
                     (valid-sentence-rows nondeterministic-row)
                     (valid-sentence-rows valid-row)))})
        response (handler {:request-method :get
                           :uri "/api/sentence-question-blocks"
                           :headers {}})
        body (json-body response)]
    (is (= ["example-sentence:104"]
           (mapv #(get % "item-id") (get body "items"))))
    (is (= ["missing-or-ambiguous-highlight-span"
            "nondeterministic-lemma-pos-mapping"]
           (mapv #(get % "reason") (get body "invalid-items"))))))

(deftest sentence-question-block-level-and-block-are-starting-priors
  (let [seen-request (atom nil)
        handler (api/make-handler
                 {:sentence-question-rows
                  (fn [request]
                    (reset! seen-request request)
                    [])})
        response (handler {:request-method :get
                           :uri "/api/sentence-question-blocks"
                           :query-string "level=C1&block=2"
                           :headers {}})
        body (json-body response)]
    (is (= 200 (:status response)))
    (is (= "C1" (get body "level")))
    (is (= 2 (get body "block")))
    (is (= "starting-prior" (get body "level-role")))
    (is (= "lemma" (get body "measurement-unit")))
    (is (= 8161 (get body "surface-rank-start")))
    (is (= 8240 (get body "surface-rank-end")))
    (is (= 8161 (:surface-rank-start @seen-request)))
    (is (= 320 (:candidate-limit @seen-request)))))

(deftest sentence-question-block-allows-absolute-beginner-alias
  (let [handler (api/make-handler
                 {:sentence-question-rows (fn [_] [])})
        response (handler {:request-method :get
                           :uri "/api/sentence-question-blocks"
                           :query-string "level=absolute-beginner"
                           :headers {}})
        body (json-body response)]
    (is (= "pre-a1" (get body "level")))
    (is (= "absolute-beginner" (get body "requested-level")))))

(deftest sentence-question-block-reports-unavailable-database
  (let [handler (api/make-handler
                 {:sentence-question-rows
                  (fn [_]
                    (throw (ex-info "DATABASE_URL is required." {:status 503})))})
        response (handler {:request-method :get
                           :uri "/api/sentence-question-blocks"
                           :headers {}})
        body (json-body response)]
    (is (= 503 (:status response)))
    (is (= "DATABASE_URL is required." (get body "error")))))

(deftest sentence-question-block-serves-eighty-item-shape
  (let [rows (mapcat (fn [index]
                       (valid-sentence-rows
                        (assoc base-sentence-row
                               :example-sentence-id (inc index)
                               :lemma-id (inc index)
                               :lemma-subtlex-pos-id (+ 1000 index)
                               :lemma-inventory-rank (inc index)
                               :surface-difficulty-rank (inc index))))
                     (range 85))
        handler (api/make-handler
                 {:sentence-question-rows (fn [_] rows)})
        response (handler {:request-method :get
                           :uri "/api/sentence-question-blocks"
                           :headers {}})
        body (json-body response)
        items (get body "items")]
    (is (= 80 (count items)))
    (is (= 80 (count (set (map #(get % "item-id") items)))))
    (is (every? #(= "sentence-context-lemma" (get % "item-type")) items))
    (is (every? #(= 5 (get % "choice-count")) items))))
