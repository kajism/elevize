(ns elevize.cljs.operator.zplynovac
  (:require [elevize.cljc.util :as cljc.util]
            [elevize.cljs.comp.status-table :as status-table]
            [elevize.cljs.comp.zplynovac :as zplynovac]
            [elevize.cljs.dygraph :as dygraph]
            [elevize.cljs.pages :as pages]
            [elevize.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]
            [taoensso.timbre :as timbre]
            [reagent.core :as reagent]
            [clojure.string :as str]))

(defn page []
  (let [device-code (re-frame/subscribe [:elevize.cljs.common/path-value [::device-code]])
        devices-by-code (re-frame/subscribe [:elevize.cljs.common/entities-by :device :code])
        device-states (re-frame/subscribe [:elevize.cljs.device-states/current])
        vars-by-device-code&name (re-frame/subscribe [:elevize.cljs.device-states/vars-by-device-code&name])
        enums-by-group (re-frame/subscribe [:elevize.cljs.enum-item/by-group&order])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      (if-not (and @devices-by-code @vars-by-device-code&name @enums-by-group)
        [re-com/throbber]
        (let [device-no (subs @device-code 2)
              oh-code (if (< device-no 3) "OH1" "OH2")
              tosp-code (str "TOS_P" device-no)
              toso-code (str "TOS_O" device-no)
              rezim-auto (get-in @device-states [@device-code "RezimAuto"])
              zobr-nabeh? (< rezim-auto 2)
              spusteno? (= rezim-auto 2)
              odstavovani? (= rezim-auto 3)
              width (if zobr-nabeh? "24%" "32%")]
          [:div
           [:h3 (str "Obsluha " (some-> @device-code (@devices-by-code) :title))]
           [:table.table.tree-table.table-hover.table-striped
            [:tbody
             (doall
              (for [device (keep @devices-by-code [oh-code
                                                   "ZP1"
                                                   "SV1"
                                                   tosp-code
                                                   toso-code
                                                   @device-code])]
                ^{:key (:id device)}
                [:tr
                 (cons
                  ^{:key 999}
                  [:td (:title device)]
                  (status-table/device-status&commands device @device-states @vars-by-device-code&name @enums-by-group (cljc.util/power-user? @user)))]))]]
           [re-com/h-box :gap "20px" :justify :between :children
            [[re-com/box :width width
              :child
              [:table.table.tree-table.table-hover.table-striped
               [:thead
                [:tr
                 [:td [:h5 "Ventilátor"]]
                 [:td "Aktuálně"]
                 [:td "Nastavit"]]]
               [zplynovac/tr-for-path :label "Frekvence" :path ["SV1" (str "rVentFreqReq" device-no)]]]]

             [re-com/box :width width
              :child
              [:table.table.tree-table.table-hover.table-striped
               [:thead
                [:tr
                 [:td [:h5 "Klapky"]]
                 [:td "Aktuálně"]
                 [:td "Nastavit"]]]
               [zplynovac/klapka device-no 1]
               [zplynovac/klapka device-no 2]
               [zplynovac/klapka device-no 3]]]

             [zplynovac/casy @device-code]

             (when zobr-nabeh?
               [re-com/box
                :child
                [:table.table.tree-table.table-hover.table-striped
                 [:thead
                  [:tr
                   [:td [:h5 "Náběh"]]
                   [:td "Aktuálně"]
                   [:td "Nastavit"]]]
                 [:tbody
                  (let [path [@device-code "FazeStartu"]
                        value (get-in @device-states path)]
                    [:tr
                     [:td "Fáze startu"]
                     [:td {:style {:font-weight "bold"}}
                      (or (some-> (get-in @vars-by-device-code&name path) :data-type (@enums-by-group) (get value) :label)
                          (str value))]
                     [:td]])]
                 [zplynovac/tr-for-path :label "Otáčky sypání 1. dávky" :path [@device-code "POtackyPriZaklDavce"]]
                 [zplynovac/tr-for-path :label "Doba sypání 1. dávky" :path [@device-code "PDobaZaklDavky"]]
                 [zplynovac/tr-for-path :label "Doba sypání další dávky" :path [@device-code "PDobaDalsiDavky"]]
                 [zplynovac/tr-for-path :label "Dodávkovat po čase" :path [@device-code "DodavkovatPoCase"]]]])]]

           [re-com/h-box :gap "20px" :justify :between :children
            [[re-com/box :width width
              :child
              [:table.table.tree-table.table-hover.table-striped
               [:thead
                [:tr
                 [:td [:h5 "Palivo"]]
                 [:td "Aktuálně"]
                 [:td "Nastavit"]]]
               [zplynovac/tr-for-path-bool
                :label "Chod"
                :path [@device-code "PalivoOn"]
                :path-err [@device-code "ChybaPalivo"]]
               [zplynovac/tr-for-path :label "Frekvence požadovaná" :path [@device-code "PalivoFreqReq"]]
               [zplynovac/tr-for-path :label "Frekvence skutečná"
                :path [@device-code "IO_PalivoFreqAct"]
                :read-only? true]]]

             [re-com/box :width width
              :child
              [:table.table.tree-table.table-hover.table-striped
               [:thead
                [:tr
                 [:td [:h5 "Buben"]]
                 [:td "Aktuálně"]
                 [:td "Nastavit"]]]
               [zplynovac/tr-for-path-bool
                :label "Chod"
                :path [@device-code "BubenOn"]
                :path-err [@device-code "ChybaBuben"]]
               [zplynovac/tr-for-path :label "Frekvence požadovaná" :path [@device-code "BubenFreqReq"]]
               [zplynovac/tr-for-path :label "Frekvence skutečná"
                :path [@device-code "IO_BubenFreqAct"]
                :read-only? true]]]

             (let [temperature-path [@device-code "IO_Teplota"]]
               [re-com/box :width width
                :child
                [:table.table.tree-table.table-hover.table-striped
                 [:thead
                  [:tr
                   [:td [:h5 "Tlak a teploty"]]
                   [:td "Aktuálně"]
                   [:td "Nastavit"]]]
                 [zplynovac/tr-for-path :label "Tlak spalin"
                  :path [@device-code "IO_TlakSpalin"]
                  :read-only? true]
                 [zplynovac/tr-for-path :label "Aktuální teplota"
                  :path temperature-path
                  :read-only? true]
                 [:tbody
                  [:tr
                   [:td {:col-span 3 :height "100px"}
                    [dygraph/variables-state-plotter [temperature-path]
                     :width "100%"
                     :height "150px"
                     :last-minutes 10
                     :show-controls? false]]]]
                 [zplynovac/tr-for-path :label "Cílová teplota" :path [@device-code "CilovaTeplotaBeh"]]
                 [zplynovac/tr-for-path :label "Za vyměníkem"
                  :path ["SVO1" (str "IO_TeplotaZaVymenikem" device-no "PredKompresorem" device-no)]
                  :read-only? true]

                 (let [path [@device-code "FazeBehu"]
                       value (get-in @device-states path)]
                   [:tbody
                    [:tr
                     [:td "Fáze běhu"]
                     [:td {:style {:font-weight "bold"}}
                      (or (some-> (get-in @vars-by-device-code&name path) :data-type (@enums-by-group) (get value) :label)
                          (str value))]
                     [:td]]])]])

             (when zobr-nabeh?
               [re-com/box :width width
                :child
                [:table.table.tree-table.table-hover.table-striped
                 [:thead
                  [:tr
                   [:td [:h5 "Spirála"]]]]
                 (let [path [@device-code "IO_Spirala_Chod"]]
                   [:tbody
                    [:tr
                     [:td {:class (str (when (not (cljc.util/bool (get-in @device-states [@device-code "IO_Spirala_Ready"])))
                                         "error"))}
                      "Aktuálně"]
                     [:td {:style {:font-weight "bold"}}
                      (if (cljc.util/bool (get-in @device-states path))
                        "zapnutá"
                        "vypnutá")]]
                    [:tr
                     [:td {:col-span 2 :height "100px"}
                      [dygraph/variables-state-plotter [path]
                       :width "100%"
                       :height "100px"
                       :last-minutes 10
                       :show-controls? false]]]])]])]]

           [re-com/h-box :gap "20px" :justify :between :children
            [[re-com/box :width width
              :child
              [:table.table.tree-table.table-hover.table-striped
               [:thead
                [:tr
                 [:td [:h5 "Olejové hospodářství 2"]]
                 [:td "Aktuálně"]
                 [:td "Nastavit"]]]
               [zplynovac/tr-for-path-bool
                :label "Ohřívací spirála"
                :path [oh-code "IO_Spirala"]
                :path-act [oh-code "IO_Spirala_Chod"]
                :path-err [oh-code "IO_Spirala_Error"]]
               [zplynovac/tr-for-path-bool
                :label "Cirkulační čerpadlo"
                :path [oh-code "IO_MotorCerpadloCirkulacni"]
                :path-act [oh-code "IO_MotorCerpadloCirkulacni_Chod"]
                :path-err [oh-code "IO_MotorCerpadloCirkulacni_Error"]]
               [zplynovac/tr-for-path-bool
                :label "Vratné čerpadlo"
                :path [oh-code "IO_MotorCerpadloVratne"]
                :path-act [oh-code "IO_MotorCerpadloVratne_Chod"]
                :path-err [oh-code "IO_MotorCerpadloVratne_Error"]]
               [zplynovac/tr-for-path-bool
                :label "Čerpadlo odsávání"
                :path [oh-code "IO_MotorCerpadloOdsavani"]
                :path-act [oh-code "IO_MotorCerpadloOdsavani_Chod"]
                :path-err [oh-code "IO_MotorCerpadloOdsavani_Error"]]
               [zplynovac/tr-for-path :label "Tlak oleje"
                :path [oh-code "IO_TlakOleje"]
                :read-only? true]
               [zplynovac/tr-for-path :label "Teplota oleje"
                :path [oh-code "IO_TeplotaOleje"]
                :read-only? true]
               [zplynovac/tr-for-path :label "Teplota oleje ve vaně"
                :path [oh-code "IO_TeplotaOlejeVeVane"]
                :read-only? true]
               [zplynovac/tr-for-path :label "Teplota oleje za výměníkem"
                :path [oh-code "IO_TeplotaOlejeZaVymenikem"]
                :read-only? true]
               [zplynovac/tr-for-path :label "Teplota na turbíně"
                :path [oh-code "IO_TeplotaNaTurbine"]
                :read-only? true]
               [zplynovac/tr-for-path :label "Teplota předního ložiska generátoru"
                :path [oh-code "IO_TeplotaNaGeneratoruPredniLozisko"]
                :read-only? true]
               [zplynovac/tr-for-path :label "Teplota zadního ložiska generátoru	"
                :path [oh-code "IO_TeplotaNaGeneratoruZadniLozisko"]
                :read-only? true]
               [zplynovac/tr-for-path :label "Teplota 1 vinutí statoru generátoru"
                :path [oh-code "IO_TeplotaNaGeneratoruVinutiStatoru1"]
                :read-only? true]
               [zplynovac/tr-for-path :label "Teplota 2 vinutí statoru generátoru"
                :path [oh-code "IO_TeplotaNaGeneratoruVinutiStatoru2"]
                :read-only? true]
               [zplynovac/tr-for-path :label "Teplota 3 vinutí statoru generátoru"
                :path [oh-code "IO_TeplotaNaGeneratoruVinutiStatoru3"]
                :read-only? true]
               [zplynovac/tr-for-path :label "Snímač otáček 1"
                :path [oh-code "IO_SnimacOtacek1"]
                :read-only? true]
               [zplynovac/tr-for-path :label "Snímač otáček 2"
                :path [oh-code "IO_SnimacOtacek2"]
                :read-only? true]
               [zplynovac/tr-for-path :label "Snímač otáček 3"
                :path [oh-code "IO_SnimacOtacek3"]
                :read-only? true]]]

             [re-com/box :width width
              :child
              [:table.table.tree-table.table-hover.table-striped
               [:thead
                [:tr
                 [:td [:h5 "Šneky podávání paliva"]]
                 [:td "Aktuálně"]
                 [:td "Nastavit"]]]
               [zplynovac/pistnice @vars-by-device-code&name @device-states tosp-code 1]
               [zplynovac/pistnice @vars-by-device-code&name @device-states tosp-code 2]
               [zplynovac/tr-for-path-bool
                :label "Šnek 1"
                :path [tosp-code "IO_Snek1"]
                :path-act [tosp-code "IO_Snek1_Chod"]
                :path-err [tosp-code "IO_Snek1_Error"]]
               [zplynovac/tr-for-path-bool
                :label "Šnek 2"
                :path [tosp-code "IO_Snek2"]
                :path-act [tosp-code "IO_Snek2_Chod"]
                :path-err [tosp-code "IO_Snek2_Error"]]]]

             [re-com/box :width width
              :child
              [:table.table.tree-table.table-hover.table-striped
               [:thead
                [:tr
                 [:td [:h5 "Šneky odpopelnění"]]
                 [:td "Aktuálně"]
                 [:td "Nastavit"]]]
               [zplynovac/pistnice @vars-by-device-code&name @device-states toso-code 1]
               [zplynovac/pistnice @vars-by-device-code&name @device-states toso-code 2]
               [zplynovac/tr-for-path-bool
                :label "Šnek 1"
                :path [toso-code "IO_Snek1"]
                :path-act [toso-code "IO_Snek1_Chod"]
                :path-err [toso-code "IO_Snek1_Error"]]
               [zplynovac/tr-for-path-bool
                :label "Šnek 2"
                :path [toso-code "IO_Snek2"]
                :path-act [toso-code "IO_Snek2_Chod"]
                :path-err [toso-code "IO_Snek2_Error"]]
               [zplynovac/tr-for-path-bool :label "Ventil popelnice"
                :path [toso-code "IO_VentilPopelniceOtevren"]
                :read-only? true]]]]]])))))

(pages/add-page ::page #'page)

(secretary/defroute "/zplynovac/:code" [code]
  (re-frame/dispatch [:elevize.cljs.common/set-path-value [::device-code] code])
  (re-frame/dispatch [:set-current-page ::page]))
