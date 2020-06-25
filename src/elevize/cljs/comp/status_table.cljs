(ns elevize.cljs.comp.status-table
  (:require [clojure.string :as str]
            [elevize.cljs.sente :refer [server-call]]
            [elevize.cljs.util :as util]
            [elevize.cljc.util :as cljc.util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]))

(re-frame/reg-event-db
 ::send-remote-cmd
 util/debug-mw
 (fn [db [_ cmd]]
   (server-call [:elevize/command {:msg cmd}]
                [:set-msg :info])
   db))

(defn- device-error-class [device-states device-code]
  (let [device-state (get device-states device-code)]
    (some->> ["FatalniChyba" "KritickaChyba" "Chyba" "Varovani"]
             (some #(when (cljc.util/bool (get device-state %)) %)))))

(defonce blink-handler-installed (atom false))

(when-not @blink-handler-installed
  (re-frame/dispatch [::blink])
  (reset! blink-handler-installed true))

(re-frame/reg-event-db
 ::blink
 ;;util/debug-mw
 (fn [db [_]]
   (js/setTimeout #(re-frame/dispatch [::blink]) (if (::blink-show? db) 150 850))
   (update db ::blink-show? not)))

(re-frame/reg-sub
 ::device-error-class
 (fn [db [_ device-code]]
   (let [device-states @(re-frame/subscribe [:elevize.cljs.device-states/current])
         blink-show? @(re-frame/subscribe [:elevize.cljs.common/path-value [::blink-show?]])]
     #_(str (device-error-class device-states device-code) (when-not blink-show? " opaque")) ;; not compatible with modal dialog
     (when blink-show?
       (device-error-class device-states device-code)))))

(defn mode-label [device-code device-states vars-by-device-code&name enums-by-group]
  (if (cljc.util/bool (get-in device-states [device-code "Manual"]))
    "Manuál"
    (let [mode-var-name "RezimAuto"
          mode-var (get-in vars-by-device-code&name [device-code mode-var-name])
          mode-var-enum (get enums-by-group (:data-type mode-var))
          var-value (get-in device-states [device-code mode-var-name])
          label (or (:label (get mode-var-enum var-value)) (str var-value) "")]
      (if (= label "Spuštěno")
        [:u label]
        label))))

(def row-count 1)

(defn status-table [device-code]
  (let [devices @(re-frame/subscribe [:entities :device])
        device-states @(re-frame/subscribe [:elevize.cljs.device-states/current])
        vars-by-device-code&name @(re-frame/subscribe [:elevize.cljs.device-states/vars-by-device-code&name])
        enums-by-group @(re-frame/subscribe [:elevize.cljs.enum-item/by-group&order])
        user @(re-frame/subscribe [:auth-user])]
    (if-not (and devices device-states)
      [re-com/throbber]
      [:table.status-table
       [:tbody
        [:tr
         [:td {:row-span (inc row-count) :style {:background-color "white"}}
          [:a {:href "#/stavy/vse"}
           (if (= "vse" device-code)
             [:b "vše"]
             "vše")]]]
        (doall
         (for [[idx device-row] (some->> (vals devices)
                                         (sort-by :code)
                                         (partition-all (Math/ceil (/ (count devices) row-count)))
                                         (map-indexed vector))]
           ^{:key idx}
           [:tr
            (doall
             (for [device device-row]
               ^{:key (:id device)}
               [:td {:class (or @(re-frame/subscribe [::device-error-class (:code device)]) "Ok")}
                (let [label (if (= device-code (:code device))
                              [:b (:code device)]
                              (:code device))]
                  (if (cljc.util/power-user? user)
                    [:a {:href (str "#/stavy/" (:code device))} label]
                    [:div label]))
                (mode-label (:code device) device-states vars-by-device-code&name enums-by-group)]))]))]])))

(defn control-button [label cmd disabled? active?]
  [re-com/button
   :label label
   :class (str "btn-sm "
               (case active?
                 nil "btn-primary"
                 true "btn-success"
                 false "btn-danger"))
   :disabled? disabled?
   :on-click #(re-frame/dispatch [::send-remote-cmd cmd])])


(defn control-buttons [device-code manual? odpocet-sec power-user?]
  [[control-button "Manuál"
    (str "MANUAL " device-code)
    (or manual?
        (and (not power-user?)
             (some-> device-code (str/starts-with? "OH"))))
    manual?]
   [:div " | "]
   [control-button "AUTO" (str "AUTO " device-code) (not manual?) (not manual?)]
   [control-button "START" (str "SET " device-code ":Spustit:=TRUE") manual? (when-not manual? true)]
   [control-button "STOP" (str "SET " device-code ":Odstavit:=TRUE") manual? (when-not manual? true)]
   [control-button "Nouze!" (str "SET " device-code ":OdstavitNouzove:=TRUE") manual? (when-not manual? true)]
   [control-button "Reset ch." (str "SET " device-code ":ResetChyba:=TRUE") false nil]
   [control-button "Stop odpočet" (str "SET " device-code ":ZastavitOdpocet:=TRUE") (not odpocet-sec) nil]
   [:div (str odpocet-sec (when odpocet-sec "s"))]])

(defn device-status&commands [device device-states vars-by-device-code&name enums-by-group power-user?]
  (cons
   ^{:key 998}
   [:td.buttons {:class (or (device-error-class device-states (:code device)) "Ok")}
    (mode-label (:code device) device-states vars-by-device-code&name enums-by-group)]
   (map-indexed (fn [idx x]
                  ^{:key idx}
                  [:td.buttons x])
                (control-buttons (:code device)
                                 (cljc.util/bool (get-in device-states [(:code device) "Manual"]))
                                 (when (cljc.util/bool (get-in device-states [(:code device) "ButtonZastavitOdpocet"]))
                                   (get-in device-states [(:code device) "OdpocetOdstaveni"]))
                                 power-user?))))
