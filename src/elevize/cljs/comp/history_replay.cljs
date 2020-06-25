(ns elevize.cljs.comp.history-replay
  (:require [cljs-time.core :as t]
            [clojure.string :as str]
            [elevize.cljc.calc-derived :as cljc.calc-derived]
            [elevize.cljc.util :as cljc.util]
            [elevize.cljs.sente :refer [server-call]]
            [elevize.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre]))

(defn- parse-date-time [{:keys [date time]}]
  (cljc.util/from-format (str date " " time) cljc.util/dMyyyyHmm))

(def refresh-interval 1000)

(defn- next-inst [{:keys [speed inst]}]
  (let [ms-per-tick (* refresh-interval speed)]
    (js/Date. (+ (.getTime inst) ms-per-tick))))

(re-frame/reg-event-db
 ::start
 util/debug-mw
 (fn [db [_]]
   (let [from (parse-date-time (::replay-settings db))
         replay-settings (-> (::replay-settings db)
                             (assoc :inst from
                                    :state :running))
         to (next-inst replay-settings)]
     (server-call [:device-state/select {:from from
                                          :to to
                                          :full-state? true}]
                  [::append-device-states from])
     (-> db
         (assoc ::replay-settings (assoc replay-settings
                                         :inst to
                                         :waiting-for-response? true))
         (assoc :elevize.cljs.device-states/history [])
         (assoc :masyst.cljs.dygraph/selected-x nil)))))

(re-frame/reg-event-db
 ::next-tick
 ;;util/debug-mw
 (fn [db [_]]
   (if (or (not (= :running (get-in db [::replay-settings :state])))
           (get-in db [::replay-settings :waiting-for-response?]))
     db
     (let [from (get-in db [::replay-settings :inst])
           to (next-inst (::replay-settings db))]
       (server-call [:device-state/select {:from from
                                            :to to}]
                    [::append-device-states from])
       (update db ::replay-settings #(assoc % :inst to
                                            :waiting-for-response? true))))))

(defonce next-tick-handler-installed (atom false))

(when-not @next-tick-handler-installed
  (js/setInterval #(re-frame/dispatch [::next-tick]) refresh-interval)
  (reset! next-tick-handler-installed true))

(re-frame/reg-event-db
 ::append-device-states
 ;;util/debug-mw
 (fn [db [_ from device-states]]
   (-> db
       (update :elevize.cljs.device-states/history
               (fn [history]
                 (reduce (fn [out changes]
                           (cljc.calc-derived/merge-device-states-history
                            out changes
                            (:elevize.cljs.power-avg-settings/avg-settings db)))
                         history
                         device-states)))
       (assoc :masyst.cljs.dygraph/selected-x nil)
       (assoc-in [::replay-settings :waiting-for-response?] false)
       (cond->
           (and (empty? device-states)
                #_(some->
                 (get-in db [::replay-settings :inst])
                 (.getTime)
                 (> (.getTime (js/Date.)))))
         (update ::replay-settings #(assoc %
                                           :state :paused
                                           :inst (-> db
                                                     (get :elevize.cljs.device-states/history)
                                                     (peek)
                                                     (get-in ["EB1" "Cas"]))))))))

(defn replay-settings []
  (let [replay-settings @(re-frame/subscribe [:elevize.cljs.common/path-value [::replay-settings]])
        current-x @(re-frame/subscribe [:elevize.cljs.common/path-value [:elevize.cljs.dygraph/selected-x]])]
    (when-not replay-settings
      (re-frame/dispatch [:elevize.cljs.common/set-path-value
                          [::replay-settings]
                          {:date (cljc.util/to-format (js/Date.) cljc.util/ddMMyyyy)
                           :time "06:00"
                           :speed 60
                           :state nil}]))
    [re-com/h-box :gap "5px" :align :center :children
     [[re-com/label :label "Datum"]
      [re-com/input-text :width "100px"
       :model (str (:date replay-settings))
       :on-change #(re-frame/dispatch [:elevize.cljs.common/set-path-value [::replay-settings :date] (cljc.util/full-dMyyyy %)])]
      [re-com/label :label "Čas"]
      [re-com/input-text :width "60px"
       :model (str (:time replay-settings))
       :on-change #(re-frame/dispatch [:elevize.cljs.common/set-path-value [::replay-settings :time] (cljc.util/full-HHmm %)])
       :validation-regex #"^(\d{0,2}):?(\d{1,2})?$"]

      [re-com/label :label "Rychlost [s]"]
      [re-com/input-text :width "60px"
       :model (str (:speed replay-settings))
       :on-change #(re-frame/dispatch [:elevize.cljs.common/set-path-value [::replay-settings :speed] (util/parse-int %)])
       :validation-regex #"^(\d{1,4})$"]

      (if (or (= :running (:state replay-settings))
              (= :paused (:state replay-settings)))
        [re-com/button :label "Vypnout" :on-click #(re-frame/dispatch [:elevize.cljs.common/set-path-value
                                                                       [::replay-settings :state] nil])]
        [re-com/button :label "Spustit" :on-click #(re-frame/dispatch [::start])])
      (when (= :running (:state replay-settings))
        [re-com/button :label "Pauza" :on-click #(re-frame/dispatch [:elevize.cljs.common/set-path-value
                                                                     [::replay-settings :state] :paused])])
      (when (= :paused (:state replay-settings))
        [re-com/button :label "Pokračovat" :on-click #(re-frame/dispatch [:elevize.cljs.common/set-path-value
                                                                          [::replay-settings :state] :running])])

      (when (:inst replay-settings)
        [re-com/label :label (str "Načteno: " (cljc.util/to-format (:inst replay-settings) cljc.util/ddMMyyyyHHmmss))])

      (when current-x
        [re-com/label :label (str "Označeno: " (cljc.util/to-format (js/Date. current-x) cljc.util/ddMMyyyyHHmmss))])]]))


