(ns jamiepratt.vocab-test-client-side.pages.html-container
  (:require
   [reagent.core :as r]))

(defn html-container [tag attrs html]
  [tag (assoc attrs
              :ref (fn [node]
                     (when node
                       (r/after-render
                        #(set! (.-innerHTML node) (or html ""))))))])
