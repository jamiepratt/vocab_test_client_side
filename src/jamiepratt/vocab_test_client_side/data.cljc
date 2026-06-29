(ns jamiepratt.vocab-test-client-side.data)

(def sentence-block-size 80)

(def adaptive-real-item-count 80)

(def lemma-inventory-size 10000)

(def lemma-stratum-size 1000)

(def live-estimate-min-real-answers 30)

(def level-options
  [{:id :absolute-beginner
    :api-level "absolute-beginner"
    :label "Absolute beginner / pre-A1"}
   {:id :a1
    :api-level "a1"
    :label "A1"}
   {:id :a2
    :api-level "a2"
    :label "A2"}
   {:id :b1
    :api-level "b1"
    :label "B1"}
   {:id :b2
    :api-level "b2"
    :label "B2"}
   {:id :c1
    :api-level "c1"
    :label "C1"}
   {:id :c2
    :api-level "c2"
    :label "C2"}])

(def level-options-by-id
  (into {} (map (juxt :id identity) level-options)))

(def adaptive-blocks
  [{:id :pre-a1
    :label "Absolute beginner / pre-A1"
    :request {:level "absolute-beginner" :block 0}
    :surface-rank-start 1
    :surface-rank-end 500
    :real-item-count adaptive-real-item-count
    :floor? true
    :lower-block-id nil
    :higher-block-id :pre-a1-plus}
   {:id :pre-a1-plus
    :label "Pre-A1 stretch"
    :request {:level "absolute-beginner" :block 1}
    :surface-rank-start 250
    :surface-rank-end 1000
    :real-item-count adaptive-real-item-count
    :lower-block-id :pre-a1
    :higher-block-id :a1}
   {:id :a1
    :label "A1"
    :request {:level "a1" :block 0}
    :surface-rank-start 1
    :surface-rank-end 2000
    :real-item-count adaptive-real-item-count
    :lower-block-id :pre-a1
    :higher-block-id :a2}
   {:id :a2
    :label "A2"
    :request {:level "a2" :block 0}
    :surface-rank-start 500
    :surface-rank-end 3000
    :real-item-count adaptive-real-item-count
    :lower-block-id :a1
    :higher-block-id :b1}
   {:id :b1
    :label "B1"
    :request {:level "b1" :block 0}
    :surface-rank-start 1000
    :surface-rank-end 5000
    :real-item-count adaptive-real-item-count
    :lower-block-id :a2
    :higher-block-id :b2}
   {:id :b2
    :label "B2"
    :request {:level "b2" :block 0}
    :surface-rank-start 2000
    :surface-rank-end 8000
    :real-item-count adaptive-real-item-count
    :lower-block-id :b1
    :higher-block-id :c1}
   {:id :c1
    :label "C1"
    :request {:level "c1" :block 0}
    :surface-rank-start 3000
    :surface-rank-end 10000
    :real-item-count adaptive-real-item-count
    :lower-block-id :b2
    :higher-block-id :c2}
   {:id :c2
    :label "C2"
    :request {:level "c2" :block 0}
    :surface-rank-start 5000
    :surface-rank-end 10000
    :real-item-count adaptive-real-item-count
    :lower-block-id :c1
    :higher-block-id :c2-plus}
   {:id :c2-plus
    :label "C2 stretch"
    :request {:level "c2" :block 1}
    :surface-rank-start 8000
    :surface-rank-end 15000
    :real-item-count adaptive-real-item-count
    :lower-block-id :c2
    :higher-block-id nil}])

(def adaptive-blocks-by-id
  (into {} (map (juxt :id identity) adaptive-blocks)))

(def starting-block-ids
  {:absolute-beginner :pre-a1
   :a1 :a1
   :a2 :a2
   :b1 :b1
   :b2 :b2
   :c1 :c1
   :c2 :c2})

(defn adaptive-block [block-id]
  (get adaptive-blocks-by-id block-id))

(defn starting-block [level-id]
  (adaptive-block (get starting-block-ids level-id :pre-a1)))

(defn overlapping-blocks? [a b]
  (and (some? a)
       (some? b)
       (<= (:surface-rank-start a)
           (:surface-rank-end b))
       (<= (:surface-rank-start b)
           (:surface-rank-end a))))

(def lemma-inventory-strata
  (mapv (fn [index]
          (let [start-rank (inc (* index lemma-stratum-size))
                end-rank (min lemma-inventory-size
                              (+ (* index lemma-stratum-size)
                                 lemma-stratum-size))]
            {:id (inc index)
             :start start-rank
             :end end-rank
             :width (inc (- end-rank start-rank))
             :label (str start-rank "-" end-rank)}))
        (range (quot lemma-inventory-size lemma-stratum-size))))

(def lemma-inventory-strata-by-id
  (into {} (map (juxt :id identity) lemma-inventory-strata)))

(defn lemma-inventory-stratum-id [lemma-inventory-rank]
  (when (and (number? lemma-inventory-rank)
             (pos? lemma-inventory-rank))
    (min (count lemma-inventory-strata)
         (inc (quot (dec (long lemma-inventory-rank))
                    lemma-stratum-size)))))

(def estimate-levels
  [{:id :pre-a1
    :label "Absolute beginner / pre-A1"
    :lower 0
    :upper 500}
   {:id :a1
    :label "A1"
    :lower 500
    :upper 1000}
   {:id :a2
    :label "A2"
    :lower 1000
    :upper 2000}
   {:id :b1
    :label "B1"
    :lower 2000
    :upper 3000}
   {:id :b2
    :label "B2"
    :lower 3000
    :upper 5000}
   {:id :c1
    :label "C1"
    :lower 5000
    :upper 8000}
   {:id :c2
    :label "C2"
    :lower 8000
    :upper lemma-inventory-size}])

(def frequency-buckets
  [{:id :rank-1-250
    :rank-start 1
    :rank-end 250
    :label "1-250"}
   {:id :rank-251-500
    :rank-start 251
    :rank-end 500
    :label "251-500"}
   {:id :rank-501-1000
    :rank-start 501
    :rank-end 1000
    :label "501-1K"}
   {:id :rank-1001-2000
    :rank-start 1001
    :rank-end 2000
    :label "1,001-2K"}
   {:id :rank-2001-3500
    :rank-start 2001
    :rank-end 3500
    :label "2,001-3.5K"}
   {:id :rank-3501-plus
    :rank-start 3501
    :rank-end nil
    :label "3,501+"}])

(def frequency-bucket-labels
  (into {} (map (juxt :id :label) frequency-buckets)))

(def ordered-frequency-bucket-ids
  (mapv :id frequency-buckets))

(defn frequency-bucket-id-for-rank [rank]
  (when (and (number? rank)
             (pos? rank))
    (let [rank (long rank)]
      (some (fn [{:keys [id rank-start rank-end]}]
              (when (and (<= rank-start rank)
                         (or (nil? rank-end)
                             (<= rank rank-end)))
                id))
            frequency-buckets))))
