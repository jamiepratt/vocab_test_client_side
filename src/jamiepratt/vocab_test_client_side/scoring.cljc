(ns jamiepratt.vocab-test-client-side.scoring
  (:require
   [jamiepratt.vocab-test-client-side.data :as data]))

(defn round-value [n]
  #?(:clj (long (Math/round (double n)))
     :cljs (js/Math.round n)))

(defn log-value [n]
  #?(:clj (Math/log (double n))
     :cljs (js/Math.log n)))

(defn exp-value [n]
  #?(:clj (Math/exp (double n))
     :cljs (js/Math.exp n)))

(defn clamp [lower upper n]
  (min upper (max lower n)))

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
  (if (contains? stats band)
    (-> stats
        (update-in [band :answered] inc)
        (update-in [band result] inc))
    stats))

(def real-item-types
  #{nil
    "sentence-context-lemma"
    :sentence-context-lemma})

(defn real-vocabulary-answer? [answer]
  (and (not= false (:vocabulary-evidence? answer))
       (contains? real-item-types (:item-type answer))))

(defn real-vocabulary-answers [answers]
  (filterv real-vocabulary-answer? answers))

(def posterior-grid-size 401)

(def theta-grid
  (mapv (fn [index]
          (/ (+ index 0.5) posterior-grid-size))
        (range posterior-grid-size)))

(def legacy-band-strata
  {:B1 1
   :B2 1
   :B3 1
   :B4 2
   :B5 3
   :B6 4})

(defn answer-stratum-id [{:keys [inventory-stratum fixed-stratum lemma-rank
                                 lemma-inventory-rank band]}]
  (or inventory-stratum
      fixed-stratum
      (data/lemma-inventory-stratum-id lemma-rank)
      (data/lemma-inventory-stratum-id lemma-inventory-rank)
      (legacy-band-strata band)))

(defn guess-rate [{:keys [guess-rate choice-count result]}]
  (if (= :dk result)
    0
    (let [raw (or guess-rate
                  (when (and (number? choice-count)
                             (> choice-count 1))
                    (/ 1 choice-count))
                  0)]
      (clamp 0 0.95 raw))))

(defn answer-log-likelihood [theta {:keys [result correct?] :as answer}]
  (let [g (guess-rate answer)
        not-g (- 1 g)
        unknown (- 1 theta)
        p (cond
            (or (= :correct result) (= true correct?))
            (+ g (* not-g theta))

            (= :wrong result)
            (* not-g unknown)

            (= :dk result)
            unknown

            :else
            unknown)]
    (log-value (max 1.0E-300 p))))

(defn prior-log-density [theta]
  (+ (* -0.5 (log-value theta))
     (* -0.5 (log-value (- 1 theta)))))

(defn stratum-log-weights [answers]
  (mapv (fn [theta]
          (reduce + (prior-log-density theta)
                  (map (partial answer-log-likelihood theta) answers)))
        theta-grid))

