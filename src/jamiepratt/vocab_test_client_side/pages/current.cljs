(ns jamiepratt.vocab-test-client-side.pages.current
  (:require
   [jamiepratt.vocab-test-client-side.data :as data]
   [jamiepratt.vocab-test-client-side.scoring :as scoring]
   [reagent.core :as r]))

(def preset-options
  [{:id :quick
    :label "Quick"
    :title "Open the core overview sections."
    :aria-label "Quick: overview only"}
   {:id :guide
    :label "Guide"
    :title "Open the overview and walkthrough sections."
    :aria-label "Guide: overview and walkthrough"}
   {:id :detail
    :label "Detail"
    :title "Open the overview, walkthrough, and methodology sections."
    :aria-label "Detail: full methodology"}])

(def preset-kinds
  {:quick #{:core}
   :guide #{:core :guide}
   :detail #{:core :guide :detail}})

(defn preset-open-ids [sections preset-id]
  (let [kinds (get preset-kinds preset-id #{:core})]
    (->> sections
         (filter #(contains? kinds (:kind %)))
         (map :id)
         set)))

(defn doc-anchor-click [event]
  (when-let [anchor (some-> (.-target event)
                            (.closest "a[href^='#']"))]
    (let [href (.getAttribute anchor "href")]
      (when (> (count href) 1)
        (.preventDefault event)
        (when-let [target (.getElementById js/document (subs href 1))]
          (.scrollIntoView target #js {:behavior "smooth"
                                       :block "start"}))))))

(defn preset-button [selected-preset open-section-ids sections {:keys [id label title aria-label]}]
  [:button {:type "button"
            :class "doc-preset-button"
            :title title
            :aria-label aria-label
            :aria-pressed (if (= @selected-preset id) "true" "false")
            :on-click #(do
                         (reset! selected-preset id)
                         (reset! open-section-ids (preset-open-ids sections id)))}
   label])

(defn preset-controls [selected-preset open-section-ids sections]
  [:div {:class "doc-preset-switcher"
         :role "group"
         :aria-label "Explanation preset"}
   (for [option preset-options]
     ^{:key (:id option)}
     [preset-button selected-preset open-section-ids sections option])])

(defn toggle-section [open-section-ids id]
  (swap! open-section-ids
         (fn [ids]
           (if (contains? ids id)
             (disj ids id)
             (conj ids id)))))

(defn accordion-section [open-section-ids {:keys [id title body]}]
  (let [open? (contains? @open-section-ids id)
        section-id (name id)
        heading-id (str section-id "-heading")
        panel-id (str section-id "-panel")]
    [:section {:id section-id
               :class "doc-accordion"
               :aria-labelledby heading-id}
     [:h2 {:id heading-id
           :class "doc-accordion-heading"}
      [:button {:type "button"
                :class "doc-accordion-button"
                :aria-expanded (if open? "true" "false")
                :aria-controls panel-id
                :on-click #(toggle-section open-section-ids id)}
       [:span title]
       [:span {:class "doc-accordion-icon"
               :aria-hidden true}
        (if open? "−" "+")]]]
     (when open?
       [:div {:id panel-id
              :class "doc-accordion-panel"
              :role "region"
              :aria-labelledby heading-id}
        [body]])]))

(defn static-level-option [selected-id {:keys [id label]}]
  [:label {:class "app-level-option doc-static-option"}
   [:input {:type "radio"
            :name "doc-starting-level-example"
            :value (name id)
            :checked (= selected-id id)
            :on-change #()}]
   [:span label]])

(defn testing-level-fragment []
  [:div {:class "doc-ui-fragment"
         :aria-label "Example starting level selector"}
   [:p {:class "doc-fragment-label"} "Starting level example"]
   [:fieldset {:class "grid gap-3"
               :role "radiogroup"
               :aria-label "Example starting level"}
    [:legend {:class "font-semibold app-ink"} "Starting level"]
    [:div {:class "grid gap-2 sm:grid-cols-2"}
     (for [option data/level-options]
       ^{:key (:id option)}
       [static-level-option :absolute-beginner option])]]])

(defn sample-question-fragment []
  [:article {:class "app-card app-question-card doc-static-question grid gap-4 p-4 sm:p-5"
             :aria-label "Example sentence question"}
   [:div {:class "grid gap-2"}
    [:p {:class "app-eyebrow"} "Item 12 of 80"]
    [:h3 {:class "text-xl font-bold app-ink"}
     "What does the highlighted word in this sentence mean?"]]
   [:p {:role "group"
        :aria-label "Polish sentence"
        :class "app-sentence-text"}
    "Codziennie "
    [:mark {:role "term"
            :class "app-target-mark"}
     "piję"]
    " wodę po treningu."]
   [:div {:role "group"
          :aria-label "Example answer choices"
          :class "grid gap-2"}
    (for [choice ["I sleep" "I walk" "I read" "I wait" "I drink"]]
      ^{:key choice}
      [:button {:type "button"
                :class "doc-choice-button"
                :disabled true}
       choice])
    [:button {:type "button"
              :class "doc-choice-button"
              :disabled true}
     "don't know "
     [:span {:class "font-normal app-muted"}
      "(don't guess for a more accurate estimate, press this if unsure)"]]]])

(defn live-estimate-fragment []
  [:div {:role "region"
         :aria-label "Example live estimate"
         :class "app-card app-live-estimate app-subtle-bg doc-ui-fragment grid gap-1 p-4 text-sm sm:p-5"}
   [:p {:class "text-xs font-bold uppercase app-muted"}
    "Live estimate of how many dictionary forms of words you know"]
   [:p {:class "font-semibold app-ink-soft"}
    "Current estimate: about 1,450 recognized Polish lemmas"]
   [:p {:class "app-muted"}
    "Likely range: 1,050-1,900"]])

(defn result-card-fragment []
  [:article {:aria-label "Example final result"
             :class "app-card app-card-wide doc-result-example grid gap-4 p-5 sm:p-6"}
   [:div {:class "grid gap-1"}
    [:p {:class "app-eyebrow"} "Final result example"]
    [:h3 {:class "text-2xl font-bold app-ink"}
     "Estimated recognized Polish lemmas"]
    [:p {:class "app-accent-text break-words text-4xl font-bold"}
     "about 1,450 recognized Polish lemmas"]
    [:p {:class "text-sm font-semibold app-ink-soft"}
     "Likely range: 1,050-1,900"]]
   [:div {:class "grid gap-2 text-sm app-muted sm:grid-cols-3"}
    [:p "Answered: 80"]
    [:p "Wrong: 17"]
    [:p "Don't know: 24"]]
   [:p {:class "break-words text-base font-semibold app-ink-soft"}
    "Approximate level: A2"]
   [:div {:role "region"
          :aria-label "Example vocabulary estimate by lemma rank"
          :class "app-subtle-bg grid gap-2 rounded-md p-3 text-sm"}
    [:p {:class "font-bold app-ink"} "Vocabulary estimate by lemma rank"]
    [:ul {:class "grid gap-1 app-muted"}
     [:li "Lemma ranks 1-1,000 | observed | 1/80 | est. 180 (range 0-400)"]
     [:li "Lemma ranks 1,001-2,000 | observed | 46/80 | est. 620 (range 450-760)"]]]])

(def testing-sections
  [{:id :testing-measures
    :kind :core
    :title "What the test measures"
    :body (fn []
            [:div {:class "doc-copy"}
             [:p "The current test estimates passive Polish vocabulary: lemmas you recognize in sentence context, even if you would not produce them yourself."]
             [:p "Each scored item asks for the English meaning of one highlighted Polish form inside a short sentence. The answer is scored against the linked lemma meaning in that context."]])}
   {:id :testing-start
    :kind :core
    :title "Choose a starting level"
    :body (fn []
            [:div {:class "doc-copy"}
             [:p "Pick the level that feels closest to your Polish right now. It only chooses the first block; the test can move easier or harder after it sees your answers."]
             [testing-level-fragment]])}
   {:id :testing-items
    :kind :guide
    :title "Answer sentence items"
    :body (fn []
            [:div {:class "doc-copy"}
             [:p "A block has 80 scored sentence-context lemma items. Choose the best English meaning when you know it."]
             [:p [:strong "Do not guess. "] "Use " [:strong "don't know"] " when unsure; that gives a cleaner estimate than random multiple-choice hits."]
             [sample-question-fragment]])}
   {:id :testing-continuation
    :kind :guide
    :title "Easier or harder continuation"
    :body (fn []
            [:div {:class "doc-copy"}
             [:p "If the first block is too easy, the session continues with harder sentence items. If it is too hard, it continues with easier sentence items."]
             [:p "Previous answers still count. The final estimate combines all scored real items from the session instead of restarting."]])}
   {:id :testing-detail
    :kind :detail
    :title "Testing methodology details"
    :body (fn []
            [:div {:class "doc-copy"}
             [:p "Current blocks sample ranked surface-form difficulty spans, while results report recognized lemmas from the 10,000-lemma inventory."]
             [:p "Served items and answer events use " [:code "lemma-inventory-stratum"] " for the scoring bin; " [:code "surface-difficulty-rank"] " remains the item-ordering signal."]
             [:p "For the full reference, open "
              [:a {:href "vocabulary-size-testing.md"} "vocabulary-size-testing.md"]
              ". The compatibility index remains at "
              [:a {:href "vocabulary-size-testing-methodology.md"} "vocabulary-size-testing-methodology.md"]
              "."]])}])

(def scoring-sections
  [{:id :scoring-meaning
    :kind :core
    :title "What the score means"
    :body (fn []
            [:div {:class "doc-copy"}
             [:p "The result is an estimate of recognized Polish lemmas, not a claim that you know an exact number of words."]
             [:p "The current reporting inventory has " (scoring/format-count data/lemma-inventory-size) " lemmas, grouped into " (scoring/format-count data/lemma-stratum-size) "-lemma strata for scoring."]])}
   {:id :scoring-live
    :kind :core
    :title "Live estimate and final result"
    :body (fn []
            [:div {:class "doc-copy"}
             [:p "The live estimate appears after " data/live-estimate-min-real-answers " scored answers. Before then, the app says it is still collecting enough evidence."]
             [live-estimate-fragment]
             [result-card-fragment]])}
   {:id :scoring-guessing
    :kind :guide
    :title "Guessing handling"
    :body (fn []
            [:div {:class "doc-copy"}
             [:p "The current model trusts the no-guess instruction at first. Wrong selected answers increase the estimated chance that the learner is guessing."]
             [:p "That lowers confidence in lucky multiple-choice correct answers and widens the likely range when the response pattern looks random."]])}
   {:id :scoring-floor
    :kind :guide
    :title "Very low and uneven results"
    :body (fn []
            [:div {:class "doc-copy"}
             [:p "At the lowest block, a very low score reports a floor result such as " [:strong "under 200"] " with a broad likely range like " [:strong "0-200"] "."]
             [:p "Starting at a high level does not automatically award credit for lower strata. Lower strata are assumed known only after a high-confidence pass or after an anchor block supplies evidence."]])}
   {:id :scoring-detail
    :kind :detail
    :title "Scoring model details"
    :body (fn []
            [:div {:class "doc-copy"}
             [:p "Current scoring model: " [:code scoring/scoring-model-version] "."]
             [:p "Each item contributes evidence to its lemma-inventory stratum. The model uses a latent session-level guessing parameter and reports a posterior likely range."]
             [:p "Final results include " [:strong "Vocabulary estimate by lemma rank"] ", a section of posterior lemma-rank estimates grouped by lemma-inventory stratum."]
             [:p "For the full reference, open "
              [:a {:href "vocabulary-size-scoring.md"} "vocabulary-size-scoring.md"]
              "."]])}])

(defn current-page [{:keys [page-class eyebrow heading dek sections footer]}]
  (r/with-let [selected-preset (r/atom :quick)
               open-section-ids (r/atom (preset-open-ids sections :quick))]
    [:div {:class (str "doc-page doc-page-current " page-class)
           :on-click doc-anchor-click}
     [:header
      [:div {:class "header-inner"}
       [:p {:class "eyebrow"} eyebrow]
       [:h1 heading]
       [:p {:class "dek"} dek]
       [preset-controls selected-preset open-section-ids sections]]]
     [:div {:class "layout"}
      [:main
       (for [section sections]
         ^{:key (:id section)}
         [accordion-section open-section-ids section])]
      [:aside {:class "toc"
               :aria-label "Contents"}
       [:p {:class "toc-title"} "Contents"]
       [:nav {:class "toc-list"}
        (for [{:keys [id title]} sections]
          ^{:key id}
          [:a {:href (str "#" (name id))}
           title])]]]
     [:footer
      [:p footer]]]))

(defn testing-page []
  [current-page {:page-class "doc-page-current-testing"
                 :eyebrow "Current testing"
                 :heading "Current vocabulary size testing"
                 :dek "How learners take the live sentence-context Polish vocabulary test today."
                 :sections testing-sections
                 :footer "Learners first; teachers can use the same result language for placement and progress conversations."}])

(defn scoring-page []
  [current-page {:page-class "doc-page-current-scoring"
                 :eyebrow "Current scoring"
                 :heading "Current vocabulary size scoring"
                 :dek "How the app turns sentence answers into a lemma estimate and likely range."
                 :sections scoring-sections
                 :footer "Scores are estimates with visible uncertainty, not exact vocabulary counts."}])
