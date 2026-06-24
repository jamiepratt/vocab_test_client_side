(ns jamiepratt.vocab-test-client-side.questions
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]))

(def questions-resource
  "jamiepratt/vocab_test_client_side/questions.edn")

(defn load-questions []
  (with-open [reader (io/reader (io/resource questions-resource))]
    (edn/read (java.io.PushbackReader. reader))))

(defonce questions
  (delay (load-questions)))

(defn all []
  @questions)
