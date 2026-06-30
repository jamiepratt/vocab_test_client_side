(ns jamiepratt.vocab-test-client-side.core
  (:require
   [jamiepratt.vocab-test-client-side.data :as data]
   [jamiepratt.vocab-test-client-side.pages.adaptive-methodology :as adaptive-methodology]
   [jamiepratt.vocab-test-client-side.pages.current :as current]
   [jamiepratt.vocab-test-client-side.pages.features :as features]
   [jamiepratt.vocab-test-client-side.scoring :as scoring]
   [reagent.core :as r]
   [reagent.dom.client :as rdom]))

(def frequency-bucket-styles
  {:rank-1-250 {:bar "band-bar band-b1"
                :badge "band-badge band-b1"
                :panel "band-panel band-b1"
                :text "band-text band-b1"}
   :rank-251-500 {:bar "band-bar band-b2"
                  :badge "band-badge band-b2"
                  :panel "band-panel band-b2"
                  :text "band-text band-b2"}
   :rank-501-1000 {:bar "band-bar band-b3"
                   :badge "band-badge band-b3"
                   :panel "band-panel band-b3"
                   :text "band-text band-b3"}
   :rank-1001-2000 {:bar "band-bar band-b4"
                    :badge "band-badge band-b4"
                    :panel "band-panel band-b4"
                    :text "band-text band-b4"}
   :rank-2001-3500 {:bar "band-bar band-b5"
                    :badge "band-badge band-b5"
                    :panel "band-panel band-b5"
                    :text "band-text band-b5"}
   :rank-3501-plus {:bar "band-bar band-b6"
                    :badge "band-badge band-b6"
                    :panel "band-panel band-b6"
                    :text "band-text band-b6"}})

(defn frequency-bucket-style-class [bucket-id style-key]
  (get-in frequency-bucket-styles [bucket-id style-key]))

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

(def default-auto-scroll-delay-ms 10000)

(def auto-scroll-options
  [{:delay-ms 0
    :label "New question immediately"}
   {:delay-ms 3000
    :label "Review correct answer for 3 seconds"}
   {:delay-ms 5000
    :label "Review correct answer for 5 seconds"}
   {:delay-ms 10000
    :label "Review correct answer for 10 seconds"}])

(def auto-scroll-delay-values
  (set (map :delay-ms auto-scroll-options)))

(defn location-param [param-name]
  (let [search-params (js/URLSearchParams. (.-search js/location))
        hash (.-hash js/location)
        hash-query-index (.indexOf hash "?")
        hash-params (when (not= -1 hash-query-index)
                      (js/URLSearchParams. (subs hash hash-query-index)))]
    (or (when (.has search-params param-name)
          (.get search-params param-name))
        (when (and hash-params (.has hash-params param-name))
          (.get hash-params param-name)))))

(defn configured-auto-scroll-delay-ms []
  (if-let [value (or (location-param "scrollDelayMs")
                     (location-param "scroll-delay-ms"))]
    (let [parsed (js/parseInt value 10)]
      (if (and (not (js/isNaN parsed))
               (contains? auto-scroll-delay-values parsed))
        parsed
        default-auto-scroll-delay-ms))
    default-auto-scroll-delay-ms))

(defn configured-auto-scroll-behavior []
  (if (zero? (configured-auto-scroll-delay-ms))
    "auto"
    "smooth"))

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
   :scroll-delay-ms (configured-auto-scroll-delay-ms)
   :scroll-behavior (configured-auto-scroll-behavior)
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
    :href "#/features"}])

(def current-routes
  [{:id :current-testing
    :label "Testing"
    :href "#/current/testing"}
   {:id :current-scoring
    :label "Scoring"
    :href "#/current/scoring"}])

(def theory-routes
  [{:id :methodology
    :label "Progressive methodology"
    :href "#/methodology"}
   {:id :adaptive-methodology
    :label "Adaptive methodology"
    :href "#/adaptive-methodology"}])

(def routes
  (vec (concat primary-routes current-routes theory-routes)))

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

(defonce current-menu-open?
  (r/atom false))

(defonce theory-menu-open?
  (r/atom false))

(defn set-theme! [theme-id]
  (reset! current-theme theme-id)
  (try
    (.setItem js/localStorage theme-storage-key (name theme-id))
    (catch :default _ nil)))

