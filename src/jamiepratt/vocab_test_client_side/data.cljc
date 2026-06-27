(ns jamiepratt.vocab-test-client-side.data)

(def sentence-block-size 80)

(def adaptive-real-item-count 80)

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

(def ordered-band-ids
  (mapv :id bands))

(def block-band-profile
  [{:band :B1
    :items 12}
   {:band :B2
    :items 16}
   {:band :B3
    :items 18}
   {:band :B4
    :items 16}
   {:band :B5
    :items 10}
   {:band :B6
    :items 8}])

(def band-sizes
  {:B1 250
   :B2 250
   :B3 500
   :B4 1000
   :B5 1500
   :B6 2500})