(defn normalized-weights [log-weights]
  (let [max-log (reduce max log-weights)
        weights (mapv #(exp-value (- % max-log)) log-weights)
        total (reduce + weights)]
    (if (pos? total)
      (mapv #(/ % total) weights)
      (repeat posterior-grid-size (/ 1 posterior-grid-size)))))

(defn weighted-mean [weights]
  (reduce + (map * theta-grid weights)))

(defn weighted-quantile [weights q]
  (let [target (clamp 0 1 q)]
    (loop [cumulative 0
           [[theta weight] & more] (map vector theta-grid weights)]
      (let [next-cumulative (+ cumulative weight)]
        (cond
          (nil? theta) (last theta-grid)
          (>= next-cumulative target) theta
          :else (recur next-cumulative more))))))

(defn stratum-posterior [answers]
  (let [weights (normalized-weights (stratum-log-weights answers))]
    {:mean (weighted-mean weights)
     :lower (weighted-quantile weights 0.025)
     :upper (weighted-quantile weights 0.975)}))

(defn observed-stratum-ids [answers]
  (->> answers
       (keep answer-stratum-id)
       distinct
       sort
       vec))

(defn reported-stratum-ids [answers]
  (if-let [highest (last (observed-stratum-ids answers))]
    (vec (range 1 (inc highest)))
    [1]))

(defn round-lemma-count [n]
  (long (clamp 0 data/lemma-inventory-size (round-value n))))

(defn format-count [n]
  #?(:clj (format "%,d" (long n))
     :cljs (.toLocaleString n "en-US")))

(defn format-range [{:keys [lower upper]}]
  (str (format-count lower) "-" (format-count upper)))

(defn posterior-summary [answers adaptive-decision]
  (let [answers-by-stratum (group-by answer-stratum-id answers)
        floor-result? (= :report-floor (:action adaptive-decision))
        stratum-summaries (mapv
                           (fn [stratum-id]
                             (let [{:keys [width]} (data/lemma-inventory-strata-by-id stratum-id)
                                   posterior (stratum-posterior
                                              (get answers-by-stratum stratum-id []))]
                               (assoc posterior
                                      :id stratum-id
                                      :width width)))
                           (reported-stratum-ids answers))
        estimate (round-lemma-count
                  (reduce + (map #(* (:width %) (:mean %))
                                 stratum-summaries)))
        lower (round-lemma-count
               (reduce + (map #(* (:width %) (:lower %))
                              stratum-summaries)))
        upper (round-lemma-count
               (reduce + (map #(* (:width %) (:upper %))
                              stratum-summaries)))]
    (if floor-result?
      {:lemma-estimate (min 199 estimate)
       :likely-range {:lower 0
                      :upper 200}
       :estimate-label "under 200"
       :estimate-unit "recognized Polish lemmas"
       :posterior-strata stratum-summaries}
      {:lemma-estimate estimate
       :likely-range {:lower lower
                      :upper (max lower upper)}
       :estimate-label nil
       :estimate-unit "recognized Polish lemmas"
       :posterior-strata stratum-summaries})))

(defn level-band-for-count [lemma-count]
  (or (some (fn [{:keys [lower upper] :as band}]
              (when (and (>= lemma-count lower)
                         (< lemma-count upper))
                band))
            data/level-bands)
      (last data/level-bands)))

(defn crossed-level-boundary? [{:keys [lower upper]} boundary]
  (and (< lower boundary)
       (> upper boundary)))

(defn level-band-label [lemma-estimate likely-range]
  (let [lower-band (level-band-for-count (:lower likely-range))
        upper-band (level-band-for-count (:upper likely-range))
        boundaries (map :lower (rest data/level-bands))
        borderline? (some (partial crossed-level-boundary? likely-range)
                          boundaries)]
    (if (and borderline?
             (not= (:id lower-band) (:id upper-band)))
      (str "borderline: " (:label lower-band) " to " (:label upper-band))
      (:label (level-band-for-count lemma-estimate)))))

(defn with-level-band [summary]
  (assoc summary :level-band (level-band-label (:lemma-estimate summary)
                                               (:likely-range summary))))

(defn estimate-display [{:keys [estimate-label lemma-estimate]}]
  (or estimate-label
      (str "about " (format-count lemma-estimate) " recognized Polish lemmas")))

(defn pending-live-estimate-label []
  (str "Not enough questions answered to make an estimate yet, answer at least "
       data/live-estimate-min-real-answers
       " questions and estimate is updated live as you answer each question."))

(defn live-estimate [summary]
  (if (< (:total summary) data/live-estimate-min-real-answers)
    {:ready? false
     :label (pending-live-estimate-label)}
    {:ready? true
     :label (str "Current estimate: " (estimate-display summary))
     :range-label (str "Likely range: " (format-range (:likely-range summary)))}))

(defn with-live-estimate [summary]
  (assoc summary :live-estimate (live-estimate summary)))

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
         posterior (with-level-band
                     (posterior-summary evidence-answers adaptive-decision))]
     (with-live-estimate
       (merge posterior
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
               :estimate-label (or (:estimate-label posterior)
                                   (:estimate-label adaptive-decision))
               :adaptive-decision adaptive-decision})))))
