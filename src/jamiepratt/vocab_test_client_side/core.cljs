(ns jamiepratt.vocab-test-client-side.core
  (:require
   [jamiepratt.vocab-test-client-side.data :as data]
   [jamiepratt.vocab-test-client-side.scoring :as scoring]
   [reagent.core :as r]
   [reagent.dom.client :as rdom]
   [shadow.lazy :as lazy]))

(def band-styles
  {:B1 {:bar "band-bar band-b1"
        :badge "band-badge band-b1"
        :panel "band-panel band-b1"
        :text "band-text band-b1"}
   :B2 {:bar "band-bar band-b2"
        :badge "band-badge band-b2"
        :panel "band-panel band-b2"
        :text "band-text band-b2"}
   :B3 {:bar "band-bar band-b3"
        :badge "band-badge band-b3"
        :panel "band-panel band-b3"
        :text "band-text band-b3"}
   :B4 {:bar "band-bar band-b4"
        :badge "band-badge band-b4"
        :panel "band-panel band-b4"
        :text "band-text band-b4"}
   :B5 {:bar "band-bar band-b5"
        :badge "band-badge band-b5"
        :panel "band-panel band-b5"
        :text "band-text band-b5"}
   :B6 {:bar "band-bar band-b6"
        :badge "band-badge band-b6"
        :panel "band-panel band-b6"
        :text "band-text band-b6"}})

(defn band-style-class [band-id style-key]
  (get-in band-styles [band-id style-key]))

(defn initial-state []
  {:screen :start
   :questions []
   :questions-loading? true
   :questions-error nil
   :current-question-index 0
   :question-choices []
   :answers []
   :answer-locked? false
   :feedback nil
   :results-data nil})

(defonce app-state
  (r/atom (initial-state)))

(def primary-routes
  [{:id :test
    :label "Test"
    :href "#/"}
   {:id :features
    :label "Features"
    :href "#/features"
    :lazy? true}])

(def theory-routes
  [{:id :methodology
    :label "Progressive methodology"
    :href "#/methodology"}
   {:id :adaptive-methodology
    :label "Adaptive methodology"
    :href "#/adaptive-methodology"
    :lazy? true}])

(def routes
  (vec (concat primary-routes theory-routes)))

(def theme-options
  [{:id :light
    :label "Light"}
   {:id :dark
    :label "Dark"}])

(def theme-storage-key "vocab-theme")

(def route-by-hash
  (into {} (map (juxt :href :id) routes)))

(defn route-from-location []
  (get route-by-hash (.-hash js/location) :test))

(defonce current-route
  (r/atom (route-from-location)))

