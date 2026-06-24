(ns jamiepratt.vocab-test-client-side.core
  (:require
   [jamiepratt.vocab-test-client-side.data :as data]
   [jamiepratt.vocab-test-client-side.scoring :as scoring]
   [reagent.core :as r]
   [reagent.dom.client :as rdom]))

(def band-styles
  {:B1 {:bar "bg-[#b91c1c]"
        :badge "bg-red-100 text-red-900 ring-red-200"
        :panel "border-red-200 bg-red-50 text-red-950"
        :text "text-red-800"}
   :B2 {:bar "bg-[#c2410c]"
        :badge "bg-orange-100 text-orange-900 ring-orange-200"
        :panel "border-orange-200 bg-orange-50 text-orange-950"
        :text "text-orange-800"}
   :B3 {:bar "bg-[#a16207]"
        :badge "bg-yellow-100 text-yellow-900 ring-yellow-200"
        :panel "border-yellow-200 bg-yellow-50 text-yellow-950"
        :text "text-yellow-800"}
   :B4 {:bar "bg-[#0f766e]"
        :badge "bg-teal-100 text-teal-900 ring-teal-200"
        :panel "border-teal-200 bg-teal-50 text-teal-950"
        :text "text-teal-800"}
   :B5 {:bar "bg-[#0369a1]"
        :badge "bg-sky-100 text-sky-900 ring-sky-200"
        :panel "border-sky-200 bg-sky-50 text-sky-950"
        :text "text-sky-800"}
   :B6 {:bar "bg-[#6d28d9]"
        :badge "bg-violet-100 text-violet-900 ring-violet-200"
        :panel "border-violet-200 bg-violet-50 text-violet-950"
        :text "text-violet-800"}})

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

(defonce root
  (delay (rdom/create-root (.getElementById js/document "app"))))

(def button-class
  "inline-flex min-h-11 w-full items-center justify-center rounded-md bg-[#991b1b] px-4 py-3 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-[#7f1d1d] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#991b1b] disabled:cursor-not-allowed disabled:opacity-70 sm:w-auto")