(defonce route-listener-registered?
  (atom false))

(defonce resize-listener-registered?
  (atom false))

(defonce root
  (delay (rdom/create-root (.getElementById js/document "app"))))

(def results-panel-id "results-panel")

(defonce auto-scroll-cleanup
  (atom nil))

(defonce auto-scroll-pending-target-id
  (atom nil))

(defonce measured-elements
  (atom {}))

(defn question-card-id [question-index]
  (str "question-card-" (inc question-index)))

(defn set-layout-height-var! [var-name element]
  (when element
    (let [height (.-height (.getBoundingClientRect element))]
      (when (pos? height)
        (.setProperty (.-style (.-documentElement js/document))
                      var-name
                      (str height "px"))))))

(defn remember-measured-element! [var-name element]
  (if element
    (do
      (swap! measured-elements assoc var-name element)
      (set-layout-height-var! var-name element))
    (swap! measured-elements dissoc var-name)))

(defn refresh-layout-measurements! []
  (doseq [[var-name element] @measured-elements]
    (set-layout-height-var! var-name element)))

(defn clear-auto-scroll! []
  (when-let [cleanup @auto-scroll-cleanup]
    (cleanup))
  (reset! auto-scroll-cleanup nil)
  (reset! auto-scroll-pending-target-id nil))

(defn scroll-to-target! [target-id behavior]
  (when-let [element (.getElementById js/document target-id)]
    (.scrollIntoView element (clj->js {:behavior behavior
                                       :block "start"}))
    true))

(defn schedule-auto-scroll!
  ([target-id]
   (schedule-auto-scroll! target-id
                          (:scroll-delay-ms @app-state)
                          (:scroll-behavior @app-state)))
  ([target-id delay-ms]
   (schedule-auto-scroll! target-id delay-ms (:scroll-behavior @app-state)))
  ([target-id delay-ms behavior]
   (clear-auto-scroll!)
   (let [delay-ms (or delay-ms default-auto-scroll-delay-ms)
         behavior (or behavior "smooth")
         cancelled? (atom false)
         timeout-id (atom nil)
         raf-id (atom nil)
         cancel-events ["scroll" "wheel" "touchstart" "keydown"]]
     (letfn [(remove-listeners! []
               (doseq [event-name cancel-events]
                 (.removeEventListener js/window event-name cancel! false)))
             (finish! []
               (when-let [id @timeout-id]
                 (js/clearTimeout id))
               (when-let [id @raf-id]
                 (js/cancelAnimationFrame id))
               (remove-listeners!)
               (reset! auto-scroll-cleanup nil)
               (reset! auto-scroll-pending-target-id nil))
             (cancel! [_]
               (reset! cancelled? true)
               (finish!))
             (scroll! []
               (when-not @cancelled?
                 (if (scroll-to-target! target-id behavior)
                   (finish!)
                   (reset! timeout-id (js/setTimeout scroll! 50)))))]
       (reset! auto-scroll-cleanup #(do
                                      (reset! cancelled? true)
                                      (finish!)))
       (reset! auto-scroll-pending-target-id target-id)
       (reset! raf-id
               (js/requestAnimationFrame
                (fn []
                  (when (pos? delay-ms)
                    (doseq [event-name cancel-events]
                      (.addEventListener js/window event-name cancel! false)))
                  (reset! timeout-id
                          (js/setTimeout scroll! delay-ms)))))))))

(def button-class
  "app-primary-button sm:w-auto")

(defn api-base-url []
  (let [config (aget js/window "VOCAB_CONFIG")]
    (or (some-> config (aget "apiBaseUrl"))
        "")))

(defn now-ms []
  (.now js/Date))

(defn rank-bound-label [value]
  (scoring/format-count value))

(defn rank-window-label [surface-rank-start surface-rank-end]
  (when (and surface-rank-start surface-rank-end)
    (str (rank-bound-label surface-rank-start)
         "-"
         (rank-bound-label surface-rank-end))))

