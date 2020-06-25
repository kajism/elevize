(ns elevize.cljs.pages
  (:require [clojure.string :as str]
            [elevize.cljc.util :as cljc.util]
            [elevize.cljs.comp.alarm-badge :as alarm-badge]
            [elevize.cljs.comp.history-replay :as history-replay]
            [elevize.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [reagent.ratom :as ratom]
            [secretary.core :as secretary]))

(def pages (atom {}))

(defn add-page [kw comp-fn]
  (swap! pages assoc kw comp-fn))

(re-frame/reg-sub-raw
 :page-state
 (fn [db [_ page-id]]
   (ratom/reaction (get-in @db [:page-states page-id]))))

(re-frame/reg-event-db
 :page-state-set
 util/debug-mw
 (fn [db [_ page-id state]]
   (assoc-in db [:page-states page-id] state)))

(re-frame/reg-event-db
 :page-state-change
 util/debug-mw
 (fn [db [_ page-id key val]]
   ((if (fn? val) update-in assoc-in) db [:page-states page-id key] val)))

(re-frame/reg-sub-raw
 ::current-page
 (fn [db _]
   (ratom/reaction (:current-page @db))))

(re-frame/reg-event-db
 :set-current-page
 util/debug-mw
 (fn [db [_ current-page]]
   (assoc db :current-page current-page)))

(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (re-frame/dispatch [:set-current-page :main]))

(re-frame/reg-sub-raw
 ::msg
 (fn [db [_ kw]]
   (ratom/reaction (get-in @db [:msg kw]))))

(re-frame/reg-event-db
 :set-msg
 util/debug-mw
 (fn [db [_ kw msg rollback-db]]
   (when (and msg (= :info kw))
     (js/setTimeout #(re-frame/dispatch [:set-msg :info nil]) 3000))
   (let [db (or rollback-db db)]
     (assoc-in db [:msg kw] msg))))

(defn error-msg-popup [title msg on-close]
  [re-com/modal-panel
   :backdrop-on-click on-close
   :child [re-com/alert-box
           :alert-type :danger
           :closeable? true
           :heading title
           :body msg
           :on-close on-close]])

(defn page []
  (let [current-page @(re-frame/subscribe [::current-page])
        error-msg @(re-frame/subscribe [::msg :error])
        info-msg @(re-frame/subscribe [::msg :info])
        time-info @(re-frame/subscribe [:elevize.cljs.device-states/time])
        orphan? (:orphan? @(re-frame/subscribe [:elevize.cljs.common/path-value [:elevize.cljs.core/version-info]]))]
    (if-not current-page
      [re-com/throbber]
      [:div
       (if orphan?
         [:div#ebs-time.error
          [history-replay/replay-settings]]
         [:div#ebs-time
          [:span {:class (if (:fresh-data? time-info) "fresh" "stale")}
           [:b
            (when (= js/Date (type (:instant time-info)))
              (cljc.util/to-format (:instant time-info) cljc.util/HHmmss))
            (when-not (:fresh-data? time-info)
              (str " (" (:last-update-sec time-info) "s)"))]]
          [alarm-badge/badge]])
       (when-not (str/blank? info-msg)
         [re-com/alert-box
          :alert-type :info
          :body info-msg
          :style {:position "fixed"}])
       [(get @pages current-page)]
       (when-not (str/blank? error-msg)
         [error-msg-popup "Systémová chyba" error-msg #(re-frame/dispatch [:set-msg :error nil])])])))
