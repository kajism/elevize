(ns elevize.cljs.core
  (:require [clojure.string :as str]
            [elevize.cljc.calc-derived :as cljc.calc-derived]
            [elevize.cljs.alarm-history :as alarm-history]
            [elevize.cljs.commands]
            [elevize.cljs.device-states]
            [elevize.cljs.device]
            [elevize.cljs.dygraph]
            [elevize.cljs.enum-item]
            [elevize.cljs.import-xlsx]
            [elevize.cljs.log]
            [elevize.cljs.pages :as pages]
            [elevize.cljs.plc-msg-history]
            [elevize.cljs.operator.energoblok2]
            [elevize.cljs.operator.start-te]
            [elevize.cljs.operator.zasobovani-palivem]
            [elevize.cljs.operator.zplynovac]
            [elevize.cljs.operator.zplynovace]
            [elevize.cljs.power-avg-settings]
            [elevize.cljs.power-history]
            [elevize.cljs.runtime-diary]
            [elevize.cljs.sente :refer [server-call start-router!]]
            [elevize.cljs.subsystem]
            [elevize.cljs.inventory-item]
            [elevize.cljs.inventory-tx]
            [elevize.cljs.svg-component]
            [elevize.cljs.status-history]
            [elevize.cljs.status-history-export]
            [elevize.cljs.user]
            [elevize.cljs.util :as util]
            [elevize.cljs.var-group]
            [elevize.cljs.variable]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [reagent.ratom :as ratom]
            [secretary.core :as secretary]
            [taoensso.timbre :as timbre]
            [elevize.cljc.util :as cljc.util])
  (:import goog.History))

(enable-console-print!)

;;https://github.com/ptaoussanis/timbre/blob/master/src/taoensso/timbre/appenders/core.cljx
(defn level->logger [level]
  (or
   (case level
     :trace  js/console.trace
     :debug  js/console.debug
     :info   js/console.info
     :warn   js/console.warn
     :error  js/console.error
     :fatal  js/console.error
     :report js/console.info)
   js/console.log))

(timbre/set-config!
 {:level (if ^boolean goog.DEBUG :debug :info)
  :appenders {::console-appender
              {:enabled? true
               :fn (fn [data]
                     #_(js/console.log data)
                     (if (not-empty (:vargs data))
                       (apply (level->logger (:level data)) (:vargs data))
                       ((level->logger (:level data)) (force (:msg_ data)))))}}})

(re-frame/reg-sub-raw
 :auth-user
 (fn [db [_]]
   (ratom/reaction (::auth-user @db))))

(re-frame/reg-event-db
 ::init-load
 util/debug-mw
 (fn [db [_]]
   (server-call [:user/auth {}]
                [:elevize.cljs.common/set-path-value [::auth-user]])
   (server-call [:elevize/version-info {}]
                [:elevize.cljs.common/set-path-value [::version-info]])
   db))