(defn normalize-sentence-item [adaptive-block-id index item]
  (assoc item
         :adaptive-block-id adaptive-block-id
         :frequency-bucket (data/frequency-bucket-id-for-rank
                            (:surface-difficulty-rank item))
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
   :lemma-inventory-stratum (:lemma-inventory-stratum answer)
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

(defn load-question-block! [adaptive-block-id continuation-message reset-session? & [scroll-after-load?]]
  (let [selected-level (:selected-level @app-state)
        adaptive-block (data/adaptive-block adaptive-block-id)]
    (when reset-session?
      (clear-auto-scroll!))
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
                     (do
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
                       (when scroll-after-load?
                         (schedule-auto-scroll!
                          (question-card-id (count (:answers @app-state)))
                          (if reset-session?
                            0
                            (:scroll-delay-ms @app-state)))))
                     (swap! app-state assoc
                            :questions-loading? false
                            :questions-error "Could not load sentence questions.")))))
        (.catch (fn [_]
                  (swap! app-state assoc
                         :questions-loading? false
                         :questions-error "Could not load sentence questions."))))))

(defn begin-test []
  (clear-auto-scroll!)
  (let [selected-level (:selected-level @app-state)
        starting-block (data/starting-block selected-level)]
    (load-question-block! (:id starting-block) nil true)))

(defn feedback-for [choice question]
  (case (:result choice)
    :correct {:kind :correct
              :selected (:label choice)
              :message "Correct"}
    :dk {:kind :dk
         :selected (:label choice)
         :message (str "Correct answer: " (:correct-translation question))}
    :wrong {:kind :wrong
            :selected (:label choice)
            :message (str "Correct answer: " (:correct-translation question))}))

(declare complete-current-block!)

(defn record-answer [question choices choice]
  (let [submitted-answer (atom nil)
        scroll-target-id (atom nil)
        complete-block? (atom false)]
    (swap! app-state
           (fn [{:keys [answer-locked? current-question-index answers
                        session-questions item-started-at-ms question-block questions] :as state}]
             (if (or answer-locked?
                     (not= (:item-id question)
                           (:item-id (nth questions current-question-index nil))))
               state
               (let [answered-at-ms (now-ms)
                     result (:result choice)
                     correct-answer (:correct-translation question)
                     answer {:question-index current-question-index
                             :item-id (:item-id question)
                             :sentence (:sentence question)
                             :sentence-translation (:sentence-translation question)
                             :target-surface (:target-surface question)
                             :target-surface-form-id (:target-surface-form-id question)
                             :highlight-span (:highlight-span question)
                             :lemma-id (:lemma-id question)
                             :lemma-pos-id (:lemma-pos-id question)
                             :candidate-rank (:surface-difficulty-rank question)
                             :lemma-inventory-stratum (:lemma-inventory-stratum question)
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
                             :frequency-bucket (:frequency-bucket question)
                             :word (:target-surface question)}
                     next-answers (conj answers answer)
                     last-question? (= current-question-index (dec (count questions)))]
                 (reset! submitted-answer answer)
                 (if last-question?
                   (do
                     (reset! complete-block? true)
                     (assoc state
                            :answers next-answers
                            :answer-locked? true
                            :feedback (feedback-for choice question)
                            :results-data (scoring/summarize-results session-questions next-answers)))
                   (do
                     (reset! scroll-target-id (question-card-id (count next-answers)))
                     (assoc state
                            :answers next-answers
                            :current-question-index (inc current-question-index)
                            :answer-locked? false
                            :feedback nil
                            :results-data (scoring/summarize-results session-questions next-answers)
                            :item-started-at-ms (now-ms))))))))
    (when-let [answer @submitted-answer]
      (submit-answer-event! (:anonymous-session-id @app-state) answer)
      (if @complete-block?
        (complete-current-block! true)
        (schedule-auto-scroll! @scroll-target-id)))))

(defn continuation-message [decision]
  (case (:action decision)
    :route-lower "The first block was too hard, so this test is continuing with easier sentence items."
    :route-higher "The first block was too easy, so this test is continuing with harder sentence items."
    nil))

(defn finish-results! [decision & [scroll-after-finish?]]
  (swap! app-state
         (fn [{:keys [answers session-questions] :as state}]
           (assoc state
                  :screen :results
                  :continuation-message nil
                  :results-data (scoring/summarize-results session-questions answers decision))))
  (when scroll-after-finish?
    (schedule-auto-scroll! results-panel-id)))

