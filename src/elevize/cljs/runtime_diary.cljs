(ns elevize.cljs.runtime-diary
  (:require [clojure.string :as str]
            [elevize.cljc.util :as cljc.util]
            [elevize.cljs.common :as common]
            [elevize.cljs.comp.buttons :as buttons]
            [elevize.cljs.comp.data-table :as data-table]
            [elevize.cljs.pages :as pages]
            [elevize.cljs.util :as util]
            [reagent.ratom :as ratom]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]
            [taoensso.timbre :as timbre]
            [cljs-time.coerce :as tc]
            [cljs-time.core :as t]))

(re-frame/reg-sub-raw
 ::rows
 (fn [db [_]]
   (let [items (re-frame/subscribe [:entities :runtime-diary])
         page-state (re-frame/subscribe [:page-state :runtime-diary])]
     (ratom/reaction
      (cond->> (or (vals @items) [])
        (some? (::from @page-state))
        (filter (let [dt (tc/from-date (::from @page-state))]
                  #(not (t/before? (tc/from-date (:from %)) dt))))
        (some? (::to @page-state))
        (filter (let [dt (tc/from-date (::to @page-state))]
                  #(not (t/after? (tc/from-date (:from %)) dt)))))))))

(defn table []
  (let [items (re-frame/subscribe [::rows])
        user (re-frame/subscribe [:auth-user])
        table-state (re-frame/subscribe [:table-state :runtime-diary])]
    (fn []
      (if-not @items
        [re-com/throbber]
        [:div
         [data-table/data-table
          :table-id :runtime-diary
          :colls [[[re-com/md-icon-button
                    :md-icon-name "zmdi-refresh"
                    :tooltip "Načíst ze serveru"
                    :on-click #(re-frame/dispatch [:entities-load :runtime-diary])]
                   (fn [row]
                     (when (and (= (:id row) (:selected-row-id @table-state)))
                       [re-com/h-box
                        :gap "5px" :justify :end
                        :children
                        [(when (= (:login @user) "kajism")
                           [buttons/delete-button #(re-frame/dispatch [:entity-delete :runtime-diary (:id row)])])]]))
                   :none]
                  {:header "Datum"
                   :val-fn :from
                   :td-comp (fn [& {:keys [value row row-state]}]
                              [:td
                               (some-> value (cljc.util/to-format cljc.util/ddMMyyyy))])}
                  ["Zařízení" :device-code]
                  {:header [:div.center "Čas běhu"]
                   :val-fn #(/ (:runtime-mins %) 60)
                   :td-comp (fn [& {:keys [value row row-state]}]
                              [:td.right
                               (cljc.util/mins--hm (:runtime-mins row))])
                   :header-modifier :sum}
                  {:header [:div.center "Čas turbíny"]
                   :val-fn #(/ (:turbine-runtime-mins %) 60)
                   :td-comp (fn [& {:keys [value row row-state]}]
                              [:td.right
                               (cljc.util/mins--hm (:turbine-runtime-mins row))])
                   :header-modifier :sum}
                  [[:div.center "T max"] :t-max]
                  [[:div.center "T avg"] :t-avg]
                  [[:div.center "Komín avg"] :t-avg-exh]
                  [[:div.center "Palivo kg"] :fuel-kg :sum]
                  [[:div.center "Váha 1 -kg"] :scale1-kg :sum]
                  [[:div.center "Váha 2 -kg"] :scale2-kg :sum]
                  ["Vypočteno" :created]]
          :rows items
          :desc? true]]))))

(defn page []
  (let [user @(re-frame/subscribe [:auth-user])
        page-state @(re-frame/subscribe [:page-state :runtime-diary])]
    [re-com/v-box :children
     [[re-com/h-box :gap "10px" :align :center
       :children
       [[:h3 "Provozní data"]
        [:h5 "Od"]
        [re-com/input-text
         :model (cljc.util/to-format (::from page-state) cljc.util/ddMMyyyy)
         :on-change #(re-frame/dispatch [:page-state-change :runtime-diary ::from (cljc.util/from-dMyyyy %)])
         :validation-regex #"^\d{0,2}$|^\d{0,2}\.\d{0,2}$|^\d{0,2}\.\d{0,2}\.\d{0,4}$"
         :width "100px"]
        [:h5 "Do"]
        [re-com/input-text
         :model (cljc.util/to-format (::to page-state) cljc.util/ddMMyyyy)
         :on-change #(re-frame/dispatch [:page-state-change :runtime-diary ::to (cljc.util/from-dMyyyy %)])
         :validation-regex #"^\d{0,2}$|^\d{0,2}\.\d{0,2}$|^\d{0,2}\.\d{0,2}\.\d{0,4}$"
         :width "100px"]]]
      (if-not (cljc.util/power-user? user)
        [re-com/throbber]
        [table])]]))

(pages/add-page ::page #'page)

(secretary/defroute "/provozni-data" []
  (re-frame/dispatch [:set-current-page ::page]))
