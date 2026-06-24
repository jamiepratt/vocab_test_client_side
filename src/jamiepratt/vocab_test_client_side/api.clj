(ns jamiepratt.vocab-test-client-side.api
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [jamiepratt.vocab-test-client-side.questions :as questions]))

(def default-allowed-origins
  #{"http://localhost:8000"
    "http://localhost:4173"
    "https://lexibench.com"
    "https://www.lexibench.com"
    "https://vocab-test-two.vercel.app"})

(defn allowed-origins []
  (if-let [configured (not-empty (System/getenv "ALLOWED_ORIGINS"))]
    (->> (str/split configured #",")
         (map str/trim)
         (remove str/blank?)
         set)
    default-allowed-origins))

(defn request-origin [request]
  (get-in request [:headers "origin"]))

(defn cors-headers [request]
  (let [origin (request-origin request)]
    (cond-> {"Vary" "Origin"}
      (contains? (allowed-origins) origin)
      (assoc "Access-Control-Allow-Origin" origin
             "Access-Control-Allow-Methods" "GET, OPTIONS"
             "Access-Control-Allow-Headers" "Content-Type"))))

(defn json-response [request status body]
  {:status status
   :headers (assoc (cors-headers request)
                   "Content-Type" "application/json; charset=utf-8")
   :body (json/write-str body)})

(defn no-content-response [request]
  {:status 204
   :headers (cors-headers request)
   :body ""})

(defn not-found-response [request]
  (json-response request 404 {:error "Not found"}))

(defn handler [{:keys [request-method uri] :as request}]
  (case request-method
    :options (no-content-response request)
    :get (case uri
           "/healthz" (json-response request 200 {:status "ok"})
           "/api/questions" (json-response request 200 (questions/all))
           (not-found-response request))
    (not-found-response request)))
