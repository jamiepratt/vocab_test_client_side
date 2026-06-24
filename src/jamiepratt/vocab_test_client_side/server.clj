(ns jamiepratt.vocab-test-client-side.server
  (:require
   [jamiepratt.vocab-test-client-side.api :as api]
   [ring.adapter.jetty :as jetty]))

(defn parse-port [value]
  (if value
    (parse-long value)
    8080))

(defn -main [& _]
  (let [port (parse-port (System/getenv "PORT"))]
    (println "Starting API on port" port)
    (jetty/run-jetty api/handler {:port port
                                  :join? true})))
