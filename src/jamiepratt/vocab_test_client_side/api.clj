(ns jamiepratt.vocab-test-client-side.api
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [jamiepratt.vocab-test-client-side.db :as db]
   [jamiepratt.vocab-test-client-side.questions :as questions])
  (:import
   [java.net URLDecoder]
   [java.nio.charset StandardCharsets]))

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

(defn- url-decode [value]
  (URLDecoder/decode (str value) (.name StandardCharsets/UTF_8)))

(defn- query-params [request]
  (if-let [query-string (not-empty (:query-string request))]
    (into {}
          (map (fn [part]
                 (let [[raw-key raw-value] (str/split part #"=" 2)]
                   [(url-decode raw-key) (url-decode (or raw-value ""))])))
          (remove str/blank? (str/split query-string #"&")))
    {}))

(defn- sentence-question-block [sentence-question-rows request]
  (let [params (query-params request)
        block-request (questions/block-request params)
        rows (sentence-question-rows block-request)]
    (questions/sentence-block params rows)))

(defn- exception-status [e]
  (or (:status (ex-data e)) 500))

(defn- sentence-question-block-response [request sentence-question-rows]
  (try
    (json-response request 200
                   (sentence-question-block sentence-question-rows request))
    (catch clojure.lang.ExceptionInfo e
      (json-response request (exception-status e)
                     {:error (ex-message e)}))))

(defn make-handler
  ([] (make-handler {}))
  ([{:keys [sentence-question-rows]
     :or {sentence-question-rows db/sentence-question-candidate-rows}}]
   (fn [{:keys [request-method uri] :as request}]
     (case request-method
       :options (no-content-response request)
       :get (case uri
              "/healthz" (json-response request 200 {:status "ok"})
              "/api/questions" (json-response request 200 (questions/all))
              "/api/sentence-question-blocks"
              (sentence-question-block-response request sentence-question-rows)
              (not-found-response request))
       (not-found-response request)))))

(def handler
  (make-handler))