(defn complete-current-block! [& [scroll-after-complete?]]
  (let [{:keys [question-block answers completed-blocks]} @app-state
        decision (scoring/adaptive-block-decision (:adaptive-block question-block)
                                                  answers
                                                  completed-blocks)
        next-block-id (:next-block-id decision)]
    (if next-block-id
      (do
        (swap! app-state update :completed-blocks conj decision)
        (load-question-block! next-block-id (continuation-message decision) false scroll-after-complete?))
      (finish-results! decision scroll-after-complete?))))

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

                      answer-locked?
                      "app-choice-disabled"

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

(defn sentence-translation [question]
  (when-let [translation (not-empty (:sentence-translation question))]
    [:p {:role "group"
         :aria-label "English translation"
         :class "app-sentence-translation"}
     translation]))

(defn dont-know-button [question choices answer-locked? feedback]
  (let [selected-dk? (= (:label dont-know-choice) (:selected feedback))
        base-class "min-h-12 rounded-md border px-4 py-3 text-left text-sm font-semibold break-words shadow-sm transition-colors focus-visible:outline-2 focus-visible:outline-offset-2"
        state-class (cond
                      (and answer-locked? selected-dk?)
                      "app-choice-dk"

                      answer-locked?
                      "app-choice-disabled"

                      :else
                      "app-choice-button")]
    [:button {:type "button"
              :class (str base-class " " state-class)
              :disabled answer-locked?
              :on-click #(record-answer question choices dont-know-choice)}
     [:span (:label dont-know-choice)]
     " "
     [:span {:class "font-normal app-muted"}
      "(don't guess for a more accurate estimate, press this if unsure)"]]))

(defn live-estimate-panel [results-data]
  (let [live-estimate (or (:live-estimate results-data)
                          {:ready? false
                           :label (scoring/pending-live-estimate-label)})]
    [:section {:aria-label "Live estimate"
               :aria-live "polite"
               :class "app-card app-live-estimate app-subtle-bg grid gap-1 p-4 text-sm sm:p-5"}
     [:p {:class "text-xs font-bold uppercase app-muted"}
      "Live estimate of how many dictionary forms of words you know"]
     [:p {:class "font-semibold app-ink-soft"}
      (:label live-estimate)]
     (when (:ready? live-estimate)
       [:p {:class "app-muted"}
        (:range-label live-estimate)])]))

(defn answer-feedback [answer]
  (when answer
    (if (:correct? answer)
      {:kind :correct
       :selected (:selected-answer answer)
       :message "Correct"}
      {:kind (:result answer)
       :selected (:selected-answer answer)
       :message (str "Correct answer: " (:correct-answer answer))})))

(defn answered-choices [answer]
  (mapv (fn [label]
          {:label label})
        (:choices answer)))

(defn active-question? [state question-index]
  (and (= :quiz (:screen state))
       (= question-index (count (:answers state)))))

(defn card-choices [{:keys [question-choices current-question-index]} question answer active?]
  (cond
    answer
    (answered-choices answer)

    active?
    (or (get question-choices current-question-index)
        (choice-options question))

    :else
    []))

(defn update-auto-scroll-delay! [delay-ms]
  (when (contains? auto-scroll-delay-values delay-ms)
    (swap! app-state assoc :scroll-delay-ms delay-ms)
    (when-let [target-id @auto-scroll-pending-target-id]
      (schedule-auto-scroll! target-id delay-ms))))

(defn auto-scroll-control [scroll-delay-ms]
  (let [selected-delay-ms (if (contains? auto-scroll-delay-values scroll-delay-ms)
                            scroll-delay-ms
                            default-auto-scroll-delay-ms)]
    [:label {:class "app-auto-scroll-control"
             :for "auto-scroll-delay"}
     [:span {:class "app-auto-scroll-label"} "Auto-scroll"]
     [:select {:id "auto-scroll-delay"
               :class "app-auto-scroll-select"
               :aria-label "Auto-scroll behavior"
               :value (str selected-delay-ms)
               :on-change #(update-auto-scroll-delay!
                            (js/parseInt (.-value (.-currentTarget %)) 10))}
      (for [{:keys [delay-ms label]} auto-scroll-options]
        ^{:key delay-ms}
        [:option {:value (str delay-ms)} label])]]))

