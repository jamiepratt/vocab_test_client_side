(ns jamiepratt.vocab-test-client-side.scoring
  (:require
   [jamiepratt.vocab-test-client-side.data :as data]))

(defn round-value [n]
  #?(:clj (long (Math/round (double n)))
     :cljs (js/Math.round n)))

(defn abs-value [n]
  #?(:clj (abs n)
     :cljs (js/Math.abs n)))

(defn round-nearest-50 [n]
  (* 50 (round-value (/ n 50))))

(defn empty-band-stats []
  (into {} (map (fn [band-id]
                  [band-id {:answered 0
                            :correct 0
                            :wrong 0
                            :dk 0
                            :proportion 0
                            :pct 0}])
                data/ordered-band-ids)))

(defn add-band-answer [stats {:keys [band result]}]
  (-> stats
      (update-in [band :answered] inc)
      (update-in [band result] inc)))

(def real-item-types
  #{nil
    "sentence-context-lemma"
    :sentence-context-lemma})

(defn real-vocabulary-answer? [answer]
  (and (not= false (:vocabulary-evidence? answer))
       (contains? real-item-types (:item-type answer))))

(defn real-vocabulary-answers [answers]
  (filterv real-vocabulary-answer? answers))

(defn adaptive-block-decision [{:keys [id lower-block-id higher-block-id floor?] :as block} answers]
  (let [block-answers (filterv #(and (real-vocabulary-answer? %)
                                     (= id (:adaptive-block-id %)))
                               answers)
        real-item-count (count block-answers)
        correct (count (filter #(= :correct (:result %)) block-answers))
        correct-rate (if (pos? real-item-count)
                       (/ correct real-item-count)
                       0)
        route-lower? (<= correct-rate 0.15)
        route-higher? (>= correct-rate 0.85)
        action (cond
                 (and route-lower? floor?) :report-floor
                 (and route-lower? lower-block-id) :route-lower
                 route-lower? :report
                 (and route-higher? higher-block-id) :route-higher
                 route-higher? :report
                 :else :report)]
    (assoc block
           :action action
           :next-block-id (case action
                            :route-lower lower-block-id
                            :route-higher higher-block-id
                            nil)
           :estimate-label (when (= :report-floor action)
                             "under 200")
           :correct correct
           :real-item-count real-item-count
           :correct-rate correct-rate
           :accuracy-pct (round-value (* 100 correct-rate)))))

(defn with-band-proportions [stats]
  (into {}
        (map (fn [[band-id {:keys [answered correct] :as stat}]]
               (let [proportion (if (pos? answered)
                                  (/ correct answered)
                                  0)]
                 [band-id (assoc stat
                                 :proportion proportion
                                 :pct (round-value (* 100 proportion)))]))
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
                     (data/band-sizes band-id)))
                data/ordered-band-ids))))

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
          data/ordered-band-ids))

(defn level-interpretation [band-stats adjusted-estimate]
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
      (str "You significantly underestimated. Your passive vocabulary is closer to ~"
           adjusted-estimate
           " words than 500. Your higher-frequency recognition is strong, with meaningful reach into the upper bands. Either you've absorbed more than you realized or you have strong cognate/Slavic-language support.")

      :else
      "Roughly in the 600-900 range - above your 500 estimate. A solid beginner foundation with the start of intermediate vocabulary.")))

(defn estimate-comparison [adjusted-estimate]
  (let [diff (- adjusted-estimate 500)
        abs-diff (abs-value diff)]
    (cond
      (< abs-diff 150)
      "Your ~500 estimate was accurate. Results are within +/-150 words."

      (pos? diff)
      (str "You knew more than you thought. Your vocabulary appears ~" abs-diff " words above your 500 guess.")

      :else
      (str "Slightly below your estimate. Your vocabulary appears ~" abs-diff " words under your 500 guess - but this is well within normal range."))))

(defn honesty-note [wrong-count dk-count total]
  (cond
    (and (pos? total) (> (/ wrong-count total) 0.15))
    "Your wrong-guess rate is notable. Some correct answers may be lucky hits (1/4 odds). The real vocabulary could be 50-150 words below the estimate."

    (> dk-count (* wrong-count 2))
    "You used \"don't know\" honestly. This estimate is probably accurate or slightly conservative."))

(defn summarize-results
  ([questions answers]
   (summarize-results questions answers nil))
  ([_questions answers adaptive-decision]
   (let [evidence-answers (real-vocabulary-answers answers)
         total (count evidence-answers)
         correct (count (filter #(= :correct (:result %)) evidence-answers))
         dk (count (filter #(= :dk (:result %)) evidence-answers))
         wrong (count (filter #(= :wrong (:result %)) evidence-answers))
         review-answers (filterv #(not= :correct (:result %)) evidence-answers)
         band-stats (band-stats-for evidence-answers)
         vocab-estimate (weighted-vocab-estimate band-stats)
         adjusted-estimate (adjusted-vocab-estimate vocab-estimate wrong)]
     {:answered total
      :total total
      :correct correct
      :dk dk
      :wrong wrong
      :accuracy-pct (if (pos? total)
                      (round-value (* 100 (/ correct total)))
                      0)
      :band-stats band-stats
      :review-answers review-answers
      :vocab-estimate vocab-estimate
      :adjusted-estimate adjusted-estimate
      :estimate-label (:estimate-label adaptive-decision)
      :adaptive-decision adaptive-decision
      :ceiling-band (ceiling-band band-stats)
      :comparison (estimate-comparison adjusted-estimate)
      :interpretation (level-interpretation band-stats adjusted-estimate)
      :honesty-note (honesty-note wrong dk total)})))
