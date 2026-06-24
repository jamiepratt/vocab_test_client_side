(ns jamiepratt.vocab-test-client-side.pages.adaptive-methodology
  (:require
   [jamiepratt.vocab-test-client-side.pages.html-container :refer [html-container]]
   [reagent.core :as r])
  (:require-macros
   [jamiepratt.vocab-test-client-side.doc-assets :refer [adaptive-html]]))

(def html (adaptive-html))

(def modes
  [{:id :beginner
    :label "Beginner"
    :panel-id "beginner-panel"
    :panel-key :beginner-panel
    :toc-key :beginner-toc}
   {:id :advanced
    :label "Advanced"
    :panel-id "advanced-panel"
    :panel-key :advanced-panel
    :toc-key :advanced-toc}])

(defonce current-mode
  (r/atom :beginner))

(defn doc-anchor-click [event]
  (when-let [anchor (some-> (.-target event)
                            (.closest "a[href^='#']"))]
    (let [href (.getAttribute anchor "href")]
      (when (> (count href) 1)
        (.preventDefault event)
        (when-let [target (.getElementById js/document (subs href 1))]
          (.scrollIntoView target #js {:behavior "smooth"
                                       :block "start"}))))))

(defn mode-tab [{:keys [id label panel-id]} selected-id]
  [:button {:id (str "tab-" (name id))
            :type "button"
            :class "tab"
            :role "tab"
            :aria-controls panel-id
            :aria-selected (if (= selected-id id) "true" "false")
            :on-click #(reset! current-mode id)}
   label])

(defn page []
  (let [selected-id @current-mode
        {:keys [id panel-id panel-key toc-key]} (some #(when (= selected-id (:id %)) %) modes)]
    [:div {:class "doc-page doc-page-adaptive"
           :on-click doc-anchor-click}
     [:header
      [:div {:class "header-inner"}
       [:p {:class "eyebrow"} "Vocabulary measurement"]
       [:h1 "Adaptive vocabulary size testing from a frequency list"]
       [:p {:class "dek"}
        "Staged method: start with a longer frequency-band test, learn real item difficulty, re-score old sessions, then move to a short adaptive test."]
       [:div {:class "tabs"
              :role "tablist"
              :aria-label "Explanation level"}
        (for [mode modes]
          ^{:key (:id mode)}
          [mode-tab mode selected-id])]]]
     [:div {:class "layout"}
      [:main
       [html-container
        :article
        {:id panel-id
         :class "panel"
         :role "tabpanel"
         :aria-labelledby (str "tab-" (name id))}
        (get html panel-key)]]
      [:aside {:class "toc"
               :aria-label "Contents"}
       [:p {:class "toc-title"} "Contents"]
       [html-container
        :nav
        {:class "toc-list"}
        (get html toc-key)]]]
     [:footer
      [:p "Part of the Vocab Test Client Side project. Static HTML is copied into the Vercel artifact by the deploy workflow."]]]))
