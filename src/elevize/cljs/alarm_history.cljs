(ns elevize.cljs.alarm-history
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
            [elevize.cljs.comp.alarm-badge :as alarm-badge]))

#_(re-frame/reg-event-db
 ::received
 util/debug-mw
 (fn [db [_ alarm]]
   (when-not (seq (::alarms db))
     (.. js/document (getElementById "alarm-sound") (play)))
   (-> db
       (update ::alarms conj alarm)
       (update :alarm-history assoc (:id alarm) alarm))))

(defn table []
  (let [items (re-frame/subscribe [:entities :alarm-history])
        current-alarms (re-frame/subscribe [:entities :alarm])]
    (fn []
      [:div
       [:h3 "Aktuální alarmy"]
       [data-table/data-table
        :table-id :alarms
        :rows-per-page 15
        :colls [[[re-com/md-icon-button
                  :md-icon-name "zmdi-refresh"
                  :tooltip "Načíst ze serveru"
                  :on-click #(re-frame/dispatch [:entities-load :alarm])]
                 (fn [row]
                   "")
                 :none]
                ["Opětovně přijat" :last-received]
                {:header "Zařízení"
                 :val-fn :device-code
                 :td-comp (fn [& {:keys [value row row-state]}]
                            [:td [:a {:href (str "#/stavy/" value)} value]])}
                {:header "KKS"
                 :val-fn :kks
                 :td-comp (fn [& {:keys [value row row-state]}]
                            [:td [:a {:href (str "#/stavy/" (:device-code row) "/" value)} value]])}
                ["Zpráva" :msg]
                {:header "Trvaní minut"
                 :val-fn :duration-min
                 :td-comp (fn [& {:keys [value row row-state]}]
                            [:td.center value])}
                ["Od" :timestamp]]
        :rows current-alarms
        :desc? true]

       [:h3 "Historie příchozích alarmů"]
       [alarm-badge/table :table-id :alarm-history :items items]])))

(defn detail []
  (let [item @(re-frame/subscribe [:entity-edit :alarm-history])
        devices @(re-frame/subscribe [:entities :device])]
    [:div
     [:h3 "Detail alarmu"]
     [re-com/v-box :gap "5px"
      :children
      [[:label "Kdy"]
       [:p (cljc.util/to-format (:timestamp item) cljc.util/ddMMyyyyHHmmss)]
       [:label "Doba trvaní alarmu v minutách"]
       [:p (:duration-min item)]
       [:label "Zařízení"]
       [:p (some->> item :device-id devices ((juxt :code :title)) (str/join " - "))]
       [:label "KKS"]
       [:p (:kks item)]
       [:label "Zpráva"]
       [:p (:msg item)]
       [:label "Kód alarmu"]
       [:p (:alarm-id item)]
       [:label "Kód info 1"]
       [:p (:alarm-info-id item)]
       [:label "Kód info 2"]
       [:p (:alarm-info2-id item)]
       [re-com/button :label "Zpět" :on-click #(-> js/window .-history .back)]]]]))

(pages/add-page ::table #'table)
(pages/add-page ::detail #'detail)

(secretary/defroute "/alarmy" []
  (re-frame/dispatch [:set-current-page ::table]))

(secretary/defroute #"/alarm/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :alarm-history (util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page ::detail]))

(common/add-kw-url :alarm-history "alarm-history")