(defn block-scored-count [answers question]
  (count (filter #(= (:adaptive-block-id question)
                     (:adaptive-block-id %))
                 answers)))

(defn quiz-status-card [{:keys [questions current-question-index answers
                                continuation-message question-block] :as state}]
  (let [question (or (nth questions current-question-index nil)
                     (last (:session-questions state)))
        scored-count (if question
                       (block-scored-count answers question)
                       (count answers))
        total (max 1 (count questions))
        current-question-number (min total (inc current-question-index))
        progress (* 100 (/ scored-count total))]
    [:section {:aria-label "Quiz status"
               :ref #(remember-measured-element! "--app-status-offset" %)
               :class "app-card app-status-card grid gap-3 p-4 text-sm sm:p-5"}
     (when continuation-message
       [:p {:role "status"
            :class "rounded-md app-subtle-bg p-3 font-semibold app-ink-soft"}
        continuation-message])
     [:div {:role "group"
            :aria-label "Quiz status details"
            :class "flex flex-wrap items-center justify-between gap-3 font-semibold app-muted"}
      [:span (str scored-count " / " total " scored")]
      [:span (str "Item " current-question-number " of " total)]
      (when question-block
        [:span {:class (str "rounded-full px-3 py-1 text-xs font-bold ring-1 "
                            (frequency-bucket-style-class
                             (:frequency-bucket question)
                             :badge))}
         (rank-window-label (:surface-rank-start question-block)
                            (:surface-rank-end question-block))])]
     [:div {:class "h-2 overflow-hidden rounded-full app-subtle-bg"
            :role "progressbar"
            :aria-valuemin 0
            :aria-valuemax total
            :aria-valuenow scored-count}
      [:div {:class "app-progress-fill h-full rounded-full transition-all"
             :style {:width (str progress "%")}}]]]))

(defn question-card [state question-index question]
  (let [answer (get (:answers state) question-index)
        active? (active-question? state question-index)
        choices (card-choices state question answer active?)
        locked? (boolean answer)
        feedback (answer-feedback answer)
        heading-id (str "question-heading-" (inc question-index))]
    [:section {:id (question-card-id question-index)
               :aria-labelledby heading-id
               :class (str "app-card app-question-card app-flow-target grid gap-5 p-5 sm:gap-6 sm:p-7"
                           (when locked? " app-question-card-answered"))}
     [:div {:class "grid gap-4 text-center"}
      [:h2 {:id heading-id
            :class "text-2xl font-bold leading-tight app-ink sm:text-3xl"}
       "What does the "
       [:mark {:class "app-target-mark"} "highlighted"]
       " word in this sentence mean?"]
      [highlighted-sentence question]
      (when locked?
        [sentence-translation question])]
     [:div {:class "grid gap-3"
            :role "group"
            :aria-label "Answer choices"}
      (for [choice choices]
        ^{:key (:label choice)}
        [choice-button question choices locked? feedback choice])]
     [:div {:class "grid gap-2 border-t app-border pt-4"}
      [dont-know-button question choices locked? feedback]]
     (when feedback
       [:p {:class (case (:kind feedback)
                     :correct "app-feedback-success rounded-md p-3 text-sm font-semibold"
                     :dk "app-feedback-dk rounded-md p-3 text-sm font-semibold"
                     "app-feedback-error rounded-md p-3 text-sm font-semibold")}
        (:message feedback)])]))

(defn visible-session-questions [{:keys [screen session-questions answers]}]
  (let [visible-count (if (= :results screen)
                        (count answers)
                        (min (count session-questions) (inc (count answers))))]
    (take visible-count session-questions)))

(declare results-screen)

(defn quiz-screen [{:keys [screen scroll-delay-ms results-data
                           questions-loading? questions-error] :as state}]
  [:main {:class "app-page app-quiz-page"}
   [quiz-status-card state]
   [:div {:class "app-quiz-stack"}
    (doall
     (map-indexed
      (fn [question-index question]
        ^{:key (str (:adaptive-block-id question) "-" (:item-id question) "-" question-index)}
        [question-card state question-index question])
      (visible-session-questions state)))
    (when (and (= :quiz screen)
               (seq (:session-questions state))
               (not questions-loading?)
               (nil? questions-error))
      [:div {:class "app-active-controls app-flow-target"}
       [auto-scroll-control scroll-delay-ms]
       [live-estimate-panel results-data]])
    (when questions-loading?
      [:section {:id "loading-card"
                 :class "app-card app-flow-target grid gap-2 p-5 text-sm font-semibold app-muted"}
       "Loading sentence questions..."])
    (when questions-error
      [:p {:role "alert"
           :class "app-card app-feedback-error rounded-md p-3 text-sm font-semibold"}
       questions-error])
    (when (= :results screen)
      [results-screen state])]])

