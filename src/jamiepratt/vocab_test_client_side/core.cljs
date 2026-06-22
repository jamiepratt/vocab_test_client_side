(ns jamiepratt.vocab-test-client-side.core
  (:require
   [clojure.string :as str]
   [reagent.core :as r]
   [reagent.dom.client :as rdom]))

(defonce app-state
  (r/atom {:count 0
           :enabled? false
           :name ""}))

(defonce root
  (delay (rdom/create-root (.getElementById js/document "app"))))

(def button-class
  "inline-flex min-h-11 items-center justify-center justify-self-start rounded-md bg-emerald-700 px-4 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-emerald-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700")

(def panel-class
  "grid gap-3 border-t border-slate-200 py-5 last:pb-0")

(def panel-heading-class
  "text-base font-semibold text-slate-950")

(def metric-class
  "m-0 text-sm leading-6 text-slate-700")

(defn counter-panel [{:keys [count]}]
  [:section {:aria-labelledby "counter-heading"
             :class panel-class}
   [:h2 {:id "counter-heading"
         :class panel-heading-class} "Counter"]
   [:p {:class metric-class}
    [:span "Count: "]
    [:strong {:class "font-semibold text-slate-950"} count]]
   [:button {:type "button"
             :class button-class
             :on-click #(swap! app-state update :count inc)}
    "Increment count"]])

(defn status-panel [{:keys [enabled?]}]
  [:section {:aria-labelledby "status-heading"
             :class panel-class}
   [:h2 {:id "status-heading"
         :class panel-heading-class} "Status"]
   [:p {:class "m-0 flex items-center gap-2 text-sm leading-6 text-slate-700"}
    [:span "Status: "]
    [:strong {:class (if enabled?
                       "rounded-full bg-emerald-100 px-2.5 py-1 text-xs font-bold text-emerald-800"
                       "rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-700")}
     (if enabled? "Enabled" "Disabled")]]
   [:button {:type "button"
             :class button-class
             :on-click #(swap! app-state update :enabled? not)}
    (if enabled? "Disable feature" "Enable feature")]])

(defn greeting-panel [{:keys [name]}]
  (let [display-name (if (seq (str/trim name)) name "friend")]
    [:section {:aria-labelledby "greeting-heading"
               :class panel-class}
     [:h2 {:id "greeting-heading"
           :class panel-heading-class} "Greeting"]
     [:label {:for "name-input"
              :class "text-sm font-semibold text-slate-950"} "Name"]
     [:input {:id "name-input"
              :type "text"
              :class "min-h-11 w-full rounded-md border border-slate-300 bg-white px-3 text-slate-950 shadow-sm outline-none transition-colors placeholder:text-slate-400 focus:border-emerald-700 focus:ring-2 focus:ring-emerald-700/20"
              :value name
              :placeholder "Ada Lovelace"
              :on-change #(swap! app-state assoc :name (.. % -target -value))}]
     [:p {:class metric-class} "Hello, " [:strong {:class "font-semibold text-slate-950"} display-name] "!"]]))

(defn app []
  (let [state @app-state]
    [:section {:class "mx-auto grid min-h-screen w-full max-w-3xl place-items-center px-4 py-8 sm:px-6"}
     [:div {:class "w-full rounded-lg border border-slate-200 bg-white p-6 shadow-xl shadow-slate-950/10 sm:p-8"}
      [:p {:class "mb-3 text-xs font-bold uppercase text-amber-700"} "shadow-cljs + nREPL"]
      [:h1 {:class "mb-3 text-4xl font-bold leading-tight text-slate-950 sm:text-5xl"} "Vocab Test Client Side"]
      [:p {:class "mb-6 max-w-2xl text-base leading-7 text-slate-600"}
       "A tiny Reagent app with enough behavior to exercise fast browser tests."]
      [counter-panel state]
      [status-panel state]
      [greeting-panel state]]]))

(defn ^:dev/after-load init []
  (rdom/render @root [app]))
