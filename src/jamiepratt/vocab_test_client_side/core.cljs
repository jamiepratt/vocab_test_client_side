(ns jamiepratt.vocab-test-client-side.core
  (:require
   [reagent.core :as r]
   [reagent.dom.client :as rdom]))

(def bands
  [{:id :B1
    :label "0-250"
    :summary "Band 1 - top 250 (sanity) - 12 words"}
   {:id :B2
    :label "250-500"
    :summary "Band 2 - 250-500 (your estimate) - 16 words"}
   {:id :B3
    :label "500-1K"
    :summary "Band 3 - 500-1,000 - 18 words"}
   {:id :B4
    :label "1K-2K"
    :summary "Band 4 - 1,000-2,000 - 16 words"}
   {:id :B5
    :label "2K-3.5K"
    :summary "Band 5 - 2,000-3,500 - 10 words"}
   {:id :B6
    :label "3.5K+"
    :summary "Band 6 - 3,500+ (ceiling) - 8 words"}])

(def band-labels
  (into {} (map (juxt :id :label) bands)))

(def questions
  [{:word "woda" :word-class "noun" :band :B1 :correct "water" :wrong ["fire" "air" "earth"]}
   {:word "jeść" :word-class "verb" :band :B1 :correct "to eat" :wrong ["to drink" "to sleep" "to walk"]}
   {:word "duży" :word-class "adj" :band :B1 :correct "big / large" :wrong ["small" "fast" "heavy"]}
   {:word "dom" :word-class "noun" :band :B1 :correct "house / home" :wrong ["car" "road" "tree"]}
   {:word "dobry" :word-class "adj" :band :B1 :correct "good" :wrong ["bad" "new" "old"]}
   {:word "pies" :word-class "noun" :band :B1 :correct "dog" :wrong ["cat" "bird" "fish"]}
   {:word "czytać" :word-class "verb" :band :B1 :correct "to read" :wrong ["to write" "to speak" "to listen"]}
   {:word "dzień" :word-class "noun" :band :B1 :correct "day" :wrong ["night" "week" "month"]}
   {:word "pić" :word-class "verb" :band :B1 :correct "to drink" :wrong ["to eat" "to cook" "to wash"]}
   {:word "mały" :word-class "adj" :band :B1 :correct "small / little" :wrong ["big" "tall" "wide"]}
   {:word "dziękuję" :word-class "interj" :band :B1 :correct "thank you" :wrong ["please" "sorry" "hello"]}
   {:word "kobieta" :word-class "noun" :band :B1 :correct "woman" :wrong ["man" "child" "friend"]}
   {:word "szybko" :word-class "adverb" :band :B2 :correct "quickly / fast" :wrong ["slowly" "quietly" "carefully"]}
   {:word "kupować" :word-class "verb" :band :B2 :correct "to buy" :wrong ["to sell" "to give" "to pay"]}
   {:word "miasto" :word-class "noun" :band :B2 :correct "city / town" :wrong ["village" "country" "street"]}
   {:word "pieniądze" :word-class "noun" :band :B2 :correct "money" :wrong ["time" "work" "food"]}
   {:word "szczęśliwy" :word-class "adj" :band :B2 :correct "happy" :wrong ["sad" "angry" "tired"]}
   {:word "zawsze" :word-class "adverb" :band :B2 :correct "always" :wrong ["never" "sometimes" "often"]}
   {:word "zamknąć" :word-class "verb" :band :B2 :correct "to close / to shut" :wrong ["to open" "to push" "to pull"]}
   {:word "gorący" :word-class "adj" :band :B2 :correct "hot" :wrong ["cold" "warm" "wet"]}
   {:word "trudny" :word-class "adj" :band :B2 :correct "difficult / hard" :wrong ["easy" "simple" "soft"]}
   {:word "mówić" :word-class "verb" :band :B2 :correct "to speak / to say" :wrong ["to listen" "to read" "to think"]}
   {:word "spać" :word-class "verb" :band :B2 :correct "to sleep" :wrong ["to wake" "to dream" "to rest"]}
   {:word "jutro" :word-class "adverb" :band :B2 :correct "tomorrow" :wrong ["yesterday" "today" "now"]}
   {:word "praca" :word-class "noun" :band :B2 :correct "work / job" :wrong ["play" "rest" "study"]}
   {:word "zimny" :word-class "adj" :band :B2 :correct "cold" :wrong ["hot" "warm" "dry"]}
   {:word "droga" :word-class "noun/adj" :band :B2 :correct "road / way / expensive (fem.)" :wrong ["bridge" "building" "cheap"]}
   {:word "biec" :word-class "verb" :band :B2 :correct "to run" :wrong ["to walk" "to jump" "to sit"]}
   {:word "pamiętać" :word-class "verb" :band :B3 :correct "to remember" :wrong ["to forget" "to learn" "to know"]}
   {:word "zapomnieć" :word-class "verb" :band :B3 :correct "to forget" :wrong ["to remember" "to lose" "to leave"]}
   {:word "wyjaśnić" :word-class "verb" :band :B3 :correct "to explain" :wrong ["to ask" "to answer" "to translate"]}
   {:word "spotkać" :word-class "verb" :band :B3 :correct "to meet" :wrong ["to leave" "to find" "to call"]}
   {:word "zdrowie" :word-class "noun" :band :B3 :correct "health" :wrong ["sickness" "strength" "life"]}
   {:word "pogoda" :word-class "noun" :band :B3 :correct "weather" :wrong ["climate" "temperature" "season"]}
   {:word "niebezpieczny" :word-class "adj" :band :B3 :correct "dangerous" :wrong ["safe" "careful" "scary"]}
   {:word "prawie" :word-class "adverb" :band :B3 :correct "almost / nearly" :wrong ["exactly" "completely" "rarely"]}
   {:word "zmęczony" :word-class "adj" :band :B3 :correct "tired" :wrong ["hungry" "bored" "sad"]}
   {:word "wiedza" :word-class "noun" :band :B3 :correct "knowledge" :wrong ["power" "wisdom" "memory"]}
   {:word "rynek" :word-class "noun" :band :B3 :correct "market / town square" :wrong ["shop" "mall" "bank"]}
   {:word "oczywiście" :word-class "adverb" :band :B3 :correct "of course / obviously" :wrong ["maybe" "rarely" "secretly"]}
   {:word "czysty" :word-class "adj" :band :B3 :correct "clean / pure" :wrong ["dirty" "wet" "empty"]}
   {:word "silny" :word-class "adj" :band :B3 :correct "strong" :wrong ["weak" "tall" "fast"]}
   {:word "martwić się" :word-class "verb" :band :B3 :correct "to worry" :wrong ["to relax" "to celebrate" "to hurry"]}
   {:word "pomysł" :word-class "noun" :band :B3 :correct "idea" :wrong ["problem" "answer" "plan"]}
   {:word "obowiązek" :word-class "noun" :band :B3 :correct "duty / obligation" :wrong ["right" "hobby" "reward"]}
   {:word "uśmiechać się" :word-class "verb" :band :B3 :correct "to smile" :wrong ["to cry" "to laugh" "to frown"]}
   {:word "doświadczenie" :word-class "noun" :band :B4 :correct "experience" :wrong ["experiment" "accident" "education"]}
   {:word "wpływ" :word-class "noun" :band :B4 :correct "influence / impact" :wrong ["entrance" "income" "result"]}
   {:word "unikać" :word-class "verb" :band :B4 :correct "to avoid" :wrong ["to confront" "to accept" "to seek"]}
   {:word "wymagać" :word-class "verb" :band :B4 :correct "to require / to demand" :wrong ["to offer" "to suggest" "to refuse"]}
   {:word "zarządzać" :word-class "verb" :band :B4 :correct "to manage / to administer" :wrong ["to obey" "to observe" "to abandon"]}
   {:word "środowisko" :word-class "noun" :band :B4 :correct "environment" :wrong ["equipment" "entertainment" "experiment"]}
   {:word "społeczeństwo" :word-class "noun" :band :B4 :correct "society" :wrong ["company" "community center" "relationship"]}
   {:word "zniszczyć" :word-class "verb" :band :B4 :correct "to destroy" :wrong ["to build" "to repair" "to protect"]}
   {:word "oszukać" :word-class "verb" :band :B4 :correct "to deceive / to cheat" :wrong ["to help" "to warn" "to forgive"]}
   {:word "wydatki" :word-class "noun" :band :B4 :correct "expenses / spending" :wrong ["income" "savings" "profit"]}
   {:word "sprzeciwiać się" :word-class "verb" :band :B4 :correct "to oppose / to object" :wrong ["to agree" "to support" "to suggest"]}
   {:word "naprawić" :word-class "verb" :band :B4 :correct "to repair / to fix" :wrong ["to break" "to destroy" "to replace"]}
   {:word "bezpieczeństwo" :word-class "noun" :band :B4 :correct "safety / security" :wrong ["danger" "emergency" "insurance"]}
   {:word "zachowanie" :word-class "noun" :band :B4 :correct "behavior / conduct" :wrong ["appearance" "attitude" "achievement"]}
   {:word "ostrożny" :word-class "adj" :band :B4 :correct "careful / cautious" :wrong ["reckless" "brave" "lazy"]}
   {:word "przekonać" :word-class "verb" :band :B4 :correct "to persuade / to convince" :wrong ["to force" "to threaten" "to confuse"]}
   {:word "nieuchronny" :word-class "adj" :band :B5 :correct "inevitable / unavoidable" :wrong ["impossible" "unexpected" "unimportant"]}
   {:word "zawiły" :word-class "adj" :band :B5 :correct "intricate / complicated" :wrong ["simple" "obvious" "brief"]}
   {:word "wytrwałość" :word-class "noun" :band :B5 :correct "perseverance / persistence" :wrong ["laziness" "weakness" "impatience"]}
   {:word "ulga" :word-class "noun" :band :B5 :correct "relief" :wrong ["pain" "burden" "anxiety"]}
   {:word "oszustwo" :word-class "noun" :band :B5 :correct "fraud / scam / deception" :wrong ["honesty" "gift" "agreement"]}
   {:word "przeszkoda" :word-class "noun" :band :B5 :correct "obstacle / barrier" :wrong ["opportunity" "solution" "shortcut"]}
   {:word "wzmocnić" :word-class "verb" :band :B5 :correct "to strengthen / to reinforce" :wrong ["to weaken" "to remove" "to soften"]}
   {:word "skłonność" :word-class "noun" :band :B5 :correct "tendency / inclination" :wrong ["aversion" "ability" "obligation"]}
   {:word "zniechęcać" :word-class "verb" :band :B5 :correct "to discourage" :wrong ["to encourage" "to inspire" "to reward"]}
   {:word "pogłębić" :word-class "verb" :band :B5 :correct "to deepen / to intensify" :wrong ["to flatten" "to lighten" "to shorten"]}
   {:word "znikomy" :word-class "adj" :band :B6 :correct "negligible / minimal" :wrong ["enormous" "sufficient" "obvious"]}
   {:word "nikczemny" :word-class "adj" :band :B6 :correct "vile / despicable" :wrong ["noble / honorable" "ordinary" "generous"]}
   {:word "żmudny" :word-class "adj" :band :B6 :correct "tedious / laborious / painstaking" :wrong ["effortless" "exciting" "quick"]}
   {:word "oszczerstwo" :word-class "noun" :band :B6 :correct "slander / defamation" :wrong ["compliment" "confession" "agreement"]}
   {:word "zuchwały" :word-class "adj" :band :B6 :correct "audacious / insolent / bold" :wrong ["timid / shy" "polite" "cautious"]}
   {:word "przebiegły" :word-class "adj" :band :B6 :correct "cunning / sly / crafty" :wrong ["honest / naive" "clumsy" "generous"]}
   {:word "ułuda" :word-class "noun" :band :B6 :correct "illusion / delusion" :wrong ["reality" "certainty" "truth"]}
   {:word "niezłomny" :word-class "adj" :band :B6 :correct "unyielding / steadfast / indomitable" :wrong ["fragile / weak" "flexible" "hesitant"]}])

