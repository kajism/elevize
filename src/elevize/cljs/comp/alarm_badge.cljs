(ns elevize.cljs.comp.alarm-badge
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [elevize.cljc.util :as cljc.util]
            [elevize.cljs.comp.data-table :as data-table]
            [elevize.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [reagent.ratom :as ratom]
            [taoensso.timbre :as timbre]))

(re-frame/reg-sub-raw
 ::new-alarms
 (fn [db [_]]
   (let [ents (re-frame/subscribe [:entities :alarm])
         ack-ids (re-frame/subscribe [:elevize.cljs.common/path-value [::ack-ids]])]
     (ratom/reaction
      (->> (vals @ents)
           (remove #(contains? @ack-ids (:id %)))
           (vec) ;; must be here to avoid laziness
           )))))

(re-frame/reg-event-db
 ::acknowledge-all
 util/debug-mw
 (fn [db [_ alarm]]
   (update db ::ack-ids #(into (or % #{}) (keys (:alarm db))))))

(defn table [&{:keys [table-id items]}]
  (let [table-state (re-frame/subscribe [:table-state table-id])]
    (fn []
      (if-not @items
        [re-com/throbber]
        [data-table/data-table
         :table-id table-id
         :rows-per-page 15
         :colls [[[re-com/md-icon-button
                   :md-icon-name "zmdi-refresh"
                   :tooltip "Načíst ze serveru"
                   :on-click #(re-frame/dispatch [:entities-load :alarm-history])]
                  (fn [row]
                    (when (and (= (:id row) (:selected-row-id @table-state)))
                      [re-com/h-box
                       :gap "5px" :justify :end
                       :children
                       [[re-com/hyperlink-href
                         :href (str "#/alarm/" (:id row))
                         :label [re-com/md-icon-button
                                 :md-icon-name "zmdi-view-web"
                                 :tooltip "Detail"]]]]))
                  :none]
                 {:header "Kdy"
                  :val-fn :timestamp
                  :td-comp (fn [& {:keys [value row row-state]}]
                             [:td
                              (cljc.util/to-format value cljc.util/ddMMyyyyHHmmss)])}
                 {:header "Zařízení"
                  :val-fn :device-code
                  :td-comp (fn [& {:keys [value row row-state]}]
                             [:td [:a {:href (str "#/stavy/" value)} value]])}
                 {:header "KKS"
                  :val-fn :kks
                  :td-comp (fn [& {:keys [value row row-state]}]
                             [:td [:a {:href (str "#/stavy/" (:device-code row) "/" value)} value]])}
                 ["Zpráva" :msg]
                 ["Trvaní minut" :duration-min]]
         :rows items
         :desc? true]))))

(defn badge []
  (let [alarms (re-frame/subscribe [:entities :alarm])
        new-alarms (re-frame/subscribe [::new-alarms])]
    (fn []
      [:span.error
       [:a {:href "#/alarmy"
            :on-click #(re-frame/dispatch [::acknowledge-all])}
        [:b "Alarmy: " (count @alarms)]]
       (when (not-empty @new-alarms)
         [re-com/modal-panel
          :backdrop-on-click #(re-frame/dispatch [::acknowledge-all])
          :child [re-com/v-box :gap "10px" :align :center :children
                  [[table :table-id :new-alarms :items new-alarms]
                   [re-com/h-box :children
                    [[re-com/button :class "btn-danger" :label "Beru na vědomí" :on-click #(re-frame/dispatch [::acknowledge-all])]
                     [re-com/hyperlink-href :label [re-com/button :label [:b "Přejít na Alarmy"] :on-click #(re-frame/dispatch [::acknowledge-all])] :href "#/alarmy"]]]]]]
         #_[:span
            [re-com/md-icon-button :md-icon-name "zmdi-badge-check" :size :smaller :tooltip-position :right-center
             :on-click #(re-frame/dispatch [::acknowledge-all]) :tooltip "Beru na vědomí"]
            " "
            (cljc.util/shorten (str/join " --- " (map :msg @new-alarms)) 185)])])))
