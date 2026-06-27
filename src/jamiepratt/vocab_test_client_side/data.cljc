(ns jamiepratt.vocab-test-client-side.data)

(def sentence-block-size 80)

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
