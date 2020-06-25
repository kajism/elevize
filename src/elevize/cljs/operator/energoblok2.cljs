(ns elevize.cljs.operator.energoblok2
  (:require [elevize.cljc.util :as cljc.util]
            [elevize.cljs.comp.status-table :as status-table]
            [elevize.cljs.comp.zplynovac :as zplynovac]
            [elevize.cljs.device-states :as device-states]
            [elevize.cljs.dygraph :as dygraph]
            [elevize.cljs.operator.zasobovani-palivem :as zasobovani-palivem]
            [elevize.cljs.pages :as pages]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [secretary.core :as secretary]
            [taoensso.timbre :as timbre]
            [elevize.cljs.util :as util]
            [cljs-time.core :as t]
            [clojure.string :as str]))

(defn prutok-klapky-nabeh [device-no]
  (let [tab-model (reagent/atom :prutok)
        device-code (str "TE" device-no)]
    (fn []
      (let [device-states @(re-frame/subscribe [:elevize.cljs.device-states/current])]
        [re-com/v-box :children
         [[re-com/horizontal-tabs
           :model tab-model
           :tabs [{:id :prutok :label "Průtok"}
                  {:id :klapky :label "Klapky"}
                  {:id :nabeh :label "Provoz"}]
           :on-change #(reset! tab-model %)]
          (case @tab-model
            :prutok
            [:table.table.tree-table.table-hover.table-striped.table-sm
             [zplynovac/tr-for-path :label "Frekvence ventilátoru Hz" :path ["SV1" (str "rVentFreqReq" device-no)]]
             [zplynovac/flowswitch device-no]
             [zplynovac/tr-for-path :label [zplynovac/teplota "Teplota vzduchu"]
              :path ["SV1" (str "IO_TeplotaVzduchuPredZplynovacem" device-no)]
              :read-only? true]
             [zplynovac/tr-for-path :label "Tlak vzduchu kPa"
              :path ["SV1" (str "IO_TlakVzduchuPredZplynovacem" device-no)]
              :read-only? true]]
            :klapky
            [:table.table.tree-table.table-hover.table-striped.table-sm
             [zplynovac/klapka device-no 1]
             [zplynovac/klapka device-no 2]
             [zplynovac/klapka device-no 3]]
            :nabeh
            [re-com/v-box :children
             [(if (= 1 (get-in device-states [device-code "RezimAuto"]))
                [:table.table.tree-table.table-hover.table-striped.table-sm
                 [zplynovac/tr-for-path :label "Fáze startu" :path [device-code "FazeStartu"] :read-only? true]
                 [:tbody
                  [:tr
                   [:td {:class (str (when (not (cljc.util/bool (get-in device-states [device-code "IO_Spirala_Ready"])))
                                       "error"))}
                    "Spirála"]
                   [:td {:col-span 2}
                    [dygraph/variables-state-plotter [[device-code "IO_Spirala_Chod"]]
                     :width "200px"
                     :height "45px"
                     :last-minutes 10
                     :show-controls? false]]]]]
                [:table.table.tree-table.table-hover.table-striped.table-sm
                 [zplynovac/tr-for-path :label "Fáze běhu" :path [device-code "FazeBehu"] :read-only? true :kks? false]
                 [zplynovac/tr-for-path :label "Vývoj teploty" :path [device-code "vtVyvojTeploty"] :read-only? true :kks? false]
                 [zplynovac/tr-for-path :label [zplynovac/teplota "Očekávaná T"] :path [device-code "rOcekavanaTeplota"] :read-only? true :kks? false]
                 [zplynovac/tr-for-path :label [zplynovac/teplota "Extrapolace T"] :path [device-code "rExtrapolaceTeploty"] :read-only? true :kks? false]
                 [zplynovac/tr-for-path :label [zplynovac/teplota "Vývoj T elektro"] :path [device-code "vtVyvojTeploty_Elektro"] :read-only? true :kks? false]
                 [zplynovac/tr-for-path :label [zplynovac/teplota "Očekávaná T el."] :path [device-code "rOcekavanaTeplota_Elektro"] :read-only? true :kks? false]
                 [zplynovac/tr-for-path :label [zplynovac/teplota "Extrapolace T el."] :path [device-code "rExtrapolaceTeploty_Elektro"] :read-only? true :kks? false]])
              [zplynovac/casy device-code]]])]]))))

(defn zplynovac [device-no]
  (let [device-code (str "TE" device-no)
        user (re-frame/subscribe [:auth-user])]
    (fn []
      (let [devices-by-code @(re-frame/subscribe [:elevize.cljs.common/entities-by :device :code])
            device-states @(re-frame/subscribe [:elevize.cljs.device-states/current])
            vars-by-device-code&name @(re-frame/subscribe [:elevize.cljs.device-states/vars-by-device-code&name])
            enums-by-group @(re-frame/subscribe [:elevize.cljs.enum-item/by-group&order])
            oh-code (if (< device-no 3) "OH1" "OH2")
            eb1-time (or (zplynovac/time-by-path ["EB1" "Cas"] device-states) (js/Date.))]
        [re-com/v-box :children
         [[re-com/box :child
           [:table.table.tree-table.table-hover.table-striped.table-sm
            [:tbody
             (let [device (get devices-by-code device-code)]
               [:tr
                (seq
                 (into [^{:key 1000}
                        [:td.buttons [:h5 (:title device)]]
                        ^{:key 999}
                        [:td
                         (let [path ["EB1" (str "prProvozniRezimZplynovac" device-no)]]
                           [zplynovac/edit-value (get-in vars-by-device-code&name path) (get-in device-states path) enums-by-group])]]
                       (status-table/device-status&commands device device-states vars-by-device-code&name enums-by-group (cljc.util/power-user? @user))))])]]]
          [re-com/h-box :gap "5px" :justify :between :children
           [[re-com/box
             :child
             (let [temperature-path [device-code "IO_Teplota"]
                   temperature-path-elektro [device-code "IO_Teplota_Elektro"]
                   temperature-path-za-vymenikem ["SVO1" (str "IO_TeplotaZaVymenikem" device-no "PredKompresorem" device-no)]]
               [:table.table.tree-table.table-hover.table-striped.table-sm
                [:thead
                 [:tr
                  [:th "Teploty zplynovače "]
                  [:th.normal "Aktuálně"]
                  [:th.normal "Nastavit"]]]
                [zplynovac/tr-for-path :label [zplynovac/teplota "Cílová teplota"] :path [device-code "CilovaTeplotaBeh"]]
                [zplynovac/tr-for-path :label [zplynovac/teplota "Aktuální teplota" "green"] :path temperature-path :read-only? true]
                [zplynovac/tr-for-path :label [zplynovac/teplota "Cílová teplota elektro"] :path [device-code "CilovaTeplotaBeh_Elektro"]]
                [zplynovac/tr-for-path :label [zplynovac/teplota "Akt. teplota elektro" "navy"] :path temperature-path-elektro :read-only? true]
                [zplynovac/tr-for-path :label [zplynovac/teplota "Tepl. za výměníkem" "red"] :path temperature-path-za-vymenikem :read-only? true]
                [:tbody
                 [:tr
                  [:td {:col-span 3 :height "100px"}
                   [dygraph/variables-state-plotter [temperature-path
                                                     temperature-path-elektro
                                                     temperature-path-za-vymenikem]
                    :width "100%"
                    :height "150px"
                    :last-minutes 10
                    :colors ["green" "navy" "red"]
                    :show-controls? false]]]]])]

            [re-com/box :child
             (let [tosp-code (str "TOS_P" device-no)
                   toso-code (str "TOS_O" device-no)
                   vysypat-popel (cljc.util/with-date (or (zplynovac/time-by-path [toso-code "dtCasVysypaniPopelnice"] device-states) eb1-time)
                                   #(t/plus % (t/minutes (or (get-in device-states [toso-code "iTimeoutPopelnice"]) 0))))]
               [:table.table.tree-table.table-hover.table-striped.table-sm
                [:thead
                 [:tr
                  [:th "Palivo"]
                  [:th.normal "Aktuálně"]
                  [:th.normal "KKS"]]]
                [zplynovac/tr-for-path-bool
                 :label "Chod" #_[zplynovac/tos-status :label "Chod" :device-code tosp-code]
                 :path [device-code "PalivoOn"]
                 :path-err [device-code "ChybaPalivo"]
                 :settings [zplynovac/palivo-popup :device-no device-no]
                 :class @(re-frame/subscribe [:elevize.cljs.comp.status-table/device-error-class tosp-code])]
                [zplynovac/tr-for-path
                 :label "Frekvence pož. Hz"
                 :path [device-code "PalivoFreqReq"]
                 :read-only? true]
                [zplynovac/tr-for-path :label "Frekvence skut. Hz"
                 :path [device-code "IO_PalivoFreqAct"]
                 :read-only? true]
                [:thead
                 [:tr
                  [:th "Buben"]
                  [:th.normal "Aktuálně"]
                  [:th.normal "Nastavit"]]]
                [zplynovac/tr-for-path-bool
                 :label "Chod"
                 :path [device-code "BubenOn"]
                 :path-err [device-code "ChybaBuben"]]
                [zplynovac/tr-for-path :label "Frekvence pož. Hz" :path [device-code "BubenFreqReq"]]
                [zplynovac/tr-for-path :label "Frekvence skut. Hz"
                 :path [device-code "IO_BubenFreqAct"]
                 :read-only? true]
                [zplynovac/tr-for-path :label "Otáčky za minutu"
                 :path [device-code "IO_CidloOtacekBuben"]
                 :read-only? true]
                [:thead
                 [:tr {:class @(re-frame/subscribe [:elevize.cljs.comp.status-table/device-error-class toso-code])}
                  [:th "Odpopelnění"]
                  [:th]
                  [:th.normal [zplynovac/odpopelneni-popup :device-no device-no]]]]
                [:tbody
                 [:tr
                  [:td "Popel" #_[zplynovac/tos-status :label "Popel" :device-code toso-code]]
                  [:td.right "Vysyp za"
                   #_(cljc.util/to-format vysypat-popel cljc.util/HHmm)]
                  [:td.bold (cljc.util/since-days-hours-mins-sec eb1-time vysypat-popel)]]]])]

            [prutok-klapky-nabeh device-no]]]]]))))

(defn page []
  (let [oh-code "OH2"
        zobr-vykony? (reagent/atom nil)
        user (re-frame/subscribe [:auth-user])]
    (fn []
      (let [devices-by-code @(re-frame/subscribe [:elevize.cljs.common/entities-by :device :code])
            device-states @(re-frame/subscribe [:elevize.cljs.device-states/current])
            vars-by-device-code&name @(re-frame/subscribe [:elevize.cljs.device-states/vars-by-device-code&name])
            enums-by-group @(re-frame/subscribe [:elevize.cljs.enum-item/by-group&order])
            orphan? (:orphan? @(re-frame/subscribe [:elevize.cljs.common/path-value [:elevize.cljs.core/version-info]]))]
        (if-not (and devices-by-code vars-by-device-code&name enums-by-group)
          [re-com/throbber]
          [re-com/v-box :gap "5px" :children
           [[status-table/status-table nil]
            [re-com/h-box :gap "5px" :justify :between :children
             [[zplynovac 3]
              [zplynovac 4]]]
            [re-com/h-box :gap "5px" :justify :between :children
             [#_[re-com/box :child
               [:table.table.tree-table.table-hover.table-striped.table-sm
                [:thead
                 [:tr
                  [:th "Elektro teploty"]
                  [:th.normal "Aktuálně"]
                  [:th.normal "KKS"]]]
                [zplynovac/tr-for-path :label [zplynovac/teplota "Tepl. před turbínou" "green"]
                 :path ["SVO1" "IO_TeplotaVstup2"]
                 :read-only? true]
                [zplynovac/tr-for-path :label [zplynovac/teplota "Tepl. za turbínou 3" "navy"]
                 :path ["SVO1" "IO_TeplotaZaTurbinou3"]
                 :read-only? true]
                [zplynovac/tr-for-path :label [zplynovac/teplota "Tepl. za turbínou 4" "red"]
                 :path ["SVO1" "IO_TeplotaZaTurbinou4"]
                 :read-only? true]
                [zplynovac/tr-for-path :label [zplynovac/teplota "Teplota oleje"]
                 :path [oh-code "IO_TeplotaOleje"]
                 :read-only? true]
                [zplynovac/tr-for-path :label [zplynovac/teplota "Olej za výměníkem"]
                 :path [oh-code "IO_TeplotaOlejeZaVymenikem"]
                 :read-only? true]
                [zplynovac/tr-for-path :label [zplynovac/teplota "Teplota na turbíně" "orange"]
                 :path [oh-code "IO_TeplotaNaTurbine"]
                 :read-only? true]
                [:tbody
                 [:tr
                  [:td {:col-span 3 :height "100px"}
                   [dygraph/variables-state-plotter [["SVO1" "IO_TeplotaVstup2"]
                                                     ["SVO1" "IO_TeplotaZaTurbinou3"]
                                                     ["SVO1" "IO_TeplotaZaTurbinou4"]
                                                     [oh-code "IO_TeplotaNaTurbine"]]
                    :width "280px"
                    :height "180px"
                    :last-minutes 10
                    :colors ["green" "navy" "red" "orange"]
                    :show-controls? false]]]]
                [zplynovac/tr-for-path :label [zplynovac/teplota "Teplota spalin"]
                 :path ["SVO1" "IO_TeplotaVystup2"]
                 :read-only? true]]]

              #_[re-com/box :child
               [:table.table.tree-table.table-hover.table-striped.table-sm
                [:thead
                 [:tr
                  [:th "Elektro tlaky"]
                  [:th.normal "Aktuálně"]
                  [:th.normal "KKS"]]]
                [zplynovac/tr-for-path :label "Tlak spalin 3 kPa"
                 :path ["TE3" "IO_TlakSpalin"]
                 :read-only? true]
                [zplynovac/tr-for-path :label "Tlak spalin 4 kPa"
                 :path ["TE4" "IO_TlakSpalin"]
                 :read-only? true]
                [zplynovac/tr-for-path :label "Tlak za výměníkem 3 kPa"
                 :path ["SVO1" "IO_TlakZaVymenikem3PredKompresorem3"]
                 :read-only? true]
                [zplynovac/tr-for-path :label "Tlak za výměníkem 4 kPa"
                 :path ["SVO1" "IO_TlakZaVymenikem4PredKompresorem4"]
                 :read-only? true]
                [zplynovac/tr-for-path :label "Tlak před turbínou kPa"
                 :path ["SVO1" "IO_TlakVstup2"]
                 :read-only? true]
                [zplynovac/tr-for-path :label "Tlak za turbínou 3 kPa"
                 :path ["SVO1" "IO_TlakZaTurbinou3"]
                 :read-only? true]
                [zplynovac/tr-for-path :label "Tlak za turbínou 4 kPa"
                 :path ["SVO1" "IO_TlakZaTurbinou4"]
                 :read-only? true]
                [zplynovac/tr-for-path :label "Tlak oleje kPa"
                 :path [oh-code "IO_TlakOleje"]
                 :read-only? true]
                [:thead
                 [:tr
                  [:th "Turbosoustrojí"]
                  [:th.normal "Aktuálně"]
                  [:th.normal "KKS"]]]
                [zplynovac/tr-for-path :label "Snímač otáček 1 RPM"
                 :path [oh-code "IO_SnimacOtacek1"]
                 :read-only? true]
                [zplynovac/tr-for-path :label "Snímač otáček 2 RPM"
                 :path [oh-code "IO_SnimacOtacek2"]
                 :read-only? true]
                [zplynovac/tr-for-path :label "Snímač otáček 3 RPM"
                 :path [oh-code "IO_SnimacOtacek3"]
                 :read-only? true]]]

              [re-com/box :child
               [:table.table.tree-table.table-hover.table-striped.table-sm
                [:thead
                 [:tr
                  [:th "Odtah "]
                  [:th.normal "Aktuálně"]
                  [:th.normal [zplynovac/odtah-popup]]]]
                [zplynovac/tr-for-path-bool
                 :label "Bezp. kl. obtoku turbíny"
                 :path ["SVO1" "IO_KlapkaBezpecnostniTurbina2"]
                 :read-only? true
                 :klapka? true
                 :obracena-logika? true]
                [zplynovac/tr-for-path-bool
                 :label "Přisávání komínu"
                 :path ["SVO1" "IO_ServoKomin_Otv"]
                 :read-only? true
                 :klapka? true]
                [zplynovac/tr-for-path-bool
                 :label "Obtoková kl. SSV2"
                 :path ["SVO1" "IO_KlapkaObtokSSV2"]
                 :read-only? true
                 :klapka? true]
                [zplynovac/tr-for-path-bool
                 :label "Vstupni klapka SSV2"
                 :path ["SVO1" "IO_KlapkaVstupSSV2"]
                 :read-only? true
                 :klapka? true]
                (comment
                  [zplynovac/tr-for-path-bool
                   :label "Klapka těsná 3"
                   :path ["SV1" "IO_KlapkaTesna3"]
                   :path-err ["SV1" "IO_KlapkaTesna3_Error"]
                   :path-act ["SV1" (str "IO_KlapkaTesna3_DorazTrue")]
                   :read-only? true
                   :klapka? true]
                  [zplynovac/tr-for-path-bool
                   :label "Klapka těsná 4"
                   :path ["SV1" "IO_KlapkaTesna4"]
                   :path-err ["SV1" "IO_KlapkaTesna4_Error"]
                   :path-act ["SV1" (str "IO_KlapkaTesna4_DorazTrue")]
                   :read-only? true
                   :klapka? true]
                  [zplynovac/tr-for-path-bool
                   :label "Spalinová klapka 3"
                   :path ["SV1" "SpalinovaKlapka3153"]
                   :path-act ["SV1" "IO_SpalinovaKlapka3153_Zav"]
                   :read-only? true
                   :klapka? true]
                  [zplynovac/tr-for-path-bool
                   :label "Spalinová klapka 4"
                   :path ["SV1" "SpalinovaKlapka3154"]
                   :path-act ["SV1" "IO_SpalinovaKlapka3154_Zav"]
                   :read-only? true
                   :klapka? true]
                  [zplynovac/tr-for-path-bool
                   :label "Klapka turba 3"
                   :path ["SV1" "KlapkaTurb3"]
                   :path-act ["SV1" "IO_KlapkaTurb3_Otv"]
                   :read-only? true
                   :klapka? true]
                  [zplynovac/tr-for-path-bool
                   :label "Klapka turba 4"
                   :path ["SV1" "KlapkaTurb4"]
                   :path-act ["SV1" "IO_KlapkaTurb4_Otv"]
                   :read-only? true
                   :klapka? true])
                [zplynovac/tr-for-path
                 :label [zplynovac/teplota "Teplota za SSV2"]
                 :path ["SVO1" "IO_TeplotaZaSSV2"]
                 :read-only? true]
                [zplynovac/tr-for-path
                 :label "Tlak za SSV2"
                 :path ["SVO1" "IO_TlakZaSSV2"]
                 :read-only? true]
                [zplynovac/tr-for-path
                 :label [zplynovac/teplota "Teplota komín"]
                 :path ["SVO1" "IO_TeplotaKomin"]
                 :read-only? true]
                [zplynovac/tr-for-path
                 :label "Tlak komín kPa"
                 :path ["SVO1" "IO_TlakKomin"]
                 :read-only? true]]]
              [re-com/box :child
               (if @zobr-vykony?
                 [:table.table.tree-table.table-hover.table-striped.table-sm
                  [:thead
                   [:tr
                    [:th "P " [re-com/button :label "Výroba tepla" :on-click #(swap! zobr-vykony? not) :class "btn-xs"]]
                    [:th.normal "Aktuálně"]]]
                  [zplynovac/tr-for-path :label "tepelný P paliva 3 kW"
                   :path ["TE3" :tepelny-vykon-paliva]
                   :read-only? true]
                  [zplynovac/tr-for-path :label "tepelný P paliva 4 kW"
                   :path ["TE4" :tepelny-vykon-paliva]
                   :read-only? true]
                  [zplynovac/tr-for-path :label [:b "tepelný P paliva kW"]
                   :path ["TE3+4" :tepelny-vykon-paliva]
                   :read-only? true]
                  [zplynovac/tr-for-path :label "P zplynovače 3 kW"
                   :path ["TE3" :vykon-zplynovace]
                   :read-only? true]
                  [zplynovac/tr-for-path :label "P zplynovače 4 kW"
                   :path ["TE4" :vykon-zplynovace]
                   :read-only? true]
                  [zplynovac/tr-for-path :label [:b "P zplynovačů kW"]
                   :path ["TE3+4" :vykon-zplynovace]
                   :read-only? true]
                  [zplynovac/tr-for-path :label "P za výměníkem 3 kW"
                   :path ["TE3" :vykon-za-vymenikem]
                   :read-only? true]
                  [zplynovac/tr-for-path :label "P za výměníkem 4 kW"
                   :path ["TE4" :vykon-za-vymenikem]
                   :read-only? true]
                  [zplynovac/tr-for-path :label [:b "P za výměníkem kW"]
                   :path ["TE3+4" :vykon-za-vymenikem]
                   :read-only? true]
                  [zplynovac/tr-for-path :label "P před turbínou 3 kW"
                   :path ["TE3" :vykon-pred-turbinou]
                   :read-only? true]
                  [zplynovac/tr-for-path :label "P před turbínou 4 kW"
                   :path ["TE4" :vykon-pred-turbinou]
                   :read-only? true]
                  [zplynovac/tr-for-path :label [:b "P před turbínou kW"]
                   :path ["TE3+4" :vykon-pred-turbinou]
                   :read-only? true]
                  [zplynovac/tr-for-path :label "P za turbínou 3 kW"
                   :path ["TE3" :vykon-za-turbinou]
                   :read-only? true]
                  [zplynovac/tr-for-path :label "P za turbínou 4 kW"
                   :path ["TE4" :vykon-za-turbinou]
                   :read-only? true]
                  [zplynovac/tr-for-path :label [:b "P za turbínou kW"]
                   :path ["TE3+4" :vykon-za-turbinou]
                   :read-only? true]]
                 [:table.table.tree-table.table-hover.table-striped.table-sm
                  [:thead
                   [:tr
                    [:th "Výroba tepla " (when (cljc.util/power-user? @user)
                                           [re-com/button :label "P" :on-click #(swap! zobr-vykony? not) :class "btn-xs"])]
                    [:th.normal "Aktuálně"]
                    [:th.normal "KKS"]]]
                  [zplynovac/tr-for-path :label [zplynovac/teplota "Teplota topná"]
                   :path ["VT1" "IO_VT_TeplotaTopna"]
                   :read-only? true]
                  [zplynovac/tr-for-path :label [zplynovac/teplota "Teplota vratu"]
                   :path ["VT1" "IO_VT_TeplotaVratu"]
                   :read-only? true]
                  [zplynovac/tr-for-path
                   :label "Čerpadlo 1 Hz"
                   :path ["VT1" "IO_VT_FreqActCerpadlo1"]
                   :read-only? true
                   :val-fn #(some-> % (/ 100))]
                  [zplynovac/tr-for-path
                   :label "Čerpadlo 2 Hz"
                   :path ["VT1" "IO_VT_FreqActCerpadlo2"]
                   :read-only? true
                   :val-fn #(some-> % (/ 100))]
                  [:thead
                   [:tr
                    [:th "Výměníková stanice 1"]
                    [:th.normal "Aktuálně"]
                    [:th.normal "KKS"]]]
                  [zplynovac/tr-for-path-bool :label "Teplo připraveno?"
                   :path ["VYM1" "TEPLO_PRIPRAVENO"]]
                  [zplynovac/tr-for-path :label [zplynovac/teplota "Teplota vody před spojkou"]
                   :path ["VYM1" "IO_TI1"]
                   :read-only? true]
                  [zplynovac/tr-for-path :label "Výkon do výměníku MW"
                   :path ["VYM1" "IO_FIQ1"]
                   :read-only? true]
                  [zplynovac/tr-for-path :label [util/danger "Průtok vody do výměníku <sup>m<sup>3</sup></sup>&frasl;<sub>h</sub>"]
                   :path ["VYM1" "IO_FIQ2"]
                   :read-only? true]
                  [:thead
                   [:tr {:class @(re-frame/subscribe [:elevize.cljs.comp.status-table/device-error-class "VTSCH1"])}
                    [:th {:col-span 2} "Výroba tepla - suché chlazení"]
                    [:th.normal [zplynovac/suche-chlazeni-popup]]]]
                  [:thead
                   [:tr {:class @(re-frame/subscribe [:elevize.cljs.comp.status-table/device-error-class "VTRCH1"])}
                    [:th {:col-span 2} "Výroba tepla - rozvody chladu"]
                    [:th.normal [zplynovac/rozvody-chladu-popup]]]]])]
              (if (and (not orphan?)
                       (or (cljc.util/bool (get-in device-states ["ZP1" "IO_DR34_ChodVpred"]))
                           (cljc.util/bool (get-in device-states ["ZP1" "IO_DR34_ChodVzad"]))))
                [re-com/box :child
                 [:table.table.tree-table.table-hover.table-striped.table-sm
                  [:thead
                   [:tr
                    [:th "Zásobování palivem"]]]
                  [:tbody
                   [:tr
                    [:td
                     [zasobovani-palivem/vizualizace :width 400]]]]]]
                [re-com/box :child
                 [:table.table.tree-table.table-hover.table-striped.table-sm
                  [:thead
                   [:tr
                    [:th "Vyvedení tepla"]
                    [:th.normal "Aktuálně"]
                    [:th.normal "KKS"]]]
                  [zplynovac/tr-for-path :label [zplynovac/teplota "Teplota vstupní" "green"]
                   :path ["VTVT1" "IO_VTVT_TeplotaVstupni"]
                   :read-only? true]
                  [zplynovac/tr-for-path :label [zplynovac/teplota "Teplota vratná SV" "navy"]
                   :path ["VTVT1" "IO_VTVT_TeplotaVratnaSeverniVetev"]
                   :read-only? true]
                  [:tbody
                   [:tr
                    [:td {:col-span 3 :height "100px"}
                     [dygraph/variables-state-plotter [["VTVT1" "IO_VTVT_TeplotaVstupni"]
                                                       ["VTVT1" "IO_VTVT_TeplotaVratnaSeverniVetev"]]
                      :width "100%"
                      :height "150px"
                      :last-minutes 60
                      :colors ["green" "navy"]
                      :show-controls? false]]]]
                  [zplynovac/tr-for-path-bool
                   :label "Klapka obtok"
                   :path ["VTVT1" "IO_VTVT_KlapkaObtok"]
                   :path-err ["VTVT1" "IO_VTVT_KlapkaObtok_Error"]
                   :path-act ["VTVT1" "IO_VTVT_KlapkaObtok_DorazOtv"]
                   :read-only? true
                   :klapka? true]
                  [zplynovac/tr-for-path
                   :label "Čerpadlo 3 Hz"
                   :path ["VTVT1" "IO_VTVT_FreqActCerpadlo1"]
                   :read-only? true
                   :val-fn #(some-> % (/ 100))]
                  [zplynovac/tr-for-path
                   :label "Čerpadlo 4 Hz"
                   :path ["VTVT1" "IO_VTVT_FreqActCerpadlo2"]
                   :read-only? true
                   :val-fn #(some-> % (/ 100))]]])]]]])))))

(pages/add-page ::page #'page)

(secretary/defroute "/energoblok2" []
  (re-frame/dispatch [:set-current-page ::page]))
