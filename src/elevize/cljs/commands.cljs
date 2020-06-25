(ns elevize.cljs.commands
  (:require [elevize.cljc.util :as cljc.util]
            [elevize.cljs.comp.status-table :as status-table]
            [elevize.cljs.pages :as pages]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]
            [taoensso.timbre :as timbre]))

(defn page []
  (let [devices @(re-frame/subscribe [:entities :device])
        device-states @(re-frame/subscribe [:elevize.cljs.device-states/current])
        vars-by-device-code&name @(re-frame/subscribe [:elevize.cljs.device-states/vars-by-device-code&name])
        enums-by-group @(re-frame/subscribe [:elevize.cljs.enum-item/by-group&order])
        user @(re-frame/subscribe [:auth-user])]
    [:div [:h3 "Příkazy"]
     (if-not devices
       [re-com/throbber]
       [:div
        [:table.table.tree-table.table-hover.table-striped
         [:thead
          [:tr
           [:th "Konfigrační parametry"]
           [:th]
           [:th
            [status-table/control-button "Uložit" "SAVE_PARAMS"]]
           [:th]
           [:th
            [status-table/control-button "Načíst" "LOAD_PARAMS"]]
           [:th {:col-span "5"}]]
          [:tr
           [:th "Kód zařízení"]
           [:th "Název zařízení"]
           [:th "Režim"]]]
         [:tbody
          (doall
           (for [device (->> (vals devices) (sort-by :code))]
             (into
              ^{:key (:id device)}
              [:tr
               ^{:key 1000}
               [:td (:code device)]
               ^{:key 999}
               [:td (:title device)]]
              (status-table/device-status&commands device device-states vars-by-device-code&name enums-by-group (cljc.util/power-user? user)))))]]])]))

(pages/add-page ::page #'page)

(secretary/defroute "/prikazy" []
  (re-frame/dispatch [:set-current-page ::page]))
