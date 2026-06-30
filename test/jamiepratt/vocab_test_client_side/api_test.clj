(ns jamiepratt.vocab-test-client-side.api-test
  (:require
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is testing]]
   [jamiepratt.vocab-test-client-side.api :as api]
   [jamiepratt.vocab-test-client-side.db :as db]))

(defn- json-body [response]
  (json/read-str (:body response)))

(defn- json-request-body [body]
  (java.io.StringReader. (json/write-str body)))

(def valid-answer-event
  {"anonymous-session-id" "123e4567-e89b-12d3-a456-426614174000"
   "test-block-id" "adaptive-block-0"
   "target-lemma-id" 11
   "target-surface-form-id" 22
   "candidate-rank" 33
   "lemma-inventory-stratum" 1
   "lemma-rank" 44
   "surface-difficulty-rank" 55
   "item-type" "sentence-context-lemma"
   "choice-count" 5
   "guess-rate" 0.2
   "selected-answer" "cat"
   "correct" true
   "response-time-ms" 1234
   "attention-check-status" "not-attention-check"})

(def base-sentence-row
  {:example-sentence-id 101
   :sentence "Kot pije wodę."
   :sentence-translation "The cat drinks water."
   :target-surface "Kot"
   :target-surface-form-id 202
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

(deftest legacy-questions-endpoint-is-not-product-wired
  (let [response (api/handler {:request-method :get
                               :uri "/api/questions"
                               :headers {"origin" "http://localhost:8000"}})
        body (json-body response)]
    (testing "HTTP contract"
      (is (= 404 (:status response)))
      (is (= "application/json; charset=utf-8"
             (get-in response [:headers "Content-Type"])))
      (is (= "http://localhost:8000"
             (get-in response [:headers "Access-Control-Allow-Origin"]))))
    (testing "legacy dictionary questions are not exposed as the product route"
      (is (= {"error" "Not found"} body)))))

(deftest e2e-dev-origin-is-allowed
  (let [response (api/handler {:request-method :get
                               :uri "/healthz"
                               :headers {"origin" "http://localhost:8001"}})]
    (is (= 200 (:status response)))
    (is (= "http://localhost:8001"
           (get-in response [:headers "Access-Control-Allow-Origin"])))))

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
              "sentence-translation" "The cat drinks water."
              "target-surface" "Kot"
              "target-surface-form-id" 202
              "highlight-span" {"start" 0 "end" 3}
              "lemma-id" 11
              "lemma-pos-id" 111
              "lemma-inventory-rank" 42
              "surface-difficulty-rank" 17
              "lemma-inventory-stratum" 1
              "correct-translation" "cat"
              "distractors" ["dog" "bird" "fish" "tree"]
              "item-type" "sentence-context-lemma"
              "choice-count" 5
              "guess-rate" 0.2}
             (select-keys item
                          ["item-id" "sentence" "sentence-translation"
                           "target-surface" "target-surface-form-id"
                           "highlight-span"
                           "lemma-id" "lemma-pos-id" "lemma-inventory-rank"
                           "surface-difficulty-rank"
                           "lemma-inventory-stratum"
                           "correct-translation" "distractors" "item-type"
                           "choice-count" "guess-rate"])))
      (is (not (contains? item "inventory-stratum")))
      (is (not (contains? item "fixed-stratum"))))))

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

(deftest sentence-question-block-c2-starts-inside-available-inventory
  (let [seen-request (atom nil)
        handler (api/make-handler
                 {:sentence-question-rows
                  (fn [request]
                    (reset! seen-request request)
                    [])})
        response (handler {:request-method :get
                           :uri "/api/sentence-question-blocks"
                           :query-string "level=C2&block=0"
                           :headers {}})
        body (json-body response)]
    (is (= 200 (:status response)))
    (is (= "C2" (get body "level")))
    (is (= 8001 (get body "surface-rank-start")))
    (is (= 8080 (get body "surface-rank-end")))
    (is (= 8001 (:surface-rank-start @seen-request)))))

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

(deftest answer-event-post-stores-anonymous-calibration-data
  (let [recorded (atom nil)
        handler (api/make-handler
                 {:answer-event-writer
                  (fn [event]
                    (reset! recorded (db/validate-answer-event! event))
                    {:answer-event-id 7})})
        response (handler {:request-method :post
                           :uri "/api/answer-events"
                           :headers {"origin" "http://localhost:8000"
                                     "content-type" "application/json"}
                           :body (json-request-body valid-answer-event)})
        body (json-body response)]
    (testing "HTTP contract"
      (is (= 201 (:status response)))
      (is (= "application/json; charset=utf-8"
             (get-in response [:headers "Content-Type"])))
      (is (= "http://localhost:8000"
             (get-in response [:headers "Access-Control-Allow-Origin"])))
      (is (= "GET, POST, OPTIONS"
             (get-in response [:headers "Access-Control-Allow-Methods"])))
      (is (= {"answer-event-id" 7} body)))
    (testing "validated anonymous answer-event payload"
      (is (= "123e4567-e89b-12d3-a456-426614174000"
             (:anonymous-session-id @recorded)))
      (is (= 11 (:target-lemma-id @recorded)))
      (is (= 22 (:target-surface-form-id @recorded)))
      (is (= 1 (:lemma-inventory-stratum @recorded)))
      (is (= "sentence-context-lemma" (:item-type @recorded)))
      (is (= "not-attention-check" (:attention-check-status @recorded))))))

(deftest answer-event-post-rejects-obsolete-inventory-stratum-field
  (let [calls (atom 0)
        handler (api/make-handler
                 {:answer-event-writer
                  (fn [event]
                    (swap! calls inc)
                    (db/validate-answer-event! event)
                    {:answer-event-id 1})})
        response (handler {:request-method :post
                           :uri "/api/answer-events"
                           :headers {"origin" "http://localhost:8000"
                                     "content-type" "application/json"}
                           :body (json-request-body
                                  (-> valid-answer-event
                                      (dissoc "lemma-inventory-stratum")
                                      (assoc "inventory-stratum" 1)))})
        body (json-body response)]
    (is (= 400 (:status response)))
    (is (= "Answer event is missing lemma-inventory-stratum."
           (get body "error")))
    (is (= 1 @calls))))

(deftest answer-event-post-rejects-malformed-payloads
  (let [calls (atom 0)
        handler (api/make-handler
                 {:answer-event-writer
                  (fn [event]
                    (swap! calls inc)
                    (db/validate-answer-event! event)
                    {:answer-event-id 1})})]
    (testing "invalid JSON does not reach the writer"
      (let [response (handler {:request-method :post
                               :uri "/api/answer-events"
                               :headers {"origin" "http://localhost:8000"}
                               :body (java.io.StringReader. "{")})
            body (json-body response)]
        (is (= 400 (:status response)))
        (is (= "Malformed JSON" (get body "error")))
        (is (zero? @calls))))
    (testing "missing required metadata is rejected as a bad request"
      (let [response (handler {:request-method :post
                               :uri "/api/answer-events"
                               :headers {"origin" "http://localhost:8000"}
                               :body (json-request-body
                                      (dissoc valid-answer-event
                                              "lemma-inventory-stratum"))})
            body (json-body response)]
        (is (= 400 (:status response)))
        (is (= "Answer event is missing lemma-inventory-stratum."
               (get body "error")))
        (is (= 1 @calls))))))
