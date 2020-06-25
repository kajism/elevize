(ns elevize.cljs.operator.zplynovace
  (:require [elevize.cljc.util :as cljc.util]
            [elevize.cljs.comp.status-table :as status-table]
            [elevize.cljs.comp.zplynovac :as zplynovac]
            [elevize.cljs.device-states :as device-states]
            [elevize.cljs.dygraph :as dygraph]
            [elevize.cljs.pages :as pages]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [secretary.core :as secretary]
            [taoensso.timbre :as timbre]
            [elevize.cljs.util :as util]
            [cljs-time.core :as t]))

(defn zplynovac-tables [device-no]
  (let [zobr-nabeh? (reagent/atom nil)
        zobr-klapky? (reagent/atom nil)
        zobr-vykony? (reagent/atom nil)
        zobr-vyvoj? (reagent/atom nil)]
    (fn []
      (let [user @(re-frame/subscribe [:auth-user])
            devices-by-code @(re-frame/subscribe [:elevize.cljs.common/entities-by :device :code])
            device-states @(re-frame/subscribe [:elevize.cljs.device-states/current])
            vars-by-device-code&name @(re-frame/subscribe [:elevize.cljs.device-states/vars-by-device-code&name])
            enums-by-group @(re-frame/subscribe [:elevize.cljs.enum-item/by-group&order])
            device-code (str "TE" device-no)
            oh-code (if (< device-no 3) "OH1" "OH2")
            rezim-auto (get-in device-states [device-code "RezimAuto"])
            odstaveno? (= rezim-auto 0)
            nabeh? (= rezim-auto 1)
            spusteno? (= rezim-auto 2)
            odstavovani? (= rezim-auto 3)
            now (or (zplynovac/time-by-path ["EB1" "Cas"] device-states) (js/Date.))]
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
                       (status-table/device-status&commands device device-states vars-by-device-code&name enums-by-group (cljc.util/power-user? user))))])]]]
          [re-com/h-box :gap "5px" :justify :between :children
           [[re-com/box :child
             (let [temperature-path [device-code "IO_Teplota"]
                   temperature-path-elektro [device-code "IO_Teplota_Elektro"]]
               (if @zobr-vykony?
                 [:table.table.tree-table.table-hover.table-striped.table-sm
                  [:thead
                   [:tr
                    [:th "P " [re-com/button :label "Teploty zplynovače" :on-click #(swap! zobr-vykony? not) :class "btn-xs"]]
                    [:th.normal {:style {:color "green"}} "t1"]
                    [:th.normal {:style {:color "navy"}} "t2"]
                    [:th.normal {:style {:color "red"}} "t3"]]]
                  [zplynovac/tr-for-path :label [:span {:style {:color "#008080"}} [zplynovac/teplota "Aktuální teplota"]] :path temperature-path :read-only? true :kks? false]
                  [zplynovac/tr-for-avgs :label [zplynovac/prumer [util/danger "průtok vzduchu <sup>m<sup>3</sup></sup>&frasl;<sub>h</sub>"]] :device-code device-code :avg :prutok-vzduchu]
                  [zplynovac/tr-for-avgs :label [zplynovac/prumer [util/danger "hm. průtok vzd. <sup>kg</sup>&frasl;<sub>s</sub>"]] :device-code device-code :avg :hm-prutok-vzduchu]
                  [zplynovac/tr-for-avgs :label [zplynovac/prumer "otáčky bubnu Hz"] :device-code device-code :avg :otacky-bubnu]
                  [zplynovac/tr-for-avgs :label [zplynovac/prumer "otáčky paliva Hz"] :device-code device-code :avg :otacky-paliva]
                  [zplynovac/tr-for-avgs :label [zplynovac/prumer [util/danger "hm. průtok paliva <sup>kg</sup>&frasl;<sub>h</sub>"]] :device-code device-code :avg :hm-prutok-paliva]
                  [zplynovac/tr-for-avgs :label [zplynovac/prumer [util/danger "tepelný P paliva kW"]] :device-code device-code :avg :tepelny-vykon-paliva]
                  [zplynovac/tr-for-avgs :label [zplynovac/prumer [util/danger "P zplynovače kW"]] :device-code device-code :avg :vykon-zplynovace]
                  [zplynovac/tr-for-avgs :label [zplynovac/prumer [util/danger "&eta; zplynovače %"]] :device-code device-code :avg :ucinnost-zplynovace]
                  [zplynovac/tr-for-avgs :label [zplynovac/prumer [util/danger "P za výměníkem kW"]] :device-code device-code :avg :vykon-za-vymenikem]
                  [zplynovac/tr-for-avgs :label [zplynovac/prumer [util/danger "P před turbínou kW"]] :device-code "TE3+4" :avg :vykon-pred-turbinou]
                  [zplynovac/tr-for-avgs :label [zplynovac/prumer [util/danger "P za turbínou kW"]] :device-code "TE3+4" :avg :vykon-za-turbinou]]
                 [:table.table.tree-table.table-hover.table-striped.table-sm
                  [:thead
                   [:tr
                    [:th "Teploty zplynovače " (when (cljc.util/admin? user)
                                                 [re-com/button :label "P" :on-click #(swap! zobr-vykony? not) :class "btn-xs"])]
                    [:th.normal "Aktuálně"]
                    [:th.normal "Nastavit"]]]
                  [zplynovac/tr-for-path :label [zplynovac/teplota "Cílová teplota"] :path [device-code "CilovaTeplotaBeh"]]
                  [zplynovac/tr-for-path :label [:span {:style {:color "#008080"}} [zplynovac/teplota "Aktuální teplota"]] :path temperature-path :read-only? true]
                  [zplynovac/tr-for-path :label [zplynovac/teplota "Cílová teplota elektro"] :path [device-code "CilovaTeplotaBeh_Elektro"]]
                  [zplynovac/tr-for-path :label [:span {:style {:color "green"}} [zplynovac/teplota "Aktuální teplota elektro"]] :path temperature-path-elektro :read-only? true]
                  [:tbody
                   [:tr
                    [:td {:col-span 3 :height "100px"}
                     [dygraph/variables-state-plotter [temperature-path
                                                       temperature-path-elektro]
                      :width "100%"
                      :height "150px"
                      :last-minutes 10
                      :colors ["#008080" "green"]
                      :show-controls? false]]]]
                  (let [toso-code (str "TOS_O" device-no)
                        vysypat-popel (cljc.util/with-date (or (zplynovac/time-by-path [toso-code "dtCasVysypaniPopelnice"] device-states) now)
                                        #(t/plus % (t/minutes (or (get-in device-states [toso-code "iTimeoutPopelnice"]) 0))))]
                    [:tbody
                     [:tr {:class @(re-frame/subscribe [:elevize.cljs.comp.status-table/device-error-class toso-code])}
                      [:td [zplynovac/tos-status :label "Odpopelnění" :device-code toso-code]]
                      [:td.right "Vysyp za"
                       #_(cljc.util/to-format vysypat-popel cljc.util/HHmm)]
                      [:td.bold (cljc.util/since-days-hours-mins-sec now vysypat-popel)]]])]))]

            [re-com/box :child
             (if @zobr-vyvoj?
               [:table.table.tree-table.table-hover.table-striped.table-sm
                  [:thead
                   [:tr
                    [:th "Vývoj " [re-com/button :label "Teploty" :on-click #(swap! zobr-vyvoj? not) :class "btn-xs"]]
                    [:th.normal "Aktuálně"]]]
                [zplynovac/tr-for-path :label "Fáze běhu" :path [device-code "FazeBehu"] :read-only? true :kks? false]
                [zplynovac/tr-for-path :label "Vývoj teploty" :path [device-code "vtVyvojTeploty"] :read-only? true :kks? false]
                [zplynovac/tr-for-path :label "Očekávaná teplota" :path [device-code "rOcekavanaTeplota"] :read-only? true :kks? false]
                [zplynovac/tr-for-path :label "Extrapolace teploty" :path [device-code "rExtrapolaceTeploty"] :read-only? true :kks? false]
                [zplynovac/tr-for-path :label "Vývoj teploty elektro" :path [device-code "vtVyvojTeploty_Elektro"] :read-only? true :kks? false]
                [zplynovac/tr-for-path :label "Očekávaná teplota el." :path [device-code "rOcekavanaTeplota_Elektro"] :read-only? true :kks? false]
                [zplynovac/tr-for-path :label "Extrapolace tepl. el." :path [device-code "rExtrapolaceTeploty_Elektro"] :read-only? true :kks? false]]
               (let [teplota-za-vymenikem-path ["SVO1" (str "IO_TeplotaZaVymenikem" device-no "PredKompresorem" device-no)]
                     teplota-pred-turbinou-path ["SVO1" "IO_TeplotaVstup2"]
                     teplota-za-turbinou-path ["SVO1" (str "IO_TeplotaZaTurbinou" device-no)]]
                 [:table.table.tree-table.table-hover.table-striped.table-sm
                  [:thead
                   [:tr
                    [:th "Teploty " (when (cljc.util/admin? user)
                                      [re-com/button :label "Vývoj" :on-click #(swap! zobr-vyvoj? not) :class "btn-xs"])]
                    [:th.normal "Aktuálně"]
                    [:th.normal "KKS"]]]
                  [zplynovac/tr-for-path :label [:span {:style {:color "green"}} [zplynovac/teplota "Teplota za výměníkem"]]
                   :path teplota-za-vymenikem-path
                   :read-only? true]
                  [zplynovac/tr-for-path :label [:span {:style {:color "navy"}} [zplynovac/teplota "Teplota před turbínou"]]
                   :path teplota-pred-turbinou-path
                   :read-only? true]
                  [zplynovac/tr-for-path :label [:span {:style {:color "red"}} [zplynovac/teplota "Teplota za turbínou"]]
                   :path teplota-za-turbinou-path
                   :read-only? true]
                  [:tbody
                   [:tr
                    [:td {:col-span 3 :height "100px"}
                     [dygraph/variables-state-plotter [teplota-za-vymenikem-path
                                                       teplota-pred-turbinou-path
                                                       teplota-za-turbinou-path]
                      :width "100%"
                      :height "150px"
                      :last-minutes 10
                      :colors ["green" "navy" "red"]
                      :show-controls? false]]]]
                  [zplynovac/tr-for-path :label [zplynovac/teplota "Teplota oleje"]
                   :path [oh-code "IO_TeplotaOleje"]
                   :read-only? true]
                  [zplynovac/tr-for-path :label [zplynovac/teplota "Teplota oleje za výměníkem"]
                   :path [oh-code "IO_TeplotaOlejeZaVymenikem"]
                   :read-only? true]
                  [zplynovac/tr-for-path :label [zplynovac/teplota "Teplota na turbíně"]
                   :path [oh-code "IO_TeplotaNaTurbine"]
                   :read-only? true]]))]

            (if (if (nil? @zobr-nabeh?)
                  (< rezim-auto 2)
                  @zobr-nabeh?)
              [re-com/v-box :children
               [(let [path [device-code "FazeStartu"]
                      value (get-in device-states path)]
                  [:table.table.tree-table.table-hover.table-striped.table-sm
                   [:thead
                    [:tr
                     [:th "Náběh " [re-com/button :label "Tlaky" :on-click #(swap! zobr-nabeh? not) :class "btn-xs"]]
                     [:th.normal "Aktuálně"]
                     [:th.normal "Nastavit"]]]
                   [zplynovac/tr-for-path :label "Fáze startu" :path [device-code "FazeStartu"] :read-only? true]
                   [zplynovac/tr-for-path :label "Otáčky sypání 1. dávky" :path [device-code "POtackyPriZaklDavce"]]
                   [zplynovac/tr-for-path :label "Doba sypání 1. dávky" :path [device-code "PDobaZaklDavky"]]
                   [zplynovac/tr-for-path :label "Doba sypání další dávky" :path [device-code "PDobaDalsiDavky"]]
                   [zplynovac/tr-for-path :label "Dodávkovat po čase" :path [device-code "DodavkovatPoCase"]]])
                [:table.table.tree-table.table-hover.table-striped.table-sm
                 [:tbody
                  [:tr
                   [:td {:class (str (when (not (cljc.util/bool (get-in device-states [device-code "IO_Spirala_Ready"])))
                                       "error"))}
                    "Spirála"]
                   [:td {:width "310px"}
                    [dygraph/variables-state-plotter [[device-code "IO_Spirala_Chod"]]
                     :width "100%"
                     :height "45px"
                     :last-minutes 10
                     :show-controls? false]]]]]
                [zplynovac/casy device-code]]]
              [re-com/box :child
               (let [tlak-za-vymenikem-path ["SVO1" (str "IO_TlakZaVymenikem" device-no "PredKompresorem" device-no)]
                     tlak-pred-turbinou-path ["SVO1" "IO_TlakVstup2"]
                     tlak-za-turbinou-path ["SVO1" (str "IO_TlakZaTurbinou" device-no)]]
                 [:table.table.tree-table.table-hover.table-striped.table-sm
                  [:thead
                   [:tr
                    [:th "Tlaky " [re-com/button :label "Náběh" :on-click #(swap! zobr-nabeh? not) :class "btn-xs"]]
                    [:th.normal "Aktuálně"]
                    [:th]]]
                  [zplynovac/tr-for-path :label [:span {:style {:color "green"}} "Tlak za výměníkem kPa"]
                   :path tlak-za-vymenikem-path
                   :read-only? true]
                  [zplynovac/tr-for-path :label [:span {:style {:color "navy"}} "Tlak před turbínou kPa"]
                   :path tlak-pred-turbinou-path
                   :read-only? true]
                  [zplynovac/tr-for-path :label [:span {:style {:color "red"}} "Tlak za turbínou kPa"]
                   :path tlak-za-turbinou-path
                   :read-only? true]
                  [:tbody
                   [:tr
                    [:td {:col-span 3 :height "100px"}
                     [dygraph/variables-state-plotter [tlak-za-vymenikem-path
                                                       tlak-pred-turbinou-path
                                                       tlak-za-turbinou-path]
                      :width "100%"
                      :height "150px"
                      :last-minutes 10
                      :colors ["green" "navy" "red"]
                      :show-controls? false]]]]
                  [zplynovac/tr-for-path :label "Tlak spalin kPa"
                   :path [device-code "IO_TlakSpalin"]
                   :read-only? true]
                  [zplynovac/tr-for-path :label "Tlak oleje kPa"
                   :path [oh-code "IO_TlakOleje"]
                   :read-only? true]])])

            [re-com/box :child
             [:table.table.tree-table.table-hover.table-striped.table-sm
              [:thead
               [:tr
                [:th "Ventilátor"]
                [:th.normal "Aktuálně"]
                [:th.normal "Nastavit"]]]
              [zplynovac/tr-for-path :label "Frekvence Hz" :path ["SV1" (str "rVentFreqReq" device-no)]]

              [:thead
               [:tr
                [:th "Palivo"]
                [:th.normal "Aktuálně"]
                [:th.normal "Nastavit"]]]
              (let [tosp-code (str "TOS_P" device-no)]
                [zplynovac/tr-for-path-bool
                 :label [zplynovac/tos-status :label"Chod" :device-code tosp-code]
                 :path [device-code "PalivoOn"]
                 :path-err [device-code "ChybaPalivo"]
                 :class @(re-frame/subscribe [:elevize.cljs.comp.status-table/device-error-class tosp-code])])
              [zplynovac/tr-for-path :label "Frekvence požadovaná Hz" :path [device-code "PalivoFreqReq"]]
              [zplynovac/tr-for-path :label "Frekvence skutečná Hz"
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
              [zplynovac/tr-for-path :label "Frekvence požadovaná Hz" :path [device-code "BubenFreqReq"]]
              [zplynovac/tr-for-path :label "Frekvence skutečná Hz"
               :path [device-code "IO_BubenFreqAct"]
               :read-only? true]]]

            (let [turbosoustroji [[:thead
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
               (into
                [:table.table.tree-table.table-hover.table-striped.table-sm]
                (into
                  (if @zobr-klapky?
                    [[:thead
                      [:tr
                       [:th "Klapky " [re-com/button :label "Průtok" :on-click #(swap! zobr-klapky? not) :class "btn-xs"]]
                       [:th.normal "Aktuálně"]
                       [:th.normal "Nastavit"]]]
                     [zplynovac/klapka device-no 1]
                     [zplynovac/klapka device-no 2]
                     [zplynovac/klapka device-no 3]]
                    [[:thead
                      [:tr
                       [:th "Průtok " [re-com/button :label "Klapky" :on-click #(swap! zobr-klapky? not) :class "btn-xs"]]
                       [:th.normal "Aktuálně"]
                       [:th.normal "KKS"]]]
                     [zplynovac/flowswitch device-no]
                     [zplynovac/tr-for-path :label [zplynovac/teplota "Teplota vzduchu"]
                      :path ["SV1" (str "IO_TeplotaVzduchuPredZplynovacem" device-no)]
                      :read-only? true]
                     [zplynovac/tr-for-path :label "Tlak vzduchu kPa"
                      :path ["SV1" (str "IO_TlakVzduchuPredZplynovacem" device-no)]
                      :read-only? true]])
                  turbosoustroji))])]]]]))))

(defn page []
  [re-com/v-box :gap "10px" :children
   [[status-table/status-table nil]
    [zplynovac-tables 3]
    [zplynovac-tables 4]]])

(pages/add-page ::page #'page)

(secretary/defroute "/zplynovace" []
  (re-frame/dispatch [:set-current-page ::page]))