(defn initial-state []
  {:screen :start
   :current-question-index 0
   :answers []
   :answer-locked? false
   :feedback nil
   :results-data nil})

(defonce app-state
  (r/atom (initial-state)))

(defonce root
  (delay (rdom/create-root (.getElementById js/document "app"))))

(def button-class
  "inline-flex min-h-11 items-center justify-center rounded-md bg-emerald-700 px-4 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-emerald-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:cursor-not-allowed disabled:opacity-70")

(defn begin-test []
  (reset! app-state (assoc (initial-state) :screen :quiz)))

(defn choice-options [question]
  (conj (into [{:label (:correct question)
                :result :correct}]
              (map (fn [wrong]
                     {:label wrong
                      :result :wrong})
                   (:wrong question)))
        {:label "don't know"
         :result :unknown}))

(defn summarize-results [answers]
  {:answered (count answers)
   :total (count questions)
   :correct (count (filter #(= :correct (:result %)) answers))
   :unknown (count (filter #(= :unknown (:result %)) answers))
   :wrong (count (filter #(= :wrong (:result %)) answers))})

(defn feedback-for [choice question]
  (case (:result choice)
    :correct {:kind :correct
              :selected (:label choice)
              :message "Correct"}
    :unknown {:kind :wrong
              :selected (:label choice)
              :message (:correct question)}
    :wrong {:kind :wrong
            :selected (:label choice)
            :message (str "Correct answer: " (:correct question))}))

(defn record-answer [question choice]
  (swap! app-state
         (fn [{:keys [answer-locked? current-question-index answers] :as state}]
           (if answer-locked?
             state
             (let [answer {:question-index current-question-index
                           :word (:word question)
                           :band (:band question)
                           :selected (:label choice)
                           :correct (:correct question)
                           :result (:result choice)}
                   next-answers (conj answers answer)]
               (assoc state
                      :answers next-answers
                      :answer-locked? true
                      :feedback (feedback-for choice question)
                      :results-data (summarize-results next-answers)))))))

(defn next-question []
  (swap! app-state
         (fn [{:keys [current-question-index answers] :as state}]
           (if (= current-question-index (dec (count questions)))
             (assoc state
                    :screen :results
                    :results-data (summarize-results answers))
             (assoc state
                    :current-question-index (inc current-question-index)
                    :answer-locked? false
                    :feedback nil)))))

(defn start-screen []
  [:main {:class "min-h-screen bg-slate-50 px-4 py-8 text-slate-950 sm:px-6"}
   [:section {:aria-labelledby "start-heading"
              :class "mx-auto grid w-full max-w-3xl gap-6 rounded-lg border border-slate-200 bg-white p-6 shadow-xl shadow-slate-950/10 sm:p-8"}
    [:div {:class "grid gap-3"}
     [:p {:class "text-sm font-semibold uppercase tracking-wide text-emerald-700"} "Polish to English"]
     [:h1 {:id "start-heading"
           :class "text-4xl font-bold leading-tight text-slate-950 sm:text-5xl"}
      "Polish Vocabulary Test"]
     [:p {:class "max-w-2xl text-base leading-7 text-slate-600"}
      "Anchored low, ranging wide."]]
    [:div {:class "grid gap-4 rounded-md border border-emerald-200 bg-emerald-50 p-4 text-sm leading-6 text-slate-700"}
     [:p [:strong {:class "font-semibold text-slate-950"} "Format: "]
      "You'll see a Polish word. Pick the correct English meaning from 4 choices."]
     [:p "You estimated ~500 words but suspect you know more, so this test ranges well past 500 to find where you actually top out."]
     [:div
      [:p {:class "font-semibold text-slate-950"} "80 words across 6 bands:"]
      [:ul {:class "mt-2 grid gap-1"}
       (for [{:keys [id summary]} bands]
         ^{:key id}
         [:li summary])]]
     [:p [:strong {:class "font-semibold text-slate-950"} "Don't guess. "]
      "Pick \"don't know\" if unsure; it makes the estimate accurate."]
     [:p "~12 minutes."]]
    [:button {:type "button"
              :class button-class
              :on-click begin-test}
     "Begin Test"]]])

(defn choice-button [question answer-locked? feedback choice]
  (let [correct-answer? (= (:label choice) (:correct question))
        selected-answer? (= (:label choice) (:selected feedback))
        selected-result (:kind feedback)
        base-class "min-h-12 rounded-md border px-4 py-3 text-left text-sm font-semibold shadow-sm transition-colors focus-visible:outline-2 focus-visible:outline-offset-2"
        state-class (cond
                      (and answer-locked? correct-answer?)
                      "border-emerald-600 bg-emerald-50 text-emerald-900"

                      (and answer-locked? selected-answer? (= :wrong selected-result))
                      "border-red-400 bg-red-50 text-red-900"

                      :else
                      "border-slate-300 bg-white text-slate-950 hover:border-emerald-700 hover:bg-emerald-50 focus-visible:outline-emerald-700")]
    [:button {:type "button"
              :class (str base-class " " state-class)
              :disabled answer-locked?
              :on-click #(record-answer question choice)}
     (:label choice)]))

(defn quiz-screen [{:keys [current-question-index answer-locked? feedback]}]
  (let [question (nth questions current-question-index)
        progress (* 100 (/ current-question-index (count questions)))]
    [:main {:class "min-h-screen bg-slate-50 px-4 py-8 text-slate-950 sm:px-6"}
     [:section {:aria-labelledby "question-word"
                :class "mx-auto grid w-full max-w-2xl gap-6 rounded-lg border border-slate-200 bg-white p-6 shadow-xl shadow-slate-950/10 sm:p-8"}
      [:div {:class "h-2 overflow-hidden rounded-full bg-slate-100"
             :role "progressbar"
             :aria-valuemin 0
             :aria-valuemax (count questions)
             :aria-valuenow current-question-index}
       [:div {:class "h-full rounded-full bg-emerald-700 transition-all"
              :style {:width (str progress "%")}}]]
      [:div {:class "flex flex-wrap items-center justify-between gap-3 text-sm font-semibold text-slate-600"}
       [:span (str (inc current-question-index) " / " (count questions))]
       [:span {:class "rounded-full bg-emerald-100 px-3 py-1 text-xs font-bold text-emerald-800"}
        (band-labels (:band question))]]
      [:div {:class "grid gap-2 text-center"}
       [:h2 {:id "question-word"
             :class "text-5xl font-bold leading-tight text-slate-950"}
        (:word question)]
       [:p {:class "text-sm font-semibold uppercase tracking-wide text-slate-500"}
        (:word-class question)]
       [:p {:class "text-base text-slate-700"} "Select the correct meaning"]]
      [:div {:class "grid gap-3"}
       (for [choice (choice-options question)]
         ^{:key (:label choice)}
         [choice-button question answer-locked? feedback choice])]
      (when feedback
        [:p {:class (if (= :correct (:kind feedback))
                      "rounded-md bg-emerald-50 p-3 text-sm font-semibold text-emerald-800"
                      "rounded-md bg-red-50 p-3 text-sm font-semibold text-red-800")}
         (:message feedback)])
      (when answer-locked?
        [:button {:type "button"
                  :class (str button-class " justify-self-end")
                  :on-click next-question}
         "Next"])]]))

(defn results-screen [{:keys [results-data]}]
  [:main {:class "min-h-screen bg-slate-50 px-4 py-8 text-slate-950 sm:px-6"}
   [:section {:aria-labelledby "results-heading"
              :class "mx-auto grid w-full max-w-2xl gap-4 rounded-lg border border-slate-200 bg-white p-6 shadow-xl shadow-slate-950/10 sm:p-8"}
    [:h1 {:id "results-heading"
          :class "text-4xl font-bold leading-tight text-slate-950"}
     "Results"]
    [:p {:class "text-base text-slate-700"}
     (str (:correct results-data) " of " (:total results-data) " correct")]
    [:button {:type "button"
              :class button-class
              :on-click begin-test}
     "Retake"]]])

(defn app []
  (let [state @app-state]
    (case (:screen state)
      :quiz [quiz-screen state]
      :results [results-screen state]
      [start-screen])))

(defn ^:dev/after-load init []
  (rdom/render @root [app]))