(defonce questions-requested?
  (atom false))

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
  [:main {:class "min-h-screen bg-[#faf7f0] px-3 py-5 text-stone-950 sm:px-6 sm:py-8"}
   [:section {:aria-labelledby "start-heading"
              :class "mx-auto grid w-full max-w-2xl gap-5 rounded-lg border border-stone-200 bg-white p-5 shadow-lg shadow-stone-900/10 sm:gap-6 sm:p-7"}
    [:div {:class "grid gap-3"}
     [:p {:class "text-sm font-semibold uppercase text-[#991b1b]"} "Polish to English"]
     [:h1 {:id "start-heading"
           :class "text-4xl font-bold leading-tight text-stone-950 sm:text-5xl"}
      "Polish Vocabulary Test"]
     [:p {:class "max-w-2xl text-base leading-7 text-stone-700"}
      "Anchored low, ranging wide."]]
    [:div {:class "grid gap-4 rounded-md border border-[#e7d8c4] bg-[#fff8ec] p-4 text-sm leading-6 text-stone-700"}
     [:p [:strong {:class "font-semibold text-stone-950"} "Format: "]
      "You'll see a Polish word. Pick the correct English meaning from 4 choices."]
     [:p "Best for roughly 250-3,500+ passive Polish words. Below that it checks the basics; above that it mostly detects that you've hit this short test's ceiling."]
     [:div
      [:p {:class "font-semibold text-stone-950"} "80 words across 6 bands:"]
      [:ul {:class "mt-2 grid gap-1"}
       (for [{:keys [id summary]} data/bands]
         ^{:key id}
         [:li {:class (str "rounded-md border px-3 py-2 " (band-style-class id :panel))}
          summary])]]
     [:p [:strong {:class "font-semibold text-stone-950"} "Don't guess. "]
      "Pick \"don't know\" if unsure; it makes the estimate accurate."]
     [:p "~12 minutes."]]
    (when questions-error
      [:p {:role "alert"
           :class "rounded-md bg-red-50 p-3 text-sm font-semibold text-red-800"}
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
                      "border-emerald-600 bg-emerald-50 text-emerald-900"

                      (and answer-locked? selected-answer? (= :wrong selected-result))
                      "border-red-400 bg-red-50 text-red-900"

                      :else
                      "border-stone-300 bg-white text-stone-950 hover:border-[#991b1b] hover:bg-red-50 focus-visible:outline-[#991b1b]")]
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
    [:main {:class "min-h-screen bg-[#faf7f0] px-3 py-5 text-stone-950 sm:px-6 sm:py-8"}
     [:section {:aria-labelledby "question-word"
                :class "mx-auto grid w-full max-w-2xl gap-5 rounded-lg border border-stone-200 bg-white p-5 shadow-lg shadow-stone-900/10 sm:gap-6 sm:p-7"}
      [:div {:class "h-2 overflow-hidden rounded-full bg-stone-100"
             :role "progressbar"
             :aria-valuemin 1
             :aria-valuemax (count questions)
             :aria-valuenow current-question-number}
       [:div {:class "h-full rounded-full bg-[#991b1b] transition-all"
              :style {:width (str progress "%")}}]]
      [:div {:class "flex flex-wrap items-center justify-between gap-3 text-sm font-semibold text-stone-600"}
       [:span (str current-question-number " / " (count questions))]
       [:span {:class (str "rounded-full px-3 py-1 text-xs font-bold ring-1 " (band-style-class (:band question) :badge))}
        (data/band-labels (:band question))]]
      [:div {:class "grid gap-2 text-center"}
       [:h2 {:id "question-word"
             :class "break-words text-4xl font-bold leading-tight text-stone-950 sm:text-5xl"}
        (:word question)]
       [:p {:class "text-sm font-semibold uppercase text-stone-500"}
        (:word-class question)]
       [:p {:class "text-base text-stone-700"} "Select the correct meaning"]]
      [:div {:class "grid gap-3"}
       (for [choice choices]
         ^{:key (:label choice)}
         [choice-button question choices answer-locked? feedback choice])]
      (when feedback
        [:p {:class (if (= :correct (:kind feedback))
                      "rounded-md bg-emerald-50 p-3 text-sm font-semibold text-emerald-800"
                      "rounded-md bg-red-50 p-3 text-sm font-semibold text-red-800")}
         (:message feedback)])
      (when answer-locked?
        [:button {:type "button"
                  :class (str button-class " justify-self-stretch sm:justify-self-end")
                  :on-click next-question}
         "Next"])]]))

(defn band-result-row [results-data band-id]
  (let [{:keys [answered correct pct]} (get-in results-data [:band-stats band-id])]
    [:li {:class "grid min-w-0 gap-2 rounded-md border border-stone-200 p-3 text-sm sm:grid-cols-[5rem_1fr_6rem] sm:items-center"}
     [:span {:class (str "font-bold " (band-style-class band-id :text))} (data/band-labels band-id)]
     [:div {:class "h-2 overflow-hidden rounded-full bg-stone-100"
            :aria-hidden true}
      [:div {:class (str "h-full rounded-full " (band-style-class band-id :bar))
             :style {:width (str pct "%")}}]]
     [:span {:class "font-semibold text-stone-700"}
      (str correct "/" answered " (" pct "%)")]]))

(defn review-answer-row [{:keys [band word correct]}]
  [:li {:class "grid min-w-0 gap-1 rounded-md border border-stone-200 p-3 text-sm sm:grid-cols-3 sm:items-center"}
   [:span {:class (str "font-bold " (band-style-class band :text))} (data/band-labels band)]
   [:span {:class "break-words font-semibold text-stone-950"} word]
   [:span {:class "break-words text-stone-700"} correct]])

(defn review-section [review-answers]
  (when (seq review-answers)
    (let [heading (str "Words to review (" (count review-answers) ")")]
      [:section {:aria-labelledby "review-heading"
                 :class "grid gap-3"}
       [:h2 {:id "review-heading"
             :class "text-lg font-bold text-stone-950"}
        heading]
       [:ul {:class "grid gap-2"}
        (for [{:keys [question-index] :as answer} review-answers]
          ^{:key question-index}
          [review-answer-row answer])]])))

