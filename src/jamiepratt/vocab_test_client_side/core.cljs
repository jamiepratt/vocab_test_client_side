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

(def ordered-band-ids
  (mapv :id bands))

(def band-sizes
  {:B1 250
   :B2 250
   :B3 500
   :B4 1000
   :B5 1500
   :B6 2500})

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
  (reset! app-state
          (assoc (initial-state)
                 :screen :quiz
                 :question-choices (mapv choice-options questions))))

(defn round-nearest-50 [n]
  (* 50 (js/Math.round (/ n 50))))

(defn empty-band-stats []
  (into {} (map (fn [band-id]
                  [band-id {:answered 0
                            :correct 0
                            :wrong 0
                            :dk 0
                            :proportion 0
                            :pct 0}])
                ordered-band-ids)))

(defn add-band-answer [stats {:keys [band result]}]
  (-> stats
      (update-in [band :answered] inc)
      (update-in [band result] inc)))

(defn with-band-proportions [stats]
  (into {}
        (map (fn [[band-id {:keys [answered correct] :as stat}]]
               (let [proportion (if (pos? answered)
                                  (/ correct answered)
                                  0)]
                 [band-id (assoc stat
                                 :proportion proportion
                                 :pct (js/Math.round (* 100 proportion)))]))
             stats)))

(defn band-stats-for [answers]
  (with-band-proportions
    (reduce add-band-answer (empty-band-stats) answers)))

(defn band-proportion [band-stats band-id]
  (get-in band-stats [band-id :proportion] 0))

(defn weighted-vocab-estimate [band-stats]
  (round-nearest-50
   (reduce +
           (map (fn [band-id]
                  (* (band-proportion band-stats band-id)
                     (band-sizes band-id)))
                ordered-band-ids))))

(defn adjusted-vocab-estimate [vocab-estimate wrong-count]
  (let [guessing-bias (* wrong-count 0.25 35)
        rounded-bias (round-nearest-50 guessing-bias)]
    (max 0 (- vocab-estimate rounded-bias))))

(defn ceiling-band [band-stats]
  (reduce (fn [highest band-id]
            (if (>= (band-proportion band-stats band-id) 0.5)
              band-id
              highest))
          :B1
          ordered-band-ids))

(defn level-interpretation [band-stats]
  (let [b1 (band-proportion band-stats :B1)
        b2 (band-proportion band-stats :B2)
        b3 (band-proportion band-stats :B3)
        b4 (band-proportion band-stats :B4)]
    (cond
      (< b1 0.7)
      "Even the top 250 words are shaky, which puts the vocabulary under 250. This is very early - the focus should be on drilling the most frequent words until they're automatic."

      (< b2 0.5)
      "The top 250 are solid but the 250-500 band is patchy. Real vocabulary is probably 300-450 - close to the 500 estimate, perhaps a touch under. Solid beginner foundation."

      (< b3 0.4)
      "The 500 estimate was accurate, maybe even a little low. The top 500 are solid and a few 500-1,000 words are sticking. This is a real A1 foundation - enough for basic survival Polish."

      (and (>= b3 0.4) (< b4 0.35))
      "You know more than you thought. The 500-1,000 band is partially solid, putting you around 700-1,000 words - well above your 500 guess. This is solid A1, approaching A2. You're underselling yourself."

      (>= b4 0.45)
      "You significantly underestimated. Your vocabulary extends well into the 1,000-2,000 range - you're probably around 1,200-1,800 words, not 500. This is solid A2 territory. Either you've absorbed more than you realized or you have strong cognate/Slavic-language support."

      :else
      "Roughly in the 600-900 range - above your 500 estimate. A solid beginner foundation with the start of intermediate vocabulary.")))

(defn estimate-comparison [adjusted-estimate]
  (let [diff (- adjusted-estimate 500)
        abs-diff (js/Math.abs diff)]
    (cond
      (< abs-diff 150)
      "Your ~500 estimate was accurate. Results are within +/-150 words."

      (pos? diff)
      (str "You knew more than you thought. Your vocabulary appears ~" abs-diff " words above your 500 guess.")

      :else
      (str "Slightly below your estimate. Your vocabulary appears ~" abs-diff " words under your 500 guess - but this is well within normal range."))))

(defn honesty-note [wrong-count dk-count total]
  (cond
    (> (/ wrong-count total) 0.15)
    "Your wrong-guess rate is notable. Some correct answers may be lucky hits (1/4 odds). The real vocabulary could be 50-150 words below the estimate."

    (> dk-count (* wrong-count 2))
    "You used \"don't know\" honestly. This estimate is probably accurate or slightly conservative."))

(defn summarize-results [answers]
  (let [total (count questions)
        correct (count (filter #(= :correct (:result %)) answers))
        dk (count (filter #(= :dk (:result %)) answers))
        wrong (count (filter #(= :wrong (:result %)) answers))
        review-answers (filterv #(not= :correct (:result %)) answers)
        band-stats (band-stats-for answers)
        vocab-estimate (weighted-vocab-estimate band-stats)
        adjusted-estimate (adjusted-vocab-estimate vocab-estimate wrong)]
    {:answered (count answers)
     :total total
     :correct correct
     :dk dk
     :wrong wrong
     :accuracy-pct (js/Math.round (* 100 (/ correct total)))
     :band-stats band-stats
     :review-answers review-answers
     :vocab-estimate vocab-estimate
     :adjusted-estimate adjusted-estimate
     :ceiling-band (ceiling-band band-stats)
     :comparison (estimate-comparison adjusted-estimate)
     :interpretation (level-interpretation band-stats)
     :honesty-note (honesty-note wrong dk total)}))

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
         (fn [{:keys [answer-locked? current-question-index answers] :as state}]
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
     [:p "You estimated ~500 words but suspect you know more, so this test ranges well past 500 to find where you actually top out."]
     [:div
      [:p {:class "font-semibold text-stone-950"} "80 words across 6 bands:"]
      [:ul {:class "mt-2 grid gap-1"}
       (for [{:keys [id summary]} bands]
         ^{:key id}
         [:li {:class (str "rounded-md border px-3 py-2 " (band-style-class id :panel))}
          summary])]]
     [:p [:strong {:class "font-semibold text-stone-950"} "Don't guess. "]
      "Pick \"don't know\" if unsure; it makes the estimate accurate."]
     [:p "~12 minutes."]]
    [:button {:type "button"
              :class button-class
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

(defn quiz-screen [{:keys [current-question-index question-choices answer-locked? feedback]}]
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
        (band-labels (:band question))]]
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
     [:span {:class (str "font-bold " (band-style-class band-id :text))} (band-labels band-id)]
     [:div {:class "h-2 overflow-hidden rounded-full bg-stone-100"
            :aria-hidden true}
      [:div {:class (str "h-full rounded-full " (band-style-class band-id :bar))
             :style {:width (str pct "%")}}]]
     [:span {:class "font-semibold text-stone-700"}
      (str correct "/" answered " (" pct "%)")]]))

(defn review-answer-row [{:keys [band word correct]}]
  [:li {:class "grid min-w-0 gap-1 rounded-md border border-stone-200 p-3 text-sm sm:grid-cols-3 sm:items-center"}
   [:span {:class (str "font-bold " (band-style-class band :text))} (band-labels band)]
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
      (for [band-id ordered-band-ids]
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
       (str "Ceiling: " (band-labels (:ceiling-band results-data)))]]
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
      [start-screen])))

(defn ^:dev/after-load init []
  (rdom/render @root [app]))
