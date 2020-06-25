(ns elevize.cljs.status-history
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
            [taoensso.timbre :as timbre]))

(defn table [items]
  (let [devices (re-frame/subscribe [:entities :device])
        variables(re-frame/subscribe [:entities :variable])
        table-state (re-frame/subscribe [:table-state :status-history])]
    (fn []
      (if-not @devices
        [re-com/throbber]
        [:div
         [data-table/data-table
          :table-id :status-history
          :colls [[[re-com/md-icon-button
                    :md-icon-name "zmdi-refresh"
                    :tooltip "Načíst ze serveru"
                    :on-click #(re-frame/dispatch [:entities-load :status-history])]
                   (fn [row] "")
                   :csv-export]
                  {:header "Kdy"
                   :val-fn :timestamp
                   :td-comp (fn [& {:keys [value row row-state]}]
                              [:td
                               (cljc.util/to-format value cljc.util/ddMMyyyyHHmmss)])}
                  ["Zařízení" #(-> % :variable-id (@variables) :device-id (@devices) :code)]
                  ["Proměnná" #(-> % :variable-id (@variables) :name)]
                  ["Hodnota" :value]]
          :rows items
          :desc? true]]))))

(defn page-status-history []
  (let [items (re-frame/subscribe [:entities :status-history])
        user @(re-frame/subscribe [:auth-user])]
    [:div
     [:h3 "Historie stavů"]
     (if-not (and @items (cljc.util/admin? user))
       [re-com/throbber]
       [table items])]))


(pages/add-page :status-history #'page-status-history)

(secretary/defroute "/historie-stavu" []
  (re-frame/dispatch [:set-current-page :status-history]))

