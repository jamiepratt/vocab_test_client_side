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

(def real-item-types
  #{nil
    "sentence-context-lemma"
    :sentence-context-lemma})

(defn real-vocabulary-answer? [answer]
  (and (not= false (:vocabulary-evidence? answer))
       (contains? real-item-types (:item-type answer))))

(defn real-vocabulary-answers [answers]
  (filterv real-vocabulary-answer? answers))

(def scoring-model-version "latent-guessing-v1")

(def theta-grid-size 401)

(def guessing-grid-size 81)

(def min-probability 1.0E-300)

(def theta-grid
  (mapv (fn [index]
          (/ (+ index 0.5) theta-grid-size))
        (range theta-grid-size)))

(def guessing-grid
  (mapv (fn [index]
          (/ (+ index 0.5) guessing-grid-size))
        (range guessing-grid-size)))

(def missing-field-token ::missing-field)

(defn required-scoring-field [answer field]
  (let [value (get answer field missing-field-token)]
    (when (or (= missing-field-token value)
              (nil? value))
      (throw (ex-info (str "Real vocabulary answer is missing "
                           (name field)
                           ".")
                      {:reason :missing-scoring-field
                       :field field
                       :answer answer})))
    value))

(defn positive-whole-number? [value]
  (and (number? value)
       (pos? value)
       (== value (long value))))

(defn required-positive-whole-number [answer field]
  (let [value (required-scoring-field answer field)]
    (when-not (positive-whole-number? value)
      (throw (ex-info (str "Real vocabulary answer has invalid "
                           (name field)
                           ".")
                      {:reason :invalid-scoring-field
                       :field field
                       :value value
                       :answer answer})))
    (long value)))

(defn answer-scoring-stratum-id [answer]
  (let [stratum-id (required-positive-whole-number answer :lemma-inventory-stratum)
        lemma-rank (required-positive-whole-number answer :lemma-rank)
        expected-stratum-id (data/lemma-inventory-stratum-id lemma-rank)]
    (when-not (= stratum-id expected-stratum-id)
      (throw (ex-info "Real vocabulary answer lemma-inventory-stratum does not match lemma-rank."
                      {:reason :lemma-stratum-rank-mismatch
                       :lemma-inventory-stratum stratum-id
                       :lemma-rank lemma-rank
                       :expected-lemma-inventory-stratum expected-stratum-id
                       :answer answer})))
    stratum-id))

(defn random-choice-rate [{:keys [guess-rate choice-count]}]
  (let [raw (or (when (number? guess-rate)
                  guess-rate)
                (when (and (number? choice-count)
                           (> choice-count 1))
                  (/ 1 choice-count))
                0)]
    (clamp 0 0.95 raw)))

(defn safe-log [p]
  (log-value (max min-probability p)))

(defn increment-count [counts k]
  (update counts k (fnil inc 0)))

(defn likelihood-summary [answers]
  (reduce (fn [summary {:keys [result correct?] :as answer}]
            (let [r (random-choice-rate answer)]
              (cond
                (or (= :correct result) (= true correct?))
                (update summary :correct-rates increment-count r)

                (= :wrong result)
                (update summary :wrong-rates increment-count r)

                :else
                (update summary :dk-count inc))))
          {:correct-rates {}
           :wrong-rates {}
           :dk-count 0}
          answers))

(defn beta-log-prior-density [alpha beta x]
  (+ (* (dec alpha) (log-value x))
     (* (dec beta) (log-value (- 1 x)))))

(defn theta-prior-log-density [theta]
  (beta-log-prior-density 0.5 0.5 theta))

(defn guessing-prior-log-density [q]
  (beta-log-prior-density 0.5 8.0 q))

(defn summary-log-likelihood [theta q {:keys [correct-rates wrong-rates dk-count]}]
  (let [unknown (- 1 theta)]
    (+ (reduce-kv (fn [total r count]
                    (+ total
                       (* count
                          (safe-log (+ theta (* unknown q r))))))
                  0
                  correct-rates)
       (reduce-kv (fn [total r count]
                    (+ total
                       (* count
                          (safe-log (* unknown q (- 1 r))))))
                  0
                  wrong-rates)
       (* dk-count (safe-log (* unknown (- 1 q)))))))

