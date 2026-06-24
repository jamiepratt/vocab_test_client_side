(ns jamiepratt.vocab-test-client-side.doc-assets
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn public-file [path]
  (io/file (System/getProperty "user.dir") "public" path))

(defn extract [html pattern label]
  (or (second (re-find pattern html))
      (throw (ex-info (str "Could not extract " label) {:label label}))))

(defn strip-scripts [html]
  (str/replace html #"(?is)\s*<script[^>]*>.*?</script>\s*" ""))

(defn body-html [path]
  (-> (slurp (public-file path))
      (extract #"(?is)<body[^>]*>\s*(.*?)\s*</body>" (str path " body"))
      strip-scripts))

(defn adaptive-panel [html id]
  (extract html
           (re-pattern (str "(?is)<article class=\"panel\" id=\"" id "\"[^>]*>\\s*(.*?)\\s*</article>"))
           id))

(defn adaptive-toc [html mode]
  (extract html
           (re-pattern (str "(?is)<nav class=\"toc-list\" data-toc=\"" mode "\"[^>]*>\\s*(.*?)\\s*</nav>"))
           (str mode " toc")))

(defmacro adaptive-html []
  (let [html (slurp (public-file "adaptive-vocabulary-testing.html"))]
    {:beginner-panel (adaptive-panel html "beginner-panel")
     :advanced-panel (adaptive-panel html "advanced-panel")
     :beginner-toc (adaptive-toc html "beginner")
     :advanced-toc (adaptive-toc html "advanced")}))

(defmacro features-html []
  (body-html "features-to-implement.html"))
