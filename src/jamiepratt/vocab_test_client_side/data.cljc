(ns jamiepratt.vocab-test-client-side.data)

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

(def band-sizes
  {:B1 250
   :B2 250
   :B3 500
   :B4 1000
   :B5 1500
   :B6 2500})
