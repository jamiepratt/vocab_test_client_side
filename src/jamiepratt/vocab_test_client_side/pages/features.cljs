(ns jamiepratt.vocab-test-client-side.pages.features
  (:require
   [jamiepratt.vocab-test-client-side.pages.html-container :refer [html-container]])
  (:require-macros
   [jamiepratt.vocab-test-client-side.doc-assets :refer [features-html]]))

;; Macro-expanded from public/features-to-implement.html at build time.
(def html (features-html))

(defn doc-anchor-click [event]
  (when-let [anchor (some-> (.-target event)
                            (.closest "a[href^='#']"))]
    (let [href (.getAttribute anchor "href")]
      (when (> (count href) 1)
        (.preventDefault event)
        (when-let [target (.getElementById js/document (subs href 1))]
          (.scrollIntoView target #js {:behavior "smooth"
                                       :block "start"}))))))

(defn page []
  [html-container
   :div
   {:class "doc-page doc-page-features"
    :on-click doc-anchor-click}
   html])
