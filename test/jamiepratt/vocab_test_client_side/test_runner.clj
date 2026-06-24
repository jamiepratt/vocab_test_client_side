(ns jamiepratt.vocab-test-client-side.test-runner
  (:require
   [clojure.test :as test]
   [jamiepratt.vocab-test-client-side.api-test]
   [jamiepratt.vocab-test-client-side.scoring-test]))

(defn -main [& _]
  (let [{:keys [fail error]} (test/run-tests
                              'jamiepratt.vocab-test-client-side.api-test
                              'jamiepratt.vocab-test-client-side.scoring-test)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