(defn posterior-stratum-status-label [status]
  (case status
    :assumed-known-lower "assumed known from higher-rank pass"
    :observed "observed"
    (name status)))

(defn posterior-stratum-count-label [{:keys [answered correct status]}]
  (if (= :assumed-known-lower status)
    "not directly tested"
    (str correct "/" answered)))

(defn posterior-stratum-rank-label [{:keys [rank-start rank-end]}]
  (str "Lemma ranks "
       (scoring/format-count rank-start)
       "-"
       (scoring/format-count rank-end)))

(defn posterior-stratum-estimate-label [{:keys [estimate likely-range]}]
  (str "est. "
       (scoring/format-count estimate)
       " (range "
       (scoring/format-range likely-range)
       ")"))

(defn posterior-stratum-result-row [{:keys [status] :as row}]
  [:li {:class "flex min-w-0 flex-wrap items-center gap-2 rounded-md border app-border p-3 text-sm font-semibold"}
   [:span {:class "font-bold app-ink"}
    (posterior-stratum-rank-label row)]
   [:span {:class "app-muted"} " | "]
   [:span {:class "app-muted"}
    (posterior-stratum-status-label status)]
   [:span {:class "app-muted"} " | "]
   [:span {:class "app-ink-soft"}
    (posterior-stratum-count-label row)]
   [:span {:class "app-muted"} " | "]
   [:span {:class "app-accent-text"}
    (posterior-stratum-estimate-label row)]])

(defn lemma-rank-results-section [posterior-strata]
  (when (seq posterior-strata)
    [:section {:aria-labelledby "lemma-rank-results-heading"
               :class "grid gap-3"}
     [:h2 {:id "lemma-rank-results-heading"
           :class "text-lg font-bold app-ink"}
      "Vocabulary estimate by lemma rank"]
     [:ul {:class "grid gap-2"}
      (for [{:keys [id] :as row} posterior-strata]
        ^{:key id}
        [posterior-stratum-result-row row])]]))

