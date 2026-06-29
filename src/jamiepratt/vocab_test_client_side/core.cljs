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

(defn random-hex [length]
  (apply str (repeatedly length #(.toString (rand-int 16) 16))))

(defn fallback-anonymous-session-id []
  (let [variant-chars "89ab"]
    (str (random-hex 8) "-"
         (random-hex 4) "-"
         "4" (random-hex 3) "-"
         (.charAt variant-chars (rand-int 4)) (random-hex 3) "-"
         (random-hex 12))))

(defn anonymous-session-id []
  (let [crypto (.-crypto js/window)]
    (if (and crypto (.-randomUUID crypto))
      (.randomUUID crypto)
      (fallback-anonymous-session-id))))

(defn initial-state []
  {:screen :start
   :anonymous-session-id (anonymous-session-id)
   :selected-level :absolute-beginner
   :question-block nil
   :adaptive-block nil
   :completed-blocks []
   :session-questions []
   :questions []
   :questions-loading? false
   :questions-error nil
   :current-question-index 0
   :question-choices []
   :answers []
   :answer-locked? false
   :feedback nil
   :continuation-message nil
   :item-started-at-ms nil
   :answer-event-submission-error nil
   :answer-event-submission-failures 0
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

(defn now-ms []
  (.now js/Date))

(defn block-index-band [index]
  (loop [remaining (inc index)
         [{:keys [band items]} & more] data/block-band-profile]
    (cond
      (nil? band) (last data/ordered-band-ids)
      (<= remaining items) band
      :else (recur (- remaining items) more))))

(defn compact-rank-bound [value]
  (if (>= value 1000)
    (if (zero? (mod value 1000))
      (str (quot value 1000) "K")
      (str (/ value 1000) "K"))
    (str value)))

(defn adaptive-block-range-label [adaptive-block-id]
  (when-let [{:keys [surface-rank-start surface-rank-end]}
             (data/adaptive-block adaptive-block-id)]
    (let [lower (if (= 1 surface-rank-start)
                  0
                  surface-rank-start)]
      (str (compact-rank-bound lower)
           "-"
           (compact-rank-bound surface-rank-end)))))

(defn normalize-sentence-item [adaptive-block-id index item]
  (assoc item
         :adaptive-block-id adaptive-block-id
         :band (block-index-band index)
         :question-number (inc index)))

(defn sentence-block-url [{:keys [request]}]
  (let [{:keys [level block]} request]
    (str (api-base-url)
         "/api/sentence-question-blocks?level="
         (js/encodeURIComponent level)
         "&block="
         block)))

(defn answer-event-url []
  (str (api-base-url) "/api/answer-events"))

(defn event-id-value [value]
  (if (keyword? value)
    (name value)
    (str value)))

(defn answer-event-payload [anonymous-session-id answer]
  {:anonymous-session-id anonymous-session-id
   :test-block-id (event-id-value (:test-block-id answer))
   :target-lemma-id (:lemma-id answer)
   :target-surface-form-id (:target-surface-form-id answer)
   :candidate-rank (:candidate-rank answer)
   :inventory-stratum (:inventory-stratum answer)
   :lemma-rank (:lemma-rank answer)
   :surface-difficulty-rank (:surface-difficulty-rank answer)
   :calibrated-difficulty (:calibrated-difficulty answer)
   :item-type (:item-type answer)
   :choice-count (:choice-count answer)
   :guess-rate (:guess-rate answer)
   :selected-answer (:selected-answer answer)
   :correct (:correct? answer)
   :response-time-ms (:response-time-ms answer)
   :attention-check-status (or (:attention-check-status answer)
                               "not-attention-check")})

(defn answer-event-error-message [error]
  (or (some-> error .-message)
      (str error)))

(defn note-answer-event-submission-failure! [event error]
  (let [message (answer-event-error-message error)]
    (.warn js/console "Answer event submission failed" (clj->js {:event event
                                                                 :error message}))
    (swap! app-state
           (fn [state]
             (-> state
                 (assoc :answer-event-submission-error {:message message
                                                        :event event})
                 (update :answer-event-submission-failures (fnil inc 0)))))))

(defn submit-answer-event! [anonymous-session-id answer]
  (let [event (answer-event-payload anonymous-session-id answer)]
    (-> (js/fetch (answer-event-url)
                  (clj->js {:method "POST"
                            :headers {"Content-Type" "application/json"}
                            :body (.stringify js/JSON (clj->js event))}))
        (.then (fn [response]
                 (when-not (.-ok response)
                   (throw (js/Error. (str "Answer event submission failed: "
                                          (.-status response)))))))
        (.catch (fn [error]
                  (note-answer-event-submission-failure! event error))))))

(defn answer-options [question]
  (into [{:label (:correct-translation question)
          :result :correct}]
        (map (fn [distractor]
               {:label distractor
                :result :wrong})
             (:distractors question))))

(def dont-know-choice
  {:label "don't know"
   :result :dk})

(defn choice-options [question]
  (vec (shuffle (answer-options question))))

(defn load-question-block! [adaptive-block-id continuation-message reset-session?]
  (let [selected-level (:selected-level @app-state)
        adaptive-block (data/adaptive-block adaptive-block-id)]
    (swap! app-state
           (fn [state]
             (assoc (if reset-session?
                      (assoc (initial-state) :selected-level selected-level)
                      state)
                    :questions-loading? true
                    :questions-error nil
                    :continuation-message continuation-message)))
    (-> (js/fetch (sentence-block-url adaptive-block))
        (.then (fn [response]
                 (if (.-ok response)
                   (.json response)
                   (throw (js/Error. "Could not load sentence questions")))))
        (.then (fn [body]
                 (let [block (js->clj body :keywordize-keys true)
                       question-block (assoc block
                                             :adaptive-block-id adaptive-block-id
                                             :adaptive-block adaptive-block)
                       questions (mapv (partial normalize-sentence-item adaptive-block-id)
                                       (range)
                                       (:items block))]
                   (if (seq questions)
                     (swap! app-state
                            (fn [state]
                              (assoc state
                                     :screen :quiz
                                     :selected-level selected-level
                                     :question-block question-block
                                     :adaptive-block adaptive-block
                                     :questions questions
                                     :session-questions (into (:session-questions state) questions)
                                     :questions-loading? false
                                     :questions-error nil
                                     :current-question-index 0
                                     :question-choices (mapv choice-options questions)
                                     :answer-locked? false
                                     :feedback nil
                                     :item-started-at-ms (now-ms))))
                     (swap! app-state assoc
                            :questions-loading? false
                            :questions-error "Could not load sentence questions.")))))
        (.catch (fn [_]
                  (swap! app-state assoc
                         :questions-loading? false
                         :questions-error "Could not load sentence questions."))))))

(defn begin-test []
  (let [selected-level (:selected-level @app-state)
        starting-block (data/starting-block selected-level)]
    (load-question-block! (:id starting-block) nil true)))

(defn feedback-for [choice question]
  (case (:result choice)
    :correct {:kind :correct
              :selected (:label choice)
              :message "Correct"}
    :dk {:kind :wrong
         :selected (:label choice)
         :message (str "Correct answer: " (:correct-translation question))}
    :wrong {:kind :wrong
            :selected (:label choice)
            :message (str "Correct answer: " (:correct-translation question))}))

(defn record-answer [question choices choice]
  (let [submitted-answer (atom nil)]
    (swap! app-state
           (fn [{:keys [answer-locked? current-question-index answers
                        session-questions item-started-at-ms question-block] :as state}]
             (if answer-locked?
               state
               (let [answered-at-ms (now-ms)
                     result (:result choice)
                     correct-answer (:correct-translation question)
                     answer {:question-index current-question-index
                             :item-id (:item-id question)
                             :sentence (:sentence question)
                             :target-surface (:target-surface question)
                             :target-surface-form-id (:target-surface-form-id question)
                             :highlight-span (:highlight-span question)
                             :lemma-id (:lemma-id question)
                             :lemma-pos-id (:lemma-pos-id question)
                             :candidate-rank (:surface-difficulty-rank question)
                             :inventory-stratum (:fixed-stratum question)
                             :lemma-rank (:lemma-inventory-rank question)
                             :surface-difficulty-rank (:surface-difficulty-rank question)
                             :item-type (:item-type question)
                             :choice-count (:choice-count question)
                             :guess-rate (:guess-rate question)
                             :choices (mapv :label choices)
                             :selected-answer (:label choice)
                             :selected (:label choice)
                             :correct-answer correct-answer
                             :correct correct-answer
                             :correct? (= :correct result)
                             :result result
                             :response-time-ms (when item-started-at-ms
                                                 (- answered-at-ms item-started-at-ms))
                             :answered-at-ms answered-at-ms
                             :attention-check-status "not-attention-check"
                             :test-block-id (or (:adaptive-block-id question-block)
                                                (:block question-block))
                             :api-block (:block question-block)
                             :adaptive-block-id (:adaptive-block-id question-block)
                             :requested-level (:requested-level question-block)
                             :level (:level question-block)
                             :band (:band question)
                             :word (:target-surface question)}
                     next-answers (conj answers answer)]
                 (reset! submitted-answer answer)
                 (assoc state
                        :answers next-answers
                        :answer-locked? true
                        :feedback (feedback-for choice question)
                        :results-data (scoring/summarize-results session-questions next-answers))))))
    (when-let [answer @submitted-answer]
      (submit-answer-event! (:anonymous-session-id @app-state) answer))))

(defn continuation-message [decision]
  (case (:action decision)
    :route-lower "The first block was too hard, so this test is continuing with easier sentence items."
    :route-higher "The first block was too easy, so this test is continuing with harder sentence items."
    nil))

(defn finish-results! [decision]
  (swap! app-state
         (fn [{:keys [answers session-questions] :as state}]
           (assoc state
                  :screen :results
                  :continuation-message nil
                  :results-data (scoring/summarize-results session-questions answers decision)))))

(defn complete-current-block! []
  (let [{:keys [question-block answers completed-blocks]} @app-state
        decision (scoring/adaptive-block-decision (:adaptive-block question-block)
                                                  answers
                                                  completed-blocks)
        next-block-id (:next-block-id decision)]
    (if next-block-id
      (do
        (swap! app-state update :completed-blocks conj decision)
        (load-question-block! next-block-id (continuation-message decision) false))
      (finish-results! decision))))

(defn next-question []
  (let [{:keys [current-question-index questions]} @app-state]
    (if (= current-question-index (dec (count questions)))
      (complete-current-block!)
      (swap! app-state assoc
             :current-question-index (inc current-question-index)
             :answer-locked? false
             :feedback nil
             :item-started-at-ms (now-ms)))))

(defn level-option [{:keys [selected-level]} {:keys [id label]}]
  [:label {:class "app-level-option"}
   [:input {:type "radio"
            :name "starting-level"
            :value (name id)
            :checked (= selected-level id)
            :on-change #(swap! app-state assoc
                               :selected-level id
                               :questions-error nil)}]
   [:span label]])

(defn start-screen [{:keys [selected-level questions-loading? questions-error]}]
  [:main {:class "app-page"}
   [:section {:aria-labelledby "start-heading"
              :class "app-card grid gap-5 p-5 sm:gap-6 sm:p-7"}
    [:div {:class "grid gap-3"}
     [:p {:class "app-eyebrow"} "Polish sentence context"]
     [:h1 {:id "start-heading"
           :class "text-4xl font-bold leading-tight app-ink sm:text-5xl"}
      "Polish Passive Vocabulary Size Test"]
     [:p {:class "app-copy max-w-2xl text-base leading-7"}
      "Passive vocabulary means words you can recognize when reading or listening, even if you would not use them yourself yet. This test estimates how many Polish word meanings you recognize in sentences."]]
    [:div {:class "app-soft-panel grid gap-4 rounded-md border p-4 text-sm leading-6"}
     [:p [:strong {:class "font-semibold app-ink"} "Format: "]
      "You'll see a Polish sentence. Choose the highlighted word's English meaning."]
     [:p "Choose the level that feels closest to your Polish right now. This only helps pick the first few questions."]
     [:fieldset {:class "grid gap-3"
                 :role "radiogroup"
                 :aria-label "Starting level"}
      [:legend {:class "font-semibold app-ink"} "Starting level"]
      [:div {:class "grid gap-2 sm:grid-cols-2"}
       (for [option data/level-options]
         ^{:key (:id option)}
         [level-option {:selected-level selected-level} option])]]
     [:p (str data/sentence-block-size " sentence-context items. ~12 minutes.")]]
    (when questions-error
      [:p {:role "alert"
           :class "app-feedback-error rounded-md p-3 text-sm font-semibold"}
       questions-error])
    [:button {:type "button"
              :class button-class
              :disabled questions-loading?
              :on-click begin-test}
     (if questions-loading?
       "Loading sentence questions..."
       "Begin Test")]]])

(defn choice-button [question choices answer-locked? feedback choice]
  (let [correct-answer? (= (:label choice) (:correct-translation question))
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

(defn highlighted-sentence [{:keys [sentence target-surface highlight-span]}]
  (let [sentence (or sentence "")
        {:keys [start end]} highlight-span]
    (if (and (number? start)
             (number? end)
             (<= 0 start end (count sentence)))
      [:p {:role "group"
           :aria-label "Polish sentence"
           :class "app-sentence-text"}
       (subs sentence 0 start)
       [:mark {:role "term"
               :class "app-target-mark"}
        (subs sentence start end)]
       (subs sentence end)]
      [:p {:role "group"
           :aria-label "Polish sentence"
           :class "app-sentence-text"}
       target-surface
       " "
       sentence])))

(defn dont-know-button [question choices answer-locked?]
  [:button {:type "button"
            :class "min-h-12 rounded-md border px-4 py-3 text-left text-sm font-semibold break-words shadow-sm transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 app-choice-button"
            :disabled answer-locked?
            :on-click #(record-answer question choices dont-know-choice)}
   [:span (:label dont-know-choice)]
   " "
   [:span {:class "font-normal app-muted"}
    "(don't guess for a more accurate estimate, press this if unsure)"]])

(defn live-estimate-panel [results-data]
  (let [live-estimate (or (:live-estimate results-data)
                          {:ready? false
                           :label (scoring/pending-live-estimate-label)})]
    [:section {:aria-label "Live estimate"
               :aria-live "polite"
               :class "app-card app-subtle-bg grid gap-1 p-4 text-sm sm:p-5"
               :style {:margin-top "20px"}}
     [:p {:class "text-xs font-bold uppercase app-muted"}
      "Live estimate of how many dictionary forms of words you know"]
     [:p {:class "font-semibold app-ink-soft"}
      (:label live-estimate)]
     (when (:ready? live-estimate)
       [:p {:class "app-muted"}
        (:range-label live-estimate)])]))

(defn quiz-screen [{:keys [questions current-question-index question-choices answers answer-locked? feedback continuation-message results-data]}]
  (let [question (nth questions current-question-index)
        choices (or (get question-choices current-question-index)
                    (choice-options question))
        current-question-number (inc current-question-index)
        scored-count (count (filter #(= (:adaptive-block-id question)
                                        (:adaptive-block-id %))
                                    answers))
        total (count questions)
        progress (if (pos? total)
                   (* 100 (/ scored-count total))
                   0)]
    [:main {:class "app-page"}
     [:section {:aria-labelledby "question-heading"
                :class "app-card grid gap-5 p-5 sm:gap-6 sm:p-7"}
      (when continuation-message
        [:p {:role "status"
             :class "rounded-md app-subtle-bg p-3 text-sm font-semibold app-ink-soft"}
         continuation-message])
      [:div {:role "group"
             :aria-label "Quiz status"
             :class "flex flex-wrap items-center justify-between gap-3 text-sm font-semibold app-muted"}
       [:span (str scored-count " / " total " scored")]
       [:span (str "Item " current-question-number " of " total)]
       [:span {:class (str "rounded-full px-3 py-1 text-xs font-bold ring-1 " (band-style-class (:band question) :badge))}
        (or (adaptive-block-range-label (:adaptive-block-id question))
            (data/band-labels (:band question)))]]
      [:div {:class "h-2 overflow-hidden rounded-full app-subtle-bg"
             :role "progressbar"
             :aria-valuemin 0
             :aria-valuemax total
             :aria-valuenow scored-count}
       [:div {:class "app-progress-fill h-full rounded-full transition-all"
              :style {:width (str progress "%")}}]]
      [:div {:class "grid gap-4 text-center"}
       [:h2 {:id "question-heading"
             :class "text-2xl font-bold leading-tight app-ink sm:text-3xl"}
        "What does the "
        [:mark {:class "app-target-mark"} "highlighted"]
        " word in this sentence mean?"]
       [highlighted-sentence question]
       [:p {:class "text-base app-muted"} "Select the best English meaning"]]
      [:div {:class "grid gap-3"
             :role "group"
             :aria-label "Answer choices"}
       (for [choice choices]
         ^{:key (:label choice)}
         [choice-button question choices answer-locked? feedback choice])]
      [:div {:class "grid gap-2 border-t app-border pt-4"}
       [dont-know-button question choices answer-locked?]]
      (when feedback
        [:p {:class (if (= :correct (:kind feedback))
                      "app-feedback-success rounded-md p-3 text-sm font-semibold"
                      "app-feedback-error rounded-md p-3 text-sm font-semibold")}
         (:message feedback)])
      (when answer-locked?
        [:button {:type "button"
                  :class (str button-class " justify-self-stretch sm:justify-self-end")
                  :on-click next-question}
         "Next"])]
     [live-estimate-panel results-data]]))

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
          ^{:key (str (:adaptive-block-id answer) "-" question-index)}
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
      [:p {:class "rounded-full px-3 py-1 text-xs font-bold ring-1 app-subtle-bg app-ink-soft"}
       (str "Level band: " (:level-band results-data))]]
     [:div {:class "app-accent-panel grid gap-1 rounded-md border p-4"}
      [:p {:class "text-xs font-bold uppercase app-muted"}
       "Estimated recognized Polish lemmas"]
      [:p {:class "app-accent-text break-words text-4xl font-bold"}
       (scoring/estimate-display results-data)]
      [:p {:class "text-sm font-semibold app-ink-soft"}
       (str "Likely range: " (scoring/format-range (:likely-range results-data)))]]
     [:p {:class "break-words text-base font-semibold app-ink-soft"}
      (str "Approximate level band: " (:level-band results-data))]
     [:p {:class "break-words text-base leading-7 app-muted"}
      "Likely ranges are broad for short tests and narrow as more sentence-context evidence is added."]
     [:p {:class "break-words rounded-md app-subtle-bg p-3 text-sm leading-6 app-muted"}
      [:strong {:class "font-semibold app-ink"} "A note on Polish: "]
      "This test scores recognition of Polish lemmas in sentence context."]]
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
  (rdom/render @root [app]))