(defn results-screen [{:keys [results-data]}]
  [:main {:class "min-h-screen bg-[#faf7f0] px-3 py-5 text-stone-950 sm:px-6 sm:py-8"}
   [:section {:aria-labelledby "results-heading"
              :class "mx-auto grid w-full max-w-3xl gap-5 rounded-lg border border-stone-200 bg-white p-5 shadow-lg shadow-stone-900/10 sm:gap-6 sm:p-7"}
    [:div {:class "grid gap-2 text-center"}
     [:h1 {:id "results-heading"
           :class "text-4xl font-bold leading-tight text-stone-950"}
      "Results"]
     [:p {:class "text-6xl font-bold text-[#991b1b]"}
      (str (:accuracy-pct results-data) "%")]
     [:p {:class "text-base font-semibold text-stone-700"}
      (str (:correct results-data) " of " (:total results-data) " correct")]]
    [:div {:class "grid gap-2 text-sm font-semibold text-stone-700 sm:grid-cols-3"}
     [:p (str "Answered: " (:answered results-data))]
     [:p (str "Wrong: " (:wrong results-data))]
     [:p (str "Don't know: " (:dk results-data))]]
    [:section {:aria-labelledby "band-results-heading"
               :class "grid gap-3"}
     [:h2 {:id "band-results-heading"
           :class "text-lg font-bold text-stone-950"}
      "Accuracy by frequency band"]
     [:ul {:class "grid gap-2"}
      (for [band-id data/ordered-band-ids]
        ^{:key band-id}
        [band-result-row results-data band-id])]]
    [review-section (:review-answers results-data)]
    [:section {:aria-labelledby "estimate-heading"
               :class "grid gap-4 border-t border-stone-200 pt-6"}
     [:div {:class "flex flex-wrap items-center justify-between gap-3"}
      [:h2 {:id "estimate-heading"
            :class "text-lg font-bold text-stone-950"}
       "Vocabulary estimate"]
      [:p {:class (str "rounded-full px-3 py-1 text-xs font-bold ring-1 " (band-style-class (:ceiling-band results-data) :badge))}
       (str "Ceiling: " (data/band-labels (:ceiling-band results-data)))]]
     [:div {:class "grid gap-1 rounded-md border border-red-200 bg-red-50 p-4"}
      [:p {:class "text-xs font-bold uppercase text-stone-600"}
       "Estimated passive vocabulary"]
      [:p {:class "break-words text-4xl font-bold text-[#7f1d1d]"}
       (str "~" (:adjusted-estimate results-data) " words")]]
     [:p {:class "break-words text-base font-semibold text-stone-800"}
      (:comparison results-data)]
     [:p {:class "break-words text-base leading-7 text-stone-700"}
      (:interpretation results-data)]
     (when (:honesty-note results-data)
       [:p {:class "break-words rounded-md bg-stone-100 p-3 text-sm font-semibold text-stone-800"}
        (:honesty-note results-data)])
     [:p {:class "break-words rounded-md bg-stone-100 p-3 text-sm leading-6 text-stone-700"}
      [:strong {:class "font-semibold text-stone-950"} "A note on Polish: "]
      "This test shows words in their dictionary (nominative) form. Polish has 7 cases, so in real text these words appear with different endings (e.g. "
      [:em "woda -> wode -> woda -> wodzie"]
      "). Recognizing a word in its base form is easier than recognizing all its inflected forms, so your reading-comprehension vocabulary may feel smaller than this score suggests until the case system clicks."]
     [:p {:class "break-words rounded-md bg-stone-100 p-3 text-sm leading-6 text-stone-700"}
      "Passive vocabulary (recognition) is typically 2-3x active vocabulary (production). This test measures recognition only."]]
    [:button {:type "button"
              :class button-class
              :on-click begin-test}
     "Retake"]]])

(defn app []
  (let [state @app-state]
    (case (:screen state)
      :quiz [quiz-screen state]
      :results [results-screen state]
      [start-screen state])))

(defn ^:dev/after-load init []
  (when-not @questions-requested?
    (reset! questions-requested? true)
    (load-questions!))
  (rdom/render @root [app]))
