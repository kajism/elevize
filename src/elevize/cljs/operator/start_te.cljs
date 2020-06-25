(ns elevize.cljs.operator.start-te
  (:require [elevize.cljs.comp.status-table :as status-table]
            [elevize.cljs.pages :as pages]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]
            [taoensso.timbre :as timbre]
            [elevize.cljc.util :as cljc.util]))

(defn page []
  (let [device-no @(re-frame/subscribe [:elevize.cljs.common/path-value [::device-no]])
        devices-by-code @(re-frame/subscribe [:elevize.cljs.common/entities-by :device :code])
        device-states @(re-frame/subscribe [:elevize.cljs.device-states/current])
        vars-by-device-code&name @(re-frame/subscribe [:elevize.cljs.device-states/vars-by-device-code&name])
        enums-by-group @(re-frame/subscribe [:elevize.cljs.enum-item/by-group&order])
        user @(re-frame/subscribe [:auth-user])]
    [:div [:h3 "Start zplynovace " device-no]
     (if-not devices-by-code
       [re-com/throbber]
       [:div
        [:table.table.tree-table.table-hover.table-striped
         [:thead
          [:tr
           [:th "Kód zařízení"]
           [:th "Název zařízení"]
           [:th "Režim"]]]
         [:tbody
          (doall
           (for [device (->> (keep devices-by-code
                                   ["VH1" "VTRCH1" "VTSCH1" "VT1" "VTVT1" "SV1" "SVO1" "ZP1"
                                    (when (= device-no "3")
                                      "OH2")
                                    (str "TOS_P" device-no) (str "TOS_O" device-no) (str "TE" device-no)]) )]
             (into
              ^{:key (:id device)}
              [:tr
               ^{:key 1000}
               [:td (:code device)]
               ^{:key 999}
               [:td (:title device)]]
              (status-table/device-status&commands device device-states vars-by-device-code&name enums-by-group (cljc.util/power-user? user)))))]]])]))

(pages/add-page ::page #'page)

(secretary/defroute "/start-te/:no" [no]
  (re-frame/dispatch [:elevize.cljs.common/set-path-value [::device-no] no])
  (re-frame/dispatch [:set-current-page ::page]))