(re-frame/reg-event-db
 ::init-app
 util/debug-mw
 (fn [db [_]]
   (re-frame/dispatch [::init-load])
   (re-frame/dispatch [:elevize.cljs.device-states/load])
   (re-frame/dispatch [:elevize.cljs.common/set-path-value [:elevize.cljs.power-avg-settings/avg-settings]
                       {:secs [nil nil nil]
                        :cached-avg? false
                        :vyhrevnost 17
                        :fuel-table cljc.calc-derived/default-fuel-table}])
   (-> db
       ;;init db
       (update-in [:elevize.cljs.device-states/variables-filter :device-code] #(or % "vse"))
       (update :elevize.cljs.comp.alarm-badge/ack-ids #(or % #{})))))

(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

(defn menu [user orphan?]
  [:nav.navbar.navbar-default
   [:div.container-fluid
    [:div.navbar-header
     [:button.navbar-toggle {:type "button" :data-toggle "collapse" :data-target "#elevize-navbar"}
      [:span.icon-bar]
      [:span.icon-bar]
      [:span.icon-bar]]
     [:a {:href "#"}
      [:img {:src "img/logo.svg" :alt "Elevize" :title "Elevize" :height "35px"}]]]
    [:div#elevize-navbar.collapse.navbar-collapse
     [:ul.nav.navbar-nav
      (when (cljc.util/power-user? user)
        [:li [:a {:href "#/stavy" :data-toggle "collapse" :data-target "#elevize-navbar"} "Stavy"]])
      [:li [:a {:href "#/prikazy" :data-toggle "collapse" :data-target "#elevize-navbar"} "Příkazy"]]
      [:li [:a {:href "#/alarmy" :data-toggle "collapse" :data-target "#elevize-navbar"} "Alarmy"]]
      [:li [:a {:href "#/komunikace" :data-toggle "collapse" :data-target "#elevize-navbar"} "Komunikace"]]
      [:li.dropdown
       [:a.dropdown-toggle  {:data-toggle "dropdown" :href "#"} "Obsluha" [:span.caret]]
       [:ul.dropdown-menu
        [:li [:a {:href "#/start-te/3" :data-toggle "collapse" :data-target "#elevize-navbar"} "Start zplynovace 3"]]
        [:li [:a {:href "#/start-te/4" :data-toggle "collapse" :data-target "#elevize-navbar"} "Start zplynovace 4"]]
        [:li [:a {:href "#/energoblok2" :data-toggle "collapse" :data-target "#elevize-navbar"} "Energoblok 2"]]
        [:li [:a {:href "#/zplynovace" :data-toggle "collapse" :data-target "#elevize-navbar"} "Zplynovače 3 a 4"]]
        [:li [:a {:href "#/zplynovac/TE3" :data-toggle "collapse" :data-target "#elevize-navbar"} "Zplynovač 3"]]
        [:li [:a {:href "#/zplynovac/TE4" :data-toggle "collapse" :data-target "#elevize-navbar"} "Zplynovač 4"]]
        [:li [:a {:href "#/zasobovani-palivem" :data-toggle "collapse" :data-target "#elevize-navbar"} "Zásobování palivem"]]]]
      (when (and orphan? ((:-rights user) :inventory-item/select))
        [:li.dropdown
         [:a.dropdown-toggle  {:data-toggle "dropdown" :href "#"} "Sklad" [:span.caret]]
         [:ul.dropdown-menu
          [:li [:a {:href "#/sklad" :data-toggle "collapse" :data-target "#elevize-navbar"} "Položky"]]
          [:li [:a {:href "#/sklad-pohyby" :data-toggle "collapse" :data-target "#elevize-navbar"} "Pohyby"]]]])
      (when (cljc.util/power-user? user)
        [:li.dropdown
         [:a.dropdown-toggle  {:data-toggle "dropdown" :href "#"} "Nastavení" [:span.caret]]
         [:ul.dropdown-menu
          [:li [:a {:href "#/subsystems" :data-toggle "collapse" :data-target "#elevize-navbar"} "Subsystémy"]]
          [:li [:a {:href "#/devices" :data-toggle "collapse" :data-target "#elevize-navbar"} "Zařízení"]]
          [:li [:a {:href "#/variables" :data-toggle "collapse" :data-target "#elevize-navbar"} "Proměnné"]]
          [:li [:a {:href "#/var-groups" :data-toggle "collapse" :data-target "#elevize-navbar"} "Skupiny proměnných"]]
          [:li [:a {:href "#/enum-items" :data-toggle "collapse" :data-target "#elevize-navbar"} "Výčtové položky"]]
          (when ((:-rights user) :import-xlsx/download-and-import)
            [:li [:a {:href "#/import-xlsx" :data-toggle "collapse" :data-target "#elevize-navbar"} "Import konfiguraze z Google drive"]])
          (when (cljc.util/admin? user)
            [:li [:a {:href "#/users" :data-toggle "collapse" :data-target "#elevize-navbar"} "Uživatelé"]])
          (when (cljc.util/admin? user)
            [:li [:a {:href "#/historie-stavu" :data-toggle "collapse" :data-target "#elevize-navbar"} "Historie stavů"]])
          (when (cljc.util/admin? user)
            [:li [:a {:href "#/export-stavu" :data-toggle "collapse" :data-target "#elevize-navbar"} "Export historie stavů"]])
          (when (cljc.util/admin? user)
            [:li [:a {:href "#/prumerovani-vykonu" :data-toggle "collapse" :data-target "#elevize-navbar"} "Plovoucí průměrování výkonu"]])
          (when (and orphan? (cljc.util/admin? user))
            [:li [:a {:href "#/historie-vykonu" :data-toggle "collapse" :data-target "#elevize-navbar"} "Historie výkonu"]])
          (when orphan?
            [:li [:a {:href "#/provozni-data" :data-toggle "collapse" :data-target "#elevize-navbar"} "Provozní data"]])]])]
     [:ul.nav.navbar-nav.navbar-right
      [:li [:a (if-not (str/blank? (:title user))
                 (:title user)
                 "<noname>")]]
      [:li [:a {:href "/logout"} "Odhlásit"]]]]]])

(defn page-main []
  (let [{:keys [version date orphan?]} @(re-frame/subscribe [:elevize.cljs.common/path-value [::version-info]])
        device-states @(re-frame/subscribe [:elevize.cljs.device-states/current])]
    [:div
     [:h3 "Elevize" (when orphan? " Sirotek")]
     [:div "Verze: " [:b version]]
     [:div "Datum: " [:b (cljc.util/to-format date cljc.util/ddMMyyyyHHmmss)]]
     [:div "EB1 verze: " [:b (get-in device-states ["EB1" "Verze"])]]]))

(pages/add-page :main #'page-main)

(defn main-app-area []
  (let [user @(re-frame/subscribe [:auth-user])
        {:keys [orphan?]} @(re-frame/subscribe [:elevize.cljs.common/path-value [::version-info]])]
    (if-not user
      [re-com/throbber]
      [:div
       [menu user orphan?]
       [:div.container-fluid
        [pages/page]]])))

(defn main []
  (start-router!)
  (hook-browser-navigation!)
  (if-let [node (.getElementById js/document "app")]
    (reagent/render [main-app-area] node)))

(main)
