(ns elevize.cljs.power-avg-settings
  (:require [clojure.string :as str]
            [elevize.cljc.calc-derived :as cljc.calc-derived]
            [elevize.cljc.util :as cljc.util]
            [elevize.cljs.common :as common]
            [elevize.cljs.comp.buttons :as buttons]
            [elevize.cljs.comp.data-table :as data-table]
            [elevize.cljs.pages :as pages]
            [elevize.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]))

(re-frame/reg-event-db
 ::set-vyhrevnost
 util/debug-mw
 (fn [db [_ vyhrevnost-MJkg]]
   (-> db
       (assoc-in [::avg-settings :vyhrevnost] vyhrevnost-MJkg)
       (assoc-in [::avg-settings :fuel-table] (->> cljc.calc-derived/default-fuel-table
                                                   (map (fn [[device-code fuel-table]]
                                                          [device-code (->> fuel-table
                                                                            (map (fn [[otacky row]]
                                                                                   [otacky (assoc row :tepelny-vykon
                                                                                                  (/ (* vyhrevnost-MJkg (:hm-prutok-kg-hod row))
                                                                                                     3.6))]))
                                                                            (into {}))]))
                                                   (into {}))))))

(defn power-avg-settings-form []
  (let [avg-settings @(re-frame/subscribe [:elevize.cljs.common/path-value [::avg-settings]])
        orphan? (:orphan? @(re-frame/subscribe [:elevize.cljs.common/path-value [:elevize.cljs.core/version-info]]))
        user @(re-frame/subscribe [:auth-user])]
    ;; initialized in ::init-app
    (if-not (cljc.util/admin? user)
      [re-com/throbber]
      [:div
       [:div.form-group
        [:label "Čas plovoucího průměru 1 [sekundy]"]
        [re-com/input-text
         :class "form-control"
         :model (str (get-in avg-settings [:secs 0]))
         :on-change #(re-frame/dispatch [:elevize.cljs.common/set-path-value [::avg-settings :secs 0] (util/parse-int %)])
         :validation-regex #"^\d*$"
         :width "100px"]]
       [:div.form-group
        [:label "Čas plovoucího průměru 2 [sekundy]"]
        [re-com/input-text
         :class "form-control"
         :model (str (get-in avg-settings [:secs 1]))
         :on-change #(re-frame/dispatch [:elevize.cljs.common/set-path-value [::avg-settings :secs 1] (util/parse-int %)])
         :validation-regex #"^\d*$"
         :width "100px"]]
       [:div.form-group
        [:label "Čas plovoucího průměru 3 [sekundy]"]
        [re-com/input-text
         :class "form-control"
         :model (str (get-in avg-settings [:secs 2]))
         :on-change #(re-frame/dispatch [:elevize.cljs.common/set-path-value [::avg-settings :secs 2] (util/parse-int %)])
         :validation-regex #"^\d*$"
         :width "100px"]]
       (when orphan?
         [:div.form-group
          [:label "Použít plovoucí průměrování?"]
          [re-com/checkbox
           :model (boolean (:cached-avg? avg-settings))
           :on-change #(re-frame/dispatch [:elevize.cljs.common/set-path-value [::avg-settings :cached-avg?] %])]])
       [:div.form-group
        [:label "Výhřevnost paliva MJ/kg"]
        [re-com/input-text
         :class "form-control"
         :model (str (:vyhrevnost avg-settings))
         :on-change #(re-frame/dispatch [::set-vyhrevnost (util/parse-int %)])
         :validation-regex #"^\d*$"
         :width "100px"]]])))


(pages/add-page :power-avg-settings #'power-avg-settings-form)

(secretary/defroute "/prumerovani-vykonu" []
  (re-frame/dispatch [:set-current-page :power-avg-settings]))

