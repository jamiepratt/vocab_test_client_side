(ns jamiepratt.vocab-test-client-side.api-test
  (:require
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is testing]]
   [jamiepratt.vocab-test-client-side.api :as api]))

(deftest questions-endpoint-serves-vocabulary-json
  (let [response (api/handler {:request-method :get
                               :uri "/api/questions"
                               :headers {"origin" "http://localhost:8000"}})
        body (json/read-str (:body response))]
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