(defn stored-theme []
  (try
    (let [stored (some-> js/localStorage
                         (.getItem theme-storage-key))
          theme-id (keyword stored)]
      (if (some #(= theme-id (:id %)) theme-options)
        theme-id
        :light))
    (catch :default _
      :light)))

(defonce current-theme
  (r/atom (stored-theme)))

(defonce theory-menu-open?
  (r/atom false))

(defn set-theme! [theme-id]
  (reset! current-theme theme-id)
  (try
    (.setItem js/localStorage theme-storage-key (name theme-id))
    (catch :default _ nil)))

(def lazy-pages
  {:adaptive-methodology
   #_{:clj-kondo/ignore [:unresolved-namespace]}
   (lazy/loadable jamiepratt.vocab-test-client-side.pages.adaptive-methodology/page)
   :features
   #_{:clj-kondo/ignore [:unresolved-namespace]}
   (lazy/loadable jamiepratt.vocab-test-client-side.pages.features/page)})

(defonce lazy-page-components
  (r/atom {}))

(defonce lazy-page-loading
  (atom #{}))

(defonce lazy-page-errors
  (r/atom {}))

(defonce route-listener-registered?
  (atom false))

(defonce root
  (delay (rdom/create-root (.getElementById js/document "app"))))

(def button-class
  "app-primary-button sm:w-auto")

(defonce questions-requested?
  (atom false))

(defn load-lazy-page! [route]
  (when-let [loadable (get lazy-pages route)]
    (when-not (or (contains? @lazy-page-components route)
                  (contains? @lazy-page-loading route))
      (swap! lazy-page-loading conj route)
      (lazy/load
       loadable
       (fn [component]
         (swap! lazy-page-components assoc route component)
         (swap! lazy-page-errors dissoc route)
         (swap! lazy-page-loading disj route))
       (fn [_]
         (swap! lazy-page-errors assoc route "Could not load this page.")
         (swap! lazy-page-loading disj route))))))

(defn api-base-url []
  (let [config (aget js/window "VOCAB_CONFIG")]
    (or (some-> config (aget "apiBaseUrl"))
        "")))

(defn normalize-question [question]
  (update question :band keyword))

(defn load-questions! []
  (-> (js/fetch (str (api-base-url) "/api/questions"))
      (.then (fn [response]
               (if (.-ok response)
                 (.json response)
                 (throw (js/Error. "Could not load questions")))))
      (.then (fn [body]
               (let [questions (mapv normalize-question
                                     (js->clj body :keywordize-keys true))]
                 (swap! app-state assoc
                        :questions questions
                        :questions-loading? false
                        :questions-error nil))))
      (.catch (fn [_]
                (swap! app-state assoc
                       :questions-loading? false
                       :questions-error "Could not load questions.")))))

(defn answer-options [question]
  (into [{:label (:correct question)
          :result :correct}]
        (map (fn [wrong]
               {:label wrong
                :result :wrong})
             (:wrong question))))

(defn choice-options [question]
  (conj (vec (shuffle (answer-options question)))
        {:label "don't know"
         :result :dk}))

(defn begin-test []
  (let [questions (:questions @app-state)]
    (when (seq questions)
      (reset! app-state
              (assoc (initial-state)
                     :screen :quiz
                     :questions questions
                     :questions-loading? false
                     :questions-error nil
                     :question-choices (mapv choice-options questions))))))

(defn feedback-for [choice question]
  (case (:result choice)
    :correct {:kind :correct
              :selected (:label choice)
              :message "Correct"}
    :dk {:kind :wrong
         :selected (:label choice)
         :message (str "Correct answer: " (:correct question))}
    :wrong {:kind :wrong
            :selected (:label choice)
            :message (str "Correct answer: " (:correct question))}))

(defn record-answer [question choices choice]
  (swap! app-state
         (fn [{:keys [answer-locked? current-question-index answers questions] :as state}]
           (if answer-locked?
             state
             (let [answer {:question-index current-question-index
                           :word (:word question)
                           :word-class (:word-class question)
                           :band (:band question)
                           :choices (mapv :label choices)
                           :selected (:label choice)
                           :correct (:correct question)
                           :result (:result choice)}
                   next-answers (conj answers answer)]
               (assoc state
                      :answers next-answers
                      :answer-locked? true
                      :feedback (feedback-for choice question)
                      :results-data (scoring/summarize-results questions next-answers)))))))

(defn next-question []
  (swap! app-state
         (fn [{:keys [current-question-index answers questions] :as state}]
           (if (= current-question-index (dec (count questions)))
             (assoc state
                    :screen :results
                    :results-data (scoring/summarize-results questions answers))
             (assoc state
                    :current-question-index (inc current-question-index)
                    :answer-locked? false
                    :feedback nil)))))

(defn start-screen [{:keys [questions questions-loading? questions-error]}]
  [:main {:class "app-page"}
   [:section {:aria-labelledby "start-heading"
              :class "app-card grid gap-5 p-5 sm:gap-6 sm:p-7"}
    [:div {:class "grid gap-3"}
     [:p {:class "app-eyebrow"} "Polish to English"]
     [:h1 {:id "start-heading"
           :class "text-4xl font-bold leading-tight app-ink sm:text-5xl"}
      "Polish Passive Vocabulary Size Test"]
     [:p {:class "app-copy max-w-2xl text-base leading-7"}
      "Anchored low, ranging wide."]]
    [:div {:class "app-soft-panel grid gap-4 rounded-md border p-4 text-sm leading-6"}
     [:p [:strong {:class "font-semibold app-ink"} "Format: "]
      "You'll see a Polish word. Pick the correct English meaning from 4 choices."]
     [:p "Best for roughly 250-3,500+ passive Polish words. Below that it checks the basics; above that it mostly detects that you've hit this short test's ceiling."]
     [:div
      [:p {:class "font-semibold app-ink"} "80 words across 6 bands:"]
      [:ul {:class "mt-2 grid gap-1"}
       (for [{:keys [id summary]} data/bands]
         ^{:key id}
         [:li {:class (str "rounded-md border px-3 py-2 " (band-style-class id :panel))}
          summary])]]
     [:p [:strong {:class "font-semibold app-ink"} "Don't guess. "]
      "Pick \"don't know\" if unsure; it makes the estimate accurate."]
     [:p "~12 minutes."]]
    (when questions-error
      [:p {:role "alert"
           :class "app-feedback-error rounded-md p-3 text-sm font-semibold"}
       questions-error])
    [:button {:type "button"
              :class button-class
              :disabled (or questions-loading? questions-error (empty? questions))
              :on-click begin-test}
     "Begin Test"]]])

(defn choice-button [question choices answer-locked? feedback choice]
  (let [correct-answer? (= (:label choice) (:correct question))
        selected-answer? (= (:label choice) (:selected feedback))
        selected-result (:kind feedback)
        base-class "min-h-12 rounded-md border px-4 py-3 text-left text-sm font-semibold break-words shadow-sm transition-colors focus-visible:outline-2 focus-visible:outline-offset-2"
        state-class (cond
                      (and answer-locked? correct-answer?)
                      "app-choice-correct"

                      (and answer-locked? selected-answer? (= :wrong selected-result))
                      "app-choice-wrong"

                      :else
                      "app-choice-button")]
    [:button {:type "button"
              :class (str base-class " " state-class)
              :disabled answer-locked?
              :on-click #(record-answer question choices choice)}
     (:label choice)]))

(defn quiz-screen [{:keys [questions current-question-index question-choices answer-locked? feedback]}]
  (let [question (nth questions current-question-index)
        choices (or (get question-choices current-question-index)
                    (choice-options question))
        current-question-number (inc current-question-index)
        progress (* 100 (/ current-question-number (count questions)))]
    [:main {:class "app-page"}
     [:section {:aria-labelledby "question-word"
                :class "app-card grid gap-5 p-5 sm:gap-6 sm:p-7"}
      [:div {:class "h-2 overflow-hidden rounded-full app-subtle-bg"
             :role "progressbar"
             :aria-valuemin 1
             :aria-valuemax (count questions)
             :aria-valuenow current-question-number}
       [:div {:class "app-progress-fill h-full rounded-full transition-all"
              :style {:width (str progress "%")}}]]
      [:div {:class "flex flex-wrap items-center justify-between gap-3 text-sm font-semibold app-muted"}
       [:span (str current-question-number " / " (count questions))]
       [:span {:class (str "rounded-full px-3 py-1 text-xs font-bold ring-1 " (band-style-class (:band question) :badge))}
        (data/band-labels (:band question))]]
      [:div {:class "grid gap-2 text-center"}
       [:h2 {:id "question-word"
             :class "break-words text-4xl font-bold leading-tight app-ink sm:text-5xl"}
        (:word question)]
       [:p {:class "text-sm font-semibold uppercase app-muted"}
        (:word-class question)]
       [:p {:class "text-base app-muted"} "Select the correct meaning"]]
      [:div {:class "grid gap-3"}
       (for [choice choices]
         ^{:key (:label choice)}
         [choice-button question choices answer-locked? feedback choice])]
      (when feedback
        [:p {:class (if (= :correct (:kind feedback))
                      "app-feedback-success rounded-md p-3 text-sm font-semibold"
                      "app-feedback-error rounded-md p-3 text-sm font-semibold")}
         (:message feedback)])
      (when answer-locked?
        [:button {:type "button"
                  :class (str button-class " justify-self-stretch sm:justify-self-end")
                  :on-click next-question}
         "Next"])]]))

(defn band-result-row [results-data band-id]
  (let [{:keys [answered correct pct]} (get-in results-data [:band-stats band-id])]
    [:li {:class "grid min-w-0 gap-2 rounded-md border app-border p-3 text-sm sm:grid-cols-[5rem_1fr_6rem] sm:items-center"}
     [:span {:class (str "font-bold " (band-style-class band-id :text))} (data/band-labels band-id)]
     [:div {:class "h-2 overflow-hidden rounded-full app-subtle-bg"
            :aria-hidden true}
      [:div {:class (str "h-full rounded-full " (band-style-class band-id :bar))
             :style {:width (str pct "%")}}]]
     [:span {:class "font-semibold app-muted"}
      (str correct "/" answered " (" pct "%)")]]))

(defn review-answer-row [{:keys [band word correct]}]
  [:li {:class "grid min-w-0 gap-1 rounded-md border app-border p-3 text-sm sm:grid-cols-3 sm:items-center"}
   [:span {:class (str "font-bold " (band-style-class band :text))} (data/band-labels band)]
   [:span {:class "break-words font-semibold app-ink"} word]
   [:span {:class "break-words app-muted"} correct]])

(defn review-section [review-answers]
  (when (seq review-answers)
    (let [heading (str "Words to review (" (count review-answers) ")")]
      [:section {:aria-labelledby "review-heading"
                 :class "grid gap-3"}
       [:h2 {:id "review-heading"
             :class "text-lg font-bold app-ink"}
        heading]
       [:ul {:class "grid gap-2"}
        (for [{:keys [question-index] :as answer} review-answers]
          ^{:key question-index}
          [review-answer-row answer])]])))

(defn results-screen [{:keys [results-data]}]
  [:main {:class "app-page"}
   [:section {:aria-labelledby "results-heading"
              :class "app-card app-card-wide grid gap-5 p-5 sm:gap-6 sm:p-7"}
    [:div {:class "grid gap-2 text-center"}
     [:h1 {:id "results-heading"
           :class "text-4xl font-bold leading-tight app-ink"}
      "Results"]
     [:p {:class "app-accent-text text-6xl font-bold"}
      (str (:accuracy-pct results-data) "%")]
     [:p {:class "text-base font-semibold app-muted"}
      (str (:correct results-data) " of " (:total results-data) " correct")]]
    [:div {:class "grid gap-2 text-sm font-semibold app-muted sm:grid-cols-3"}
     [:p (str "Answered: " (:answered results-data))]
     [:p (str "Wrong: " (:wrong results-data))]
     [:p (str "Don't know: " (:dk results-data))]]
    [:section {:aria-labelledby "band-results-heading"
               :class "grid gap-3"}
     [:h2 {:id "band-results-heading"
           :class "text-lg font-bold app-ink"}
      "Accuracy by frequency band"]
     [:ul {:class "grid gap-2"}
      (for [band-id data/ordered-band-ids]
        ^{:key band-id}
        [band-result-row results-data band-id])]]
    [review-section (:review-answers results-data)]
    [:section {:aria-labelledby "estimate-heading"
               :class "grid gap-4 border-t app-border pt-6"}
     [:div {:class "flex flex-wrap items-center justify-between gap-3"}
      [:h2 {:id "estimate-heading"
            :class "text-lg font-bold app-ink"}
       "Vocabulary estimate"]
      [:p {:class (str "rounded-full px-3 py-1 text-xs font-bold ring-1 " (band-style-class (:ceiling-band results-data) :badge))}
       (str "Ceiling: " (data/band-labels (:ceiling-band results-data)))]]
     [:div {:class "app-accent-panel grid gap-1 rounded-md border p-4"}
      [:p {:class "text-xs font-bold uppercase app-muted"}
       "Estimated passive vocabulary"]
      [:p {:class "app-accent-text break-words text-4xl font-bold"}
       (str "~" (:adjusted-estimate results-data) " words")]]
     [:p {:class "break-words text-base font-semibold app-ink-soft"}
      (:comparison results-data)]
     [:p {:class "break-words text-base leading-7 app-muted"}
      (:interpretation results-data)]
     (when (:honesty-note results-data)
       [:p {:class "break-words rounded-md app-subtle-bg p-3 text-sm font-semibold app-ink-soft"}
        (:honesty-note results-data)])
     [:p {:class "break-words rounded-md app-subtle-bg p-3 text-sm leading-6 app-muted"}
      [:strong {:class "font-semibold app-ink"} "A note on Polish: "]
      "This test shows words in their dictionary (nominative) form. Polish has 7 cases, so in real text these words appear with different endings (e.g. "
      [:em "woda -> wode -> woda -> wodzie"]
      "). Recognizing a word in its base form is easier than recognizing all its inflected forms, so your reading-comprehension vocabulary may feel smaller than this score suggests until the case system clicks."]
     [:p {:class "break-words rounded-md app-subtle-bg p-3 text-sm leading-6 app-muted"}
      "Passive vocabulary (recognition) is typically 2-3x active vocabulary (production). This test measures recognition only."]]
    [:button {:type "button"
              :class button-class
              :on-click begin-test}
     "Retake"]]])

(defn active-theory-route [route]
  (some #(when (= route (:id %)) %) theory-routes))

(defn close-theory-menu! []
  (reset! theory-menu-open? false))

(defn nav-link [current-route {:keys [id label href]}]
  [:a {:href href
       :aria-controls "page-content"
       :aria-current (when (= current-route id) "page")
       :on-click #(close-theory-menu!)
       :class "app-menu-link"}
   label])

(defn theory-link [current-route {:keys [id label href]}]
  [:a {:href href
       :aria-controls "page-content"
       :aria-current (when (= current-route id) "page")
       :on-click #(close-theory-menu!)
       :class "app-dropdown-link"}
   label])

(defn theory-menu [current-route]
  (let [active-route (active-theory-route current-route)
        active? (boolean active-route)
        summary-label (if active-route
                        (str "Theory › " (:label active-route))
                        "Theory")]
    [:details {:class "app-theory-menu"
               :open @theory-menu-open?
               :on-toggle #(reset! theory-menu-open? (.-open (.-currentTarget %)))}
     [:summary {:class "app-menu-summary"
                :aria-current (when active? "page")}
      summary-label]
     [:div {:class "app-dropdown"}
      (for [route theory-routes]
        ^{:key (:id route)}
        [theory-link current-route route])]]))

(defn theme-button [selected-id {:keys [id label]}]
  [:button {:type "button"
            :class "app-theme-button"
            :aria-pressed (if (= selected-id id) "true" "false")
            :on-click #(set-theme! id)}
   label])

(defn theme-switcher [selected-id]
  [:div {:class "app-theme-switcher"
         :role "group"
         :aria-label "Color theme"}
   (for [option theme-options]
     ^{:key (:id option)}
     [theme-button selected-id option])])

(defn top-menu [current-route selected-theme]
  [:header {:class "app-topbar"}
   [:div {:class "app-topbar-inner"}
    [:div {:class "app-brand"} "Polish Vocabulary"]
    [:nav {:aria-label "Main"
           :class "app-menu"}
     (for [route primary-routes]
       ^{:key (:id route)}
       [nav-link current-route route])
     [theory-menu current-route]]
    [theme-switcher selected-theme]]])

(defn methodology-section [id title & body]
  (into
   [:section {:id id
              :aria-labelledby (str id "-heading")
              :class "grid gap-3 border-t app-border pt-6 first:border-t-0 first:pt-0"}
    [:h2 {:id (str id "-heading")
          :class "text-2xl font-bold leading-tight app-ink"}
     title]]
   body))

(defn methodology-screen []
  [:main {:class "app-page"}
   [:article {:aria-labelledby "methodology-heading"
              :class "app-card app-card-wide grid gap-6 p-5 sm:p-7"}
    [:div {:class "grid gap-3"}
     [:p {:class "app-eyebrow"} "Testing methodology"]
     [:h1 {:id "methodology-heading"
           :class "text-4xl font-bold leading-tight app-ink sm:text-5xl"}
      "Progressive vocabulary test methodology"]
     [:p {:class "app-copy max-w-2xl text-base leading-7"}
      "Launch from frequency-ranked words, learn real item difficulty from live responses, then move toward a shorter calibrated adaptive test."]]
    [methodology-section
     "core-bet"
     "Core bet"
     [:ol {:class "grid list-decimal gap-2 pl-5 text-sm leading-6 app-muted"}
      [:li "Frequency rank is good enough to launch."]
      [:li "It is not good enough to keep forever."]
      [:li "Raw responses let us learn real item difficulty."]
      [:li "Once item difficulty is known, old tests can be re-scored and future tests can get shorter."]]]
    [methodology-section
     "estimate"
     "What we estimate"
     [:p {:class "text-sm leading-6 app-muted"}
      "The score estimates receptive vocabulary size: how many target-language lemmas the learner probably recognizes. It counts lemmas, not displayed word forms."]
     [:ul {:class "grid list-disc gap-2 pl-5 text-sm leading-6 app-muted"}
      [:li "Final scores should include a center estimate plus lower and upper range."]
      [:li "Scores should retain the scoring model version."]
      [:li "Reliability flags should come from checks, timing, and fake-word behavior."]]]
    [methodology-section
     "launch-test"
     "Stage 1: long stratified launch test"
     [:p {:class "text-sm leading-6 app-muted"}
      "Before calibration exists, the test samples broadly across frequency bands rather than adapting too early."]
     [:div {:class "app-soft-panel grid gap-2 rounded-md border p-4 text-sm leading-6"}
      [:p "60-80 real words"]
      [:p "5-10 quality checks"]
      [:p "Several frequency bands"]
      [:p "Randomized order"]
      [:p "Occasional meaning checks after claimed-known answers"]]]
    [methodology-section
     "scoring"
     "Stage 1 scoring"
     [:p {:class "text-sm leading-6 app-muted"}
      "Use frequency-band scoring: calculate hit rate per band, correct overclaiming with fake-word false alarms, clamp corrected hit rate to 0-1, then sum band size times corrected hit rate."]
     [:p {:class "rounded-md app-subtle-bg p-3 text-sm leading-6 app-muted"}
      "This score is provisional. It can be useful to the learner, but the uncertainty range should be wide."]]
    [methodology-section
     "calibration"
     "Stage 2: calibration"
     [:p {:class "text-sm leading-6 app-muted"}
      "After enough clean response data, estimate real item difficulty with a Rasch model."]
     [:pre {:class "overflow-x-auto rounded-md bg-stone-950 p-4 text-sm text-stone-50"}
      "P(response_i = 1 | theta_user, b_item) = logistic(theta_user - b_item)"]
     [:p {:class "text-sm leading-6 app-muted"}
      "Bad sessions and poor-fit items are filtered before publishing a calibrated item-bank version."]]
    [methodology-section
     "videos"
     "Video explainers"
     [:ul {:class "grid list-disc gap-2 pl-5 text-sm leading-6 app-muted"}
      [:li
       [:a {:href "https://www.youtube.com/watch?v=P8huS6PPxJA"
            :class "app-accent-text font-bold underline underline-offset-4"}
        "What is Item Response Theory?"]
       " - quick conceptual intro before the Rasch-specific parts."]
      [:li
       [:a {:href "https://www.youtube.com/watch?v=Qsk8PaDS9oM"
            :class "app-accent-text font-bold underline underline-offset-4"}
        "What is the Rasch Model?"]
       " - direct explanation of the calibration model used here."]
      [:li
       [:a {:href "https://www.youtube.com/watch?v=B-oLR7XRVCU"
            :class "app-accent-text font-bold underline underline-offset-4"}
        "Understanding Item Response Theory: Key Concepts & Applications"]
       " - longer seminar-style overview."]]]
    [methodology-section
     "production"
     "Stages 3-4: re-score and shorten"
     [:p {:class "text-sm leading-6 app-muted"}
      "Each calibration round can add a new score record for old sessions without overwriting prior scores."]
     [:p {:class "text-sm leading-6 app-muted"}
      "Once enough items are calibrated, switch to a hybrid production test: calibrated scored items for the visible estimate, calibration-tail items for future item-bank quality, and quality checks for reliability."]]
    [:p {:class "border-t app-border pt-5 text-sm leading-6 app-muted"}
     "Full source document: "
     [:a {:href "progressive-vocabulary-testing-methodology.md"
          :class "app-accent-text font-bold underline underline-offset-4"}
      "progressive-vocabulary-testing-methodology.md"]]]])

(defn lazy-page-screen [route]
  (if-let [component (get @lazy-page-components route)]
    [component]
    (do
      (load-lazy-page! route)
      [:main {:class "app-page"}
       [:section {:class "app-card grid gap-3 p-5 sm:p-7"}
        (if-let [message (get @lazy-page-errors route)]
          [:p {:role "alert"
               :class "app-feedback-error rounded-md p-3 text-sm font-semibold"}
           message]
          [:p {:class "text-sm font-semibold app-muted"}
           "Loading..."])]])))

(defn test-screen [state]
  (case (:screen state)
    :quiz [quiz-screen state]
    :results [results-screen state]
    [start-screen state]))

(defn routed-screen [route state]
  (case route
    :methodology [methodology-screen]
    :adaptive-methodology [lazy-page-screen route]
    :features [lazy-page-screen route]
    [test-screen state]))

(defn app []
  (let [state @app-state
        route @current-route
        selected-theme @current-theme]
    [:div {:class "app-frame"
           :data-theme (name selected-theme)}
     [top-menu route selected-theme]
     [:div {:id "page-content"}
      [routed-screen route state]]]))

(defn ^:dev/after-load init []
  (when-not @route-listener-registered?
    (reset! route-listener-registered? true)
    (.addEventListener js/window "hashchange" #(do
                                                 (close-theory-menu!)
                                                 (reset! current-route (route-from-location)))))
  (reset! current-route (route-from-location))
  (when-not @questions-requested?
    (reset! questions-requested? true)
    (load-questions!))
  (rdom/render @root [app]))