(defn review-answer-row [{:keys [frequency-bucket word correct]}]
  [:li {:class "grid min-w-0 gap-1 rounded-md border app-border p-3 text-sm sm:grid-cols-3 sm:items-center"}
   [:span {:class (str "font-bold "
                       (frequency-bucket-style-class frequency-bucket :text))}
    (data/frequency-bucket-labels frequency-bucket)]
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
  [:section {:id results-panel-id
             :aria-labelledby "results-heading"
             :class "app-card app-card-wide app-flow-target grid gap-5 p-5 sm:gap-6 sm:p-7"}
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
   [lemma-rank-results-section (:posterior-strata results-data)]
   [review-section (:review-answers results-data)]
   [:section {:aria-labelledby "estimate-heading"
              :class "grid gap-4 border-t app-border pt-6"}
    [:div {:class "flex flex-wrap items-center justify-between gap-3"}
     [:h2 {:id "estimate-heading"
           :class "text-lg font-bold app-ink"}
      "Vocabulary estimate"]
     [:p {:class "rounded-full px-3 py-1 text-xs font-bold ring-1 app-subtle-bg app-ink-soft"
          :aria-label (str "Approximate level: " (:estimate-level results-data))}
      (:estimate-level results-data)]]
    [:div {:class "app-accent-panel grid gap-1 rounded-md border p-4"}
     [:p {:class "text-xs font-bold uppercase app-muted"}
      "Estimated recognized Polish lemmas"]
     [:p {:class "app-accent-text break-words text-4xl font-bold"}
      (scoring/estimate-display results-data)]
     [:p {:class "text-sm font-semibold app-ink-soft"}
      (str "Likely range: " (scoring/format-range (:likely-range results-data)))]]
    [:p {:class "break-words text-base font-semibold app-ink-soft"}
     (str "Approximate level: " (:estimate-level results-data))]
    [:p {:class "break-words text-base leading-7 app-muted"}
     "Likely ranges are broad for short tests and narrow as more sentence-context evidence is added."]
    [:p {:class "break-words rounded-md app-subtle-bg p-3 text-sm leading-6 app-muted"}
     [:strong {:class "font-semibold app-ink"} "A note on Polish: "]
     "This test scores recognition of Polish lemmas in sentence context."]]
   [:button {:type "button"
             :class button-class
             :on-click begin-test}
    "Retake"]])

(defn active-menu-route [route menu-routes]
  (some #(when (= route (:id %)) %) menu-routes))

(defn close-nav-menus! []
  (reset! current-menu-open? false)
  (reset! theory-menu-open? false))

(defn nav-link [current-route {:keys [id label href]}]
  [:a {:href href
       :aria-controls "page-content"
       :aria-current (when (= current-route id) "page")
       :on-click #(close-nav-menus!)
       :class "app-menu-link"}
   label])

(defn dropdown-link [current-route {:keys [id label href]}]
  [:a {:href href
       :aria-controls "page-content"
       :aria-current (when (= current-route id) "page")
       :on-click #(close-nav-menus!)
       :class "app-dropdown-link"}
   label])

(defn dropdown-menu [current-route {:keys [summary routes open-state class]}]
  (let [active-route (active-menu-route current-route routes)
        active? (boolean active-route)
        summary-label (if active-route
                        (str summary " › " (:label active-route))
                        summary)]
    [:details {:class (str "app-theory-menu " class)
               :open @open-state
               :on-toggle #(reset! open-state (.-open (.-currentTarget %)))}
     [:summary {:class "app-menu-summary"
                :aria-current (when active? "page")}
      summary-label]
     [:div {:class "app-dropdown"}
      (for [route routes]
        ^{:key (:id route)}
        [dropdown-link current-route route])]]))

(defn current-menu [current-route]
  [dropdown-menu current-route {:summary "Current"
                                :routes current-routes
                                :open-state current-menu-open?
                                :class "app-current-menu"}])

(defn theory-menu [current-route]
  [dropdown-menu current-route {:summary "Theory"
                                :routes theory-routes
                                :open-state theory-menu-open?
                                :class "app-methodology-menu"}])

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
  [:header {:class "app-topbar"
            :ref #(remember-measured-element! "--app-topbar-offset" %)}
   [:div {:class "app-topbar-inner"}
    [:div {:class "app-brand"} "Polish Vocabulary"]
    [:nav {:aria-label "Main"
           :class "app-menu"}
     (for [route primary-routes]
       ^{:key (:id route)}
       [nav-link current-route route])
     [current-menu current-route]
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
      "Before calibration exists, the test samples broadly across frequency buckets rather than adapting too early."]
     [:div {:class "app-soft-panel grid gap-2 rounded-md border p-4 text-sm leading-6"}
      [:p "60-80 real words"]
      [:p "5-10 quality checks"]
      [:p "Several frequency buckets"]
      [:p "Randomized order"]
      [:p "Occasional meaning checks after claimed-known answers"]]]
    [methodology-section
     "scoring"
     "Stage 1 scoring"
     [:p {:class "text-sm leading-6 app-muted"}
      "Use frequency-bucket scoring: calculate hit rate per bucket, correct overclaiming with fake-word false alarms, clamp corrected hit rate to 0-1, then sum bucket size times corrected hit rate."]
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

(defn test-screen [state]
  (case (:screen state)
    :quiz [quiz-screen state]
    :results [quiz-screen state]
    [start-screen state]))

(defn routed-screen [route state]
  (case route
    :current-testing [current/testing-page]
    :current-scoring [current/scoring-page]
    :methodology [methodology-screen]
    :adaptive-methodology [adaptive-methodology/page]
    :features [features/page]
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
                                                 (close-nav-menus!)
                                                 (reset! current-route (route-from-location)))))
  (when-not @resize-listener-registered?
    (reset! resize-listener-registered? true)
    (.addEventListener js/window "resize" refresh-layout-measurements!))
  (reset! current-route (route-from-location))
  (rdom/render @root [app]))