(defn stratum-log-weights [answer-summary q]
  (mapv (fn [theta]
          (+ (theta-prior-log-density theta)
             (summary-log-likelihood theta q answer-summary)))
        theta-grid))

(defn log-sum-exp [log-weights]
  (let [max-log (reduce max log-weights)
        scaled-total (reduce + (map #(exp-value (- % max-log)) log-weights))]
    (+ max-log (log-value scaled-total))))

(defn normalized-weights [log-weights]
  (let [size (count log-weights)]
    (if (zero? size)
      []
      (let [max-log (reduce max log-weights)
            weights (mapv #(exp-value (- % max-log)) log-weights)
            total (reduce + weights)]
        (if (pos? total)
          (mapv #(/ % total) weights)
          (repeat size (/ 1 size)))))))

(defn weighted-mean
  ([weights]
   (weighted-mean theta-grid weights))
  ([grid weights]
   (reduce + (map * grid weights))))

(defn weighted-quantile
  ([weights q]
   (weighted-quantile theta-grid weights q))
  ([grid weights q]
   (let [target (clamp 0 1 q)]
     (loop [cumulative 0
            pairs (map vector grid weights)]
       (if-let [[[value weight] & more] (seq pairs)]
         (let [next-cumulative (+ cumulative weight)]
           (if (>= next-cumulative target)
             value
             (recur next-cumulative more)))
         (last grid))))))

(defn posterior-from-grid [grid weights]
  {:mean (weighted-mean grid weights)
   :lower (weighted-quantile grid weights 0.025)
   :upper (weighted-quantile grid weights 0.975)})

(defn stratum-q-state [answer-summary q]
  (let [theta-log-weights (stratum-log-weights answer-summary q)]
    {:theta-log-weights theta-log-weights
     :log-evidence (log-sum-exp theta-log-weights)}))

(defn mixed-theta-weights [q-weights q-states]
  (reduce (fn [acc [q-weight {:keys [theta-log-weights]}]]
            (mapv + acc
                  (mapv #(* q-weight %)
                        (normalized-weights theta-log-weights))))
          (vec (repeat theta-grid-size 0))
          (map vector q-weights q-states)))

(defn latent-guessing-posterior [answers]
  (let [answers-by-stratum (dissoc (group-by answer-scoring-stratum-id answers) nil)
        stratum-ids (sort (keys answers-by-stratum))
        stratum-states (into {}
                             (map (fn [[stratum-id stratum-answers]]
                                    [stratum-id
                                     (let [answer-summary
                                           (likelihood-summary stratum-answers)]
                                       (mapv #(stratum-q-state answer-summary %)
                                             guessing-grid))])
                                  answers-by-stratum))
        q-log-weights (mapv
                       (fn [q-index q]
                         (reduce + (guessing-prior-log-density q)
                                 (map (fn [stratum-id]
                                        (:log-evidence
                                         (nth (get stratum-states stratum-id)
                                              q-index)))
                                      stratum-ids)))
                       (range)
                       guessing-grid)
        q-weights (normalized-weights q-log-weights)
        strata (into {}
                     (map (fn [stratum-id]
                            [stratum-id
                             (assoc (posterior-from-grid
                                     theta-grid
                                     (mixed-theta-weights
                                      q-weights
                                      (get stratum-states stratum-id)))
                                    :id stratum-id)])
                          stratum-ids))]
    {:guessing-posterior (posterior-from-grid guessing-grid q-weights)
     :observed-stratum-ids (vec stratum-ids)
     :strata strata}))

(defn observed-stratum-ids [answers]
  (->> answers
       (keep answer-scoring-stratum-id)
       distinct
       sort
       vec))

(defn block-target-real-item-count [block-id]
  (or (:real-item-count (data/adaptive-block block-id))
      data/adaptive-real-item-count))

(defn block-answer-summary [block-answers]
  (let [real-item-count (count block-answers)
        correct (count (filter #(= :correct (:result %)) block-answers))
        correct-rate (if (pos? real-item-count)
                       (/ correct real-item-count)
                       0)]
    {:real-item-count real-item-count
     :correct correct
     :correct-rate correct-rate}))

(def empty-stratum-answer-counts
  {:answered 0
   :correct 0
   :wrong 0
   :dk 0})

(def counted-result-keys
  #{:correct :wrong :dk})

(def missing-rank-sort-value 999999999999)

(defn review-result-sort-value [result]
  (case result
    :wrong 0
    :dk 1
    2))

(defn review-rank-sort-value [rank]
  (if (number? rank)
    (long rank)
    missing-rank-sort-value))

(defn review-answer-sort-key [{:keys [result lemma-rank question-index]}]
  [(review-result-sort-value result)
   (review-rank-sort-value lemma-rank)
   (review-rank-sort-value question-index)])

(defn sorted-review-answers [answers]
  (->> answers
       (filter #(not= :correct (:result %)))
       (sort-by review-answer-sort-key)
       vec))

(defn add-stratum-answer-count [counts {:keys [result] :as answer}]
  (let [stratum-id (answer-scoring-stratum-id answer)]
    (cond-> (update-in counts [stratum-id :answered] (fnil inc 0))
      (contains? counted-result-keys result)
      (update-in [stratum-id result] (fnil inc 0)))))

(defn stratum-answer-counts [answers]
  (reduce add-stratum-answer-count {} answers))

(defn counts-for-stratum [counts stratum-id]
  (merge empty-stratum-answer-counts
         (get counts stratum-id)))

(defn high-confidence-pass? [answers]
  (boolean
   (some (fn [[block-id block-answers]]
           (let [{:keys [real-item-count correct-rate]}
                 (block-answer-summary block-answers)]
             (and (>= real-item-count (block-target-real-item-count block-id))
                  (>= correct-rate 0.85))))
         (group-by :adaptive-block-id (real-vocabulary-answers answers)))))

(defn assumed-known-lower-stratum-ids [answers observed-ids]
  (if (and (seq observed-ids)
           (high-confidence-pass? answers))
    (let [highest-observed (apply max observed-ids)
          observed-set (set observed-ids)]
      (vec (remove observed-set (range 1 highest-observed))))
    []))

(defn reported-stratum-ids [answers]
  (let [observed-ids (observed-stratum-ids answers)]
    (vec (sort (concat observed-ids
                       (assumed-known-lower-stratum-ids answers observed-ids))))))

(defn round-lemma-count [n]
  (long (clamp 0 data/lemma-inventory-size (round-value n))))

(defn format-count [n]
  #?(:clj (format "%,d" (long n))
     :cljs (.toLocaleString n "en-US")))

(defn format-range [{:keys [lower upper]}]
  (str (format-count lower) "-" (format-count upper)))

(defn posterior-summary [answers adaptive-decision]
  (let [latent-posterior (latent-guessing-posterior answers)
        observed-ids (:observed-stratum-ids latent-posterior)
        assumed-ids (assumed-known-lower-stratum-ids answers observed-ids)
        assumed-set (set assumed-ids)
        reported-ids (reported-stratum-ids answers)
        answer-counts (stratum-answer-counts answers)
        floor-result? (= :report-floor (:action adaptive-decision))
        stratum-summaries (mapv
                           (fn [stratum-id]
                             (let [{:keys [start end width label]}
                                   (data/lemma-inventory-strata-by-id stratum-id)
                                   posterior (if (contains? assumed-set stratum-id)
                                               {:mean 1
                                                :lower 1
                                                :upper 1
                                                :status :assumed-known-lower}
                                               (assoc (get-in latent-posterior
                                                              [:strata stratum-id])
                                                      :status :observed))]
                               (merge posterior
                                      (counts-for-stratum answer-counts stratum-id)
                                      (let [estimate (round-lemma-count
                                                      (* width (:mean posterior)))
                                            lower (round-lemma-count
                                                   (* width (:lower posterior)))
                                            upper (round-lemma-count
                                                   (* width (:upper posterior)))]
                                        {:id stratum-id
                                         :label label
                                         :rank-start start
                                         :rank-end end
                                         :width width
                                         :estimate estimate
                                         :likely-range {:lower lower
                                                        :upper (max lower upper)}}))))
                           reported-ids)
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
       :scoring-model-version scoring-model-version
       :guessing-posterior (:guessing-posterior latent-posterior)
       :posterior-strata stratum-summaries}
      {:lemma-estimate estimate
       :likely-range {:lower lower
                      :upper (max lower upper)}
       :estimate-label nil
       :estimate-unit "recognized Polish lemmas"
       :scoring-model-version scoring-model-version
       :guessing-posterior (:guessing-posterior latent-posterior)
       :posterior-strata stratum-summaries})))

(defn estimate-level-for-count [lemma-count]
  (or (some (fn [{:keys [lower upper] :as level}]
              (when (and (>= lemma-count lower)
                         (< lemma-count upper))
                level))
            data/estimate-levels)
      (last data/estimate-levels)))

(defn crossed-level-boundary? [{:keys [lower upper]} boundary]
  (and (< lower boundary)
       (> upper boundary)))

(defn estimate-level-label [lemma-estimate likely-range]
  (let [lower-level (estimate-level-for-count (:lower likely-range))
        upper-level (estimate-level-for-count (:upper likely-range))
        boundaries (map :lower (rest data/estimate-levels))
        borderline? (some (partial crossed-level-boundary? likely-range)
                          boundaries)]
    (if (and borderline?
             (not= (:id lower-level) (:id upper-level)))
      (str "borderline: " (:label lower-level) " to " (:label upper-level))
      (:label (estimate-level-for-count lemma-estimate)))))

(defn with-estimate-level [summary]
  (assoc summary :estimate-level (estimate-level-label (:lemma-estimate summary)
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

(defn completed-block-id [completed-block]
  (if (map? completed-block)
    (:id completed-block)
    completed-block))

(defn lower-anchor-needed? [answers completed-block-ids]
  (let [observed-ids (observed-stratum-ids (real-vocabulary-answers answers))]
    (and (empty? completed-block-ids)
         (seq observed-ids)
         (> (first observed-ids) 1)
         (not (high-confidence-pass? answers)))))

(defn adaptive-block-decision
  ([block answers]
   (adaptive-block-decision block answers []))
  ([{:keys [id lower-block-id higher-block-id floor?] :as block} answers completed-blocks]
   (let [block-answers (filterv #(and (real-vocabulary-answer? %)
                                      (= id (:adaptive-block-id %)))
                                answers)
         {:keys [real-item-count correct correct-rate]}
         (block-answer-summary block-answers)
         completed-block-ids (set (keep completed-block-id completed-blocks))
         route-lower? (<= correct-rate 0.15)
         route-higher? (>= correct-rate 0.85)
         initial-action (cond
                          (and route-lower? floor?) :report-floor
                          (and route-lower? lower-block-id) :route-lower
                          route-lower? :report
                          (and route-higher? higher-block-id) :route-higher
                          route-higher? :report
                          (and lower-block-id
                               (lower-anchor-needed? answers completed-block-ids))
                          :route-lower
                          :else :report)
         initial-next-block-id (case initial-action
                                 :route-lower lower-block-id
                                 :route-higher higher-block-id
                                 nil)
         revisit-completed? (contains? completed-block-ids initial-next-block-id)
         action (if revisit-completed?
                  :report
                  initial-action)
         next-block-id (when-not revisit-completed?
                         initial-next-block-id)]
     (assoc block
            :action action
            :next-block-id next-block-id
            :estimate-label (when (= :report-floor action)
                              "under 200")
            :correct correct
            :real-item-count real-item-count
            :correct-rate correct-rate
            :accuracy-pct (round-value (* 100 correct-rate))))))

(defn summarize-results
  ([questions answers]
   (summarize-results questions answers nil))
  ([_questions answers adaptive-decision]
   (let [evidence-answers (real-vocabulary-answers answers)
         total (count evidence-answers)
         correct (count (filter #(= :correct (:result %)) evidence-answers))
         dk (count (filter #(= :dk (:result %)) evidence-answers))
         wrong (count (filter #(= :wrong (:result %)) evidence-answers))
         review-answers (sorted-review-answers evidence-answers)
         posterior (with-estimate-level
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
               :review-answers review-answers
               :estimate-label (or (:estimate-label posterior)
                                   (:estimate-label adaptive-decision))
               :adaptive-decision adaptive-decision})))))
